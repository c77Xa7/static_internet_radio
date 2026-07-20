package com.staticradio.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feeds station artwork (Radio Browser favicon or user-uploaded image) into
 * the Media3 notification's large icon. Reuses the app's existing Coil
 * dependency instead of pulling in media3-datasource just for this.
 */
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    private val imageLoader = ImageLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalStateException("Could not decode bitmap"))
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false) // notification artwork needs a software bitmap
                .build()
            val result = runCatching { imageLoader.execute(request) }.getOrNull()
            val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalStateException("Failed to load artwork: $uri"))
        }
        return future
    }
}
