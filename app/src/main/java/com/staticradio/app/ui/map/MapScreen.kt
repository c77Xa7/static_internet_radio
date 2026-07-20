package com.staticradio.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.staticradio.app.data.ResolvedStation
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.toResolved
import com.staticradio.app.ui.common.AppTopBar
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import com.staticradio.app.ui.common.TopBarMode
import com.staticradio.app.ui.home.HomeViewModel
import com.staticradio.app.ui.home.StationFilterDialog
import kotlinx.coroutines.flow.map
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

private val DARK_GREYSCALE_FILTER = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

// Light mode: plain neutral desaturation reads as a cold, out-of-place grey
// against the app's warm concrete palette — this duotones the greyscale
// (black-and-white luminance) between the theme's ink and concrete tones
// instead, so the map reads as part of the same palette. Dark mode keeps the
// plain desaturated look (already confirmed good) untouched.
private fun lightModeMapFilter(inkColor: Int, concreteColor: Int): ColorMatrixColorFilter {
    fun channel(target: Int, source: Int): Float = (target - source) / 255f
    val inkR = AndroidColor.red(inkColor); val inkG = AndroidColor.green(inkColor); val inkB = AndroidColor.blue(inkColor)
    val concreteR = AndroidColor.red(concreteColor); val concreteG = AndroidColor.green(concreteColor); val concreteB = AndroidColor.blue(concreteColor)
    val matrix = floatArrayOf(
        channel(concreteR, inkR) * 0.2126f, channel(concreteR, inkR) * 0.7152f, channel(concreteR, inkR) * 0.0722f, 0f, inkR.toFloat(),
        channel(concreteG, inkG) * 0.2126f, channel(concreteG, inkG) * 0.7152f, channel(concreteG, inkG) * 0.0722f, 0f, inkG.toFloat(),
        channel(concreteB, inkB) * 0.2126f, channel(concreteB, inkB) * 0.7152f, channel(concreteB, inkB) * 0.0722f, 0f, inkB.toFloat(),
        0f, 0f, 0f, 1f, 0f
    )
    return ColorMatrixColorFilter(ColorMatrix(matrix))
}

private val PIN_PALETTE = listOf(0xFF1D1C18, 0xFF3D6BFF, 0xFFCFEE2E, 0xFFB7AE99, 0xFFFF4713).map { it.toInt() }

// CARTO's Voyager style — free, no API key, OSM data underneath (same source
// as stock Mapnik) via CARTO's own cartography, which leans more Latin/English
// in its place labels than stock Mapnik does for many non-Latin-script
// regions. Not a hard guarantee of English-only labels everywhere — that was
// Wikimedia's "osm-intl" tile set, which now 403s on external requests
// (confirmed dead, not just flaky) — but this is the closest reliable, free,
// no-auth option available.
private val ENGLISH_LEANING_TILE_SOURCE = XYTileSource(
    "CartoVoyager",
    0, 19,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    )
)

private const val TILE_CACHE_MAX_BYTES = 300L * 1024 * 1024
private const val TILE_CACHE_TRIM_BYTES = 250L * 1024 * 1024

// Web Mercator projection's real latitude limit — constraining scroll to this
// (plus disabling vertical repetition) is what stops osmdroid duplicating the
// map above/below the visible area at low zoom.
private const val MAX_MERCATOR_LATITUDE = 85.0511

/**
 * OpenStreetMap tiles via osmdroid (no Google Play Services dependency —
 * fits the app's local-only/privacy stance), greyscale color-filtered per
 * the post-brutalist design direction. Teardrop pins from each station's
 * lat/lon, station image clipped into the pin's center circle.
 *
 * pickMode turns this into a tap-to-select coordinate picker (used from Edit
 * Station's "Pick on Map") instead of tap-to-play.
 */
@Composable
fun MapScreen(
    stationDao: StationDao,
    onStationClick: (ResolvedStation) -> Unit,
    modifier: Modifier = Modifier,
    pickMode: Boolean = false,
    onLocationPicked: ((Double, Double) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onListClick: () -> Unit = {},
    onGridClick: () -> Unit = {},
    onMixesClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    homeViewModel: HomeViewModel? = null
) {
    val context = LocalContext.current
    val allStations by remember(stationDao) {
        stationDao.observeStationsWithTags().map { rows -> rows.map { it.toResolved() } }
    }.collectAsState(initial = emptyList())

    var showFilterDialog by remember { mutableStateOf(false) }
    val filter = homeViewModel?.filter?.collectAsState()?.value
    val genres = homeViewModel?.genres?.collectAsState()?.value ?: emptyList()
    val countryCodes = homeViewModel?.countryCodes?.collectAsState()?.value ?: emptyList()
    val moods = homeViewModel?.moods?.collectAsState()?.value ?: emptyList()
    val styles = homeViewModel?.styles?.collectAsState()?.value ?: emptyList()

    val filterActive = filter != null && (filter.favoritesOnly || filter.genre != null || filter.countryCode != null ||
        filter.mood != null || filter.style != null)

    if (showFilterDialog && homeViewModel != null && filter != null) {
        StationFilterDialog(
            filter = filter,
            genres = genres,
            countryCodes = countryCodes,
            moods = moods,
            styles = styles,
            onGenreClick = homeViewModel::toggleGenreFilter,
            onCountryClick = homeViewModel::toggleCountryFilter,
            onMoodClick = homeViewModel::toggleMoodFilter,
            onStyleClick = homeViewModel::toggleStyleFilter,
            onFavoritesClick = homeViewModel::toggleFavoritesOnly,
            onDismiss = { showFilterDialog = false }
        )
    }

    val stations = (if (filter != null) {
        allStations
            .filter { filter.genre == null || filter.genre in it.genres }
            .filter { filter.countryCode == null || it.countryCode == filter.countryCode }
            .filter { filter.mood == null || it.mood == filter.mood }
            .filter { filter.style == null || it.style == filter.style }
            .filter { !filter.favoritesOnly || it.isFavorite }
    } else {
        allStations
    }).filter { it.latitude != null && it.longitude != null }

    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val keylineColor = MaterialTheme.colorScheme.outline.toArgb()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inkColorArgb = MaterialTheme.colorScheme.outline.toArgb()
    val concreteColorArgb = MaterialTheme.colorScheme.background.toArgb()
    val mapTileFilter = remember(isDarkTheme, inkColorArgb, concreteColorArgb) {
        if (isDarkTheme) DARK_GREYSCALE_FILTER else lightModeMapFilter(inkColorArgb, concreteColorArgb)
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val markerBitmaps = remember { mutableStateMapOf<String, Bitmap?>() }
    LaunchedEffect(stations) {
        val loader = ImageLoader(context)
        stations.forEach { station ->
            val url = station.imageUrl
            if (url != null && !markerBitmaps.containsKey(station.id)) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .transformations(CircleCropTransformation())
                    .build()
                val drawable = runCatching { loader.execute(request).drawable }.getOrNull()
                markerBitmaps[station.id] = (drawable as? BitmapDrawable)?.bitmap
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!pickMode) {
            AppTopBar(
                mode = TopBarMode.MAP,
                activeViewMode = null,
                onListClick = onListClick,
                onGridClick = onGridClick,
                onMapClick = {},
                onMixesClick = onMixesClick,
                onFilterClick = { showFilterDialog = true },
                filterActive = filterActive,
                onAddClick = onAddClick,
                onSettingsClick = onSettingsClick
            )
        }
        Box(modifier = Modifier.weight(1f).clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    // filesDir (not cacheDir) so the OS doesn't reclaim previously-viewed
                    // tiles under storage pressure — that's what lets areas you've already
                    // browsed keep working without a connection.
                    osmdroidBasePath = File(ctx.filesDir, "osmdroid")
                    osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
                    tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
                    tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
                }
                MapView(ctx).apply {
                    // Without an explicit MATCH_PARENT size, osmdroid can mismeasure inside a
                    // Compose AndroidView. Disabling vertical repetition + constraining the
                    // scrollable area to the real Mercator bounds is what actually stops the
                    // map duplicating above/below the visible area at low zoom.
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setTileSource(ENGLISH_LEANING_TILE_SOURCE)
                    setMultiTouchControls(true)
                    // Osmdroid's built-in zoom buttons are plain square Android widgets we
                    // can't restyle — hidden in favor of the circular Compose ones below.
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    isVerticalMapRepetitionEnabled = false
                    setScrollableAreaLimitLatitude(MAX_MERCATOR_LATITUDE, -MAX_MERCATOR_LATITUDE, 0)
                    minZoomLevel = 3.0
                    controller.setZoom(4.0)
                    controller.setCenter(GeoPoint(20.0, 0.0))
                    overlayManager.tilesOverlay.setColorFilter(mapTileFilter)

                    if (pickMode && onLocationPicked != null) {
                        overlays.add(
                            MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    onLocationPicked(p.latitude, p.longitude)
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            })
                        )
                    }

                    mapViewRef = this
                }
            },
            update = { mapView ->
                val eventsOverlay = mapView.overlays.filterIsInstance<MapEventsOverlay>()
                mapView.overlays.clear()
                mapView.overlays.addAll(eventsOverlay)

                stations.forEach { station ->
                    val lat = station.latitude ?: return@forEach
                    val lon = station.longitude ?: return@forEach
                    val marker = Marker(mapView)
                    marker.position = GeoPoint(lat, lon)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.icon = createTeardropIcon(context, station.name, accentColor, keylineColor, markerBitmaps[station.id])
                    marker.setOnMarkerClickListener { _, _ -> onStationClick(station); true }
                    mapView.overlays.add(marker)
                }
                mapView.invalidate()
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, top = 16.dp, start = 16.dp, bottom = 16.dp + LocalPlayerBarBottomInset.current),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZoomButton(label = "+", onClick = { mapViewRef?.controller?.zoomIn() })
            ZoomButton(label = "−", onClick = { mapViewRef?.controller?.zoomOut() })
        }

        if (pickMode && onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = androidx.compose.ui.graphics.Color(0xFF17171A)
                )
            }
        }
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = androidx.compose.ui.graphics.Color(0xFF17171A),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

private fun createTeardropIcon(
    context: Context,
    name: String,
    accentColor: Int,
    keylineColor: Int,
    stationBitmap: Bitmap?
): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (48 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val radius = size * 0.32f
    val cx = size / 2f
    val cy = size * 0.36f

    paint.style = Paint.Style.FILL
    paint.color = accentColor
    canvas.drawCircle(cx, cy, radius, paint)
    val path = Path().apply {
        moveTo(cx - radius * 0.72f, cy + radius * 0.55f)
        lineTo(cx + radius * 0.72f, cy + radius * 0.55f)
        lineTo(cx, size * 0.94f)
        close()
    }
    canvas.drawPath(path, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * 0.036f
    paint.color = keylineColor
    canvas.drawCircle(cx, cy, radius, paint)

    if (stationBitmap != null) {
        val destRect = RectF(cx - radius * 0.62f, cy - radius * 0.62f, cx + radius * 0.62f, cy + radius * 0.62f)
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(stationBitmap, null, destRect, paint)
    } else {
        val fillColor = PIN_PALETTE[Math.floorMod(name.hashCode(), PIN_PALETTE.size)]
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawCircle(cx, cy, radius * 0.62f, paint)

        val luminance = (AndroidColor.red(fillColor) * 0.299 + AndroidColor.green(fillColor) * 0.587 + AndroidColor.blue(fillColor) * 0.114) / 255
        paint.color = if (luminance > 0.5) 0xFF14140F.toInt() else 0xFFF1EDE2.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 0.75f
        paint.isFakeBoldText = true
        val fm = paint.fontMetrics
        canvas.drawText(name.trim().take(1).ifEmpty { "?" }.uppercase(), cx, cy - (fm.ascent + fm.descent) / 2, paint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
