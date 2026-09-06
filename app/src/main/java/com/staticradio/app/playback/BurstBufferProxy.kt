package com.staticradio.app.playback

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The cast burst-buffer shadow proxy designed in PROJECT_CONTEXT.md
 * ("Known gaps / next steps", item 8 — root cause documented in the
 * Chromecast architecture section).
 *
 * A Chromecast receiver won't start audio until it holds roughly 240-256KB,
 * and it accumulates that at whatever rate the origin server feeds it —
 * Icecast's burst-on-connect is the entire variable (a server that dumps
 * ~1MB on connect casts instantly; one that doesn't takes 256KB / bitrate
 * seconds, the observed ~15s at 128kbps). This is fixable client-side:
 *
 *  - a *shadow* fetch opens a second connection to the stream and fills an
 *    in-memory ring buffer (deliberately NOT a proxy in front of the local
 *    ExoPlayer path — local playback is the app's core working feature and
 *    must not gain a new failure mode to fix a cast-only problem);
 *  - a tiny HTTP server on the LAN serves the buffered backlog at LAN speed;
 *  - casting hands the receiver http://<phone-lan-ip>:<port>/... instead of
 *    the origin URL, so the receiver's appetite is cleared instantly.
 *
 * The backlog has to exist *before* the cast starts — hence "pre-buffer":
 * the service warms the next stations (next/previous in the playlist, and
 * the pre-decided random queue behind Shuffle) while you listen, so casting
 * after ~15s of listening — the normal flow — is instant. Casting from cold
 * still stalls; that limit is inherent to the design and accepted.
 *
 * Cost: double bandwidth for each pre-buffered station while its shadow
 * fetch runs. Gated behind Settings -> Cast -> "Pre-buffer for casting"
 * (off by default) which the Settings copy explicitly warns about.
 */
class BurstBufferProxy(
    private val scope: CoroutineScope,
    private val ringCapacityBytes: Int = DEFAULT_RING_CAPACITY_BYTES
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    // One proxy entry per warmed stream URL.
    private val entries = ConcurrentHashMap<String, ProxyEntry>()

    // Marked per entry while a cast is actively reading through it, so the
    // shadow fetch's failure doesn't tear down an in-flight cast.
    private val activelyServedUrls = ConcurrentHashMap.newKeySet<String>()

    data class ProxyEntry(
        val streamUrl: String,
        val contentType: String,
        @Volatile var ring: RingBuffer,
        @Volatile var fetchJob: Job?,
        @Volatile var dead: Boolean = false
    )

    /** Fixed-capacity ring: newest bytes overwrite oldest once full. */
    class RingBuffer(private val capacity: Int) {
        private val data = ByteArray(capacity)
        private var writePos = 0L // monotonic
        @Volatile private var closed = false

        val totalWritten: Long get() = writePos
        val isClosed: Boolean get() = closed

        @Synchronized
        fun write(input: InputStream, onError: () -> Unit) {
            try {
                val buf = ByteArray(16 * 1024)
                while (!closed) {
                    val n = input.read(buf)
                    if (n < 0) break
                    // Wrap around, overwriting the oldest bytes.
                    var offset = 0
                    while (offset < n) {
                        val chunk = minOf(n - offset, capacity)
                        val pos = (writePos % capacity).toInt()
                        val first = minOf(chunk, capacity - pos)
                        System.arraycopy(buf, offset, data, pos, first)
                        if (first < chunk) System.arraycopy(buf, offset + first, data, 0, chunk - first)
                        writePos += chunk
                        offset += chunk
                    }
                    (this as Object).notifyAll()
                }
            } catch (e: Exception) {
                if (!closed) onError()
            }
        }

        /** Reads up to [max] bytes starting at absolute offset [from]; blocks while empty at that offset. */
        @Synchronized
        fun readFrom(from: Long, max: Int, isCancelled: () -> Boolean): ByteArray? {
            while (!isCancelled()) {
                val available = writePos - from
                if (available > 0) {
                    val start = (from % capacity).toInt()
                    val n = minOf(max, available.toInt(), capacity)
                    val out = ByteArray(n)
                    val first = minOf(n, capacity - start)
                    System.arraycopy(data, start, out, 0, first)
                    if (first < n) System.arraycopy(data, 0, out, first, n - first)
                    return out
                }
                if (closed) return null
                (this as Object).wait(1000)
            }
            return null
        }

        @Synchronized
        fun close() {
            closed = true
            (this as Object).notifyAll()
        }
    }

    // ---- Lifecycle ----

    fun start() {
        if (serverSocket != null) return
        try {
            // Port 0 = let the OS pick a free port (per the design notes).
            val socket = ServerSocket(0, 8, InetAddress.getByName("0.0.0.0"))
            serverSocket = socket
            serverThread = Thread({
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        scope.launch(Dispatchers.IO) { serve(client) }
                    } catch (e: Exception) {
                        if (!socket.isClosed) Log.w(TAG, "accept failed", e)
                    }
                }
            }, "BurstBufferProxyServer").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Burst buffer proxy listening on port ${socket.localPort}")
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start burst buffer proxy", e)
            serverSocket = null
        }
    }

    fun stop() {
        activelyServedUrls.clear()
        entries.values.forEach { it.fetchJob?.cancel(); it.ring.close() }
        entries.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverThread = null
    }

    fun lanUrlFor(streamUrl: String): String? {
        val socket = serverSocket ?: return null
        val ip = lanIpAddress() ?: return null
        // The path carries the origin URL (encoded); the server demuxes on it.
        return "http://$ip:${socket.localPort}/stream?url=${java.net.URLEncoder.encode(streamUrl, "UTF-8")}"
    }

    private fun lanIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    // ---- Shadow fetching ----

    /**
     * Starts (or tops up) the shadow fetch for [streamUrl]. Cheap no-op when
     * the entry is already warm. Returns immediately — buffering happens in
     * the background, it doesn't block playback or casting.
     */
    fun preBuffer(streamUrl: String, contentType: String?) {
        if (streamUrl.isBlank() || serverSocket == null) return
        val existing = entries[streamUrl]
        if (existing != null && !existing.dead && existing.fetchJob?.isActive == true) return

        if (existing != null) { existing.ring.close() }
        val entry = ProxyEntry(
            streamUrl = streamUrl,
            contentType = contentType ?: "audio/mpeg",
            ring = RingBuffer(ringCapacityBytes),
            fetchJob = null
        )
        entry.fetchJob = scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(streamUrl)
                    .header("Icy-MetaData", "0") // no ICY padding — raw audio only in the buffer
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Shadow fetch for pre-buffer got HTTP ${response.code}")
                        entry.dead = true
                        return@use
                    }
                    val body = response.body ?: return@use
                    entry.ring.write(body.byteStream()) {
                        entry.dead = true
                        Log.w(TAG, "Shadow fetch failed for $streamUrl")
                    }
                }
            } catch (e: Exception) {
                if (!activelyServedUrls.contains(streamUrl)) {
                    entry.dead = true
                    Log.w(TAG, "Shadow fetch error for $streamUrl", e)
                }
            }
        }
        entries[streamUrl] = entry
    }

    /** Drops a warmed entry — used when playback moves on and a station is no longer upcoming. */
    fun release(streamUrl: String) {
        if (activelyServedUrls.contains(streamUrl)) return
        entries.remove(streamUrl)?.let { it.fetchJob?.cancel(); it.ring.close() }
    }

    fun releaseAll() {
        val serving = activelyServedUrls.toSet()
        entries.entries.removeAll { (url, entry) ->
            if (url in serving) return@removeAll false
            entry.fetchJob?.cancel()
            entry.ring.close()
            true
        }
    }

    // ---- Serving ----

    private fun serve(socket: Socket) {
        socket.use { sock ->
            try {
                val requestLine = sock.getInputStream().bufferedReader().readLine() ?: return
                val path = requestLine.split(" ").getOrNull(1) ?: return
                val target = java.net.URLDecoder.decode(
                    path.substringAfter("url=", "").substringBefore('&'), "UTF-8"
                )
                if (target.isBlank()) {
                    sock.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    return
                }
                val entry = entries[target] ?: run {
                    sock.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    return
                }
                activelyServedUrls.add(target)
                try {
                    val from = 0L // serve the whole backlog, then live-tail
                    sock.getOutputStream().let { raw ->
                        val out = BufferedOutputStream(raw, 64 * 1024)
                        out.write(
                            ("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: ${entry.contentType}\r\n" +
                                "Connection: close\r\n" +
                                "\r\n").toByteArray()
                        )
                        out.flush()
                        var offset = from
                        val cancelled = { sock.isClosed || entry.ring.isClosed }
                        while (!cancelled()) {
                            val chunk = entry.ring.readFrom(offset, 32 * 1024, cancelled) ?: break
                            out.write(chunk)
                            out.flush()
                            offset += chunk.size
                        }
                        out.flush()
                    }
                } finally {
                    activelyServedUrls.remove(target)
                }
            } catch (e: Exception) {
                // Client (receiver) hung up mid-stream — normal when a cast ends.
                Log.d(TAG, "Proxy client disconnected: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "BurstBufferProxy"

        // The receiver's appetite is ~240-256KB; 512KB clears it with headroom
        // (the size the design brief called for) without being memory-heavy.
        const val DEFAULT_RING_CAPACITY_BYTES = 512 * 1024

        // How many upcoming stations to keep warm: next/previous 3 in the
        // playlist, plus the 3-entry random queue behind Shuffle.
        const val PRE_BUFFER_COUNT = 3
    }
}
