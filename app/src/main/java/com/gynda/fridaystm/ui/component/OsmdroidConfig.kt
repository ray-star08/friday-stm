package com.gynda.fridaystm.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView

/**
 * One-time osmdroid bootstrap, shared by every map surface ([GeofenceMiniMap] and
 * the Larkam run map).
 *
 * osmdroid persists its own configuration (tile cache paths, user agent) into a
 * `SharedPreferences`; a dedicated `osmdroid` file is used rather than the app's
 * default prefs so map internals never collide with app state. Setting
 * `userAgentValue` is mandatory — OpenStreetMap tile servers answer 403 without it.
 *
 * Render-bug fix: the user-agent is applied synchronously via
 * [ensureOsmdroidConfigured] BEFORE the first `MapView` is created. Previously the
 * agent was only set inside an async `LaunchedEffect`, so the first tile
 * requests fired without an agent, got 403s, and the map stayed a grey grid.
 */
@Volatile
private var osmdroidLoaded = false

fun ensureOsmdroidConfigured(context: Context) {
    val appContext = context.applicationContext
    // Cheap + synchronous: must be visible before the first tile request.
    Configuration.getInstance().userAgentValue = "FridaySTM/1.0 (${appContext.packageName})"
    if (osmdroidLoaded) return
    synchronized(OsmdroidLock) {
        if (osmdroidLoaded) return
        try {
            val prefs = appContext.getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE)
            Configuration.getInstance().load(appContext, prefs)
            // load() may overwrite the agent — re-apply afterwards.
            Configuration.getInstance().userAgentValue = "FridaySTM/1.0 (${appContext.packageName})"
            Configuration.getInstance().tileDownloadThreads = 4
            Configuration.getInstance().tileDownloadMaxQueueSize = 40
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 250L * 1024L * 1024L
        } catch (_: Exception) {
            // Best-effort: a map with default cache still renders.
        }
        osmdroidLoaded = true
    }
}

private val OsmdroidLock = Any()

/**
 * Suspending entry point kept for existing callers. Does its disk work on
 * [Dispatchers.IO]: the prefs load touches storage and must not block the main
 * thread during startup.
 */
internal suspend fun configureOsmdroid(context: Context) = withContext(Dispatchers.IO) {
    ensureOsmdroidConfigured(context)
}

/** Shared defaults for every FridaySTM map: crisp tiles on xxhdpi, pinch-zoom. */
fun MapView.applyFridayDefaults() {
    setTilesScaledToDpi(true)
    setMultiTouchControls(true)
}

/** Zoom level that frames a geofence of [radiusMeter] with margin. */
fun zoomForRadius(radiusMeter: Int): Double = when {
    radiusMeter <= 50 -> 18.5
    radiusMeter <= 100 -> 18.0
    radiusMeter <= 150 -> 17.5
    radiusMeter <= 250 -> 17.0
    radiusMeter <= 500 -> 16.0
    radiusMeter <= 1_000 -> 15.0
    else -> 14.0
}

/**
 * OpenStreetMap Standard TileSource: 100% free, no API key required.
 */
val OsmStandard = TileSourceFactory.MAPNIK

/**
 * Humanitarian OpenStreetMap TileSource: modern pastel light style, 100% free.
 */
val OsmHot = XYTileSource(
    "OsmHot",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.fr/hot/",
        "https://b.tile.openstreetmap.fr/hot/",
        "https://c.tile.openstreetmap.fr/hot/"
    ),
    "© OpenStreetMap contributors, Tiles courtesy of Humanitarian OpenStreetMap Team"
)

/**
 * CARTO dark-matter tiles for dark theme. Using real dark tiles instead of an
 * invert [android.graphics.ColorMatrix] filter keeps labels legible and avoids
 * the washed-out grey the matrix produced on HOT tiles.
 */
val OsmDark = XYTileSource(
    "CartoDark",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

/** Tile source per theme: light → HOT pastel, dark → CARTO dark. */
fun defaultTileSource(isDark: Boolean) = if (isDark) OsmDark else OsmHot

/**
 * Esri World Imagery (Satellite) TileSource: free satellite imagery, no API key required.
 */
val EsriSatellite = XYTileSource(
    "EsriSatellite",
    0, 19, 256, ".jpg",
    arrayOf(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
    ),
    "Tiles © Esri — Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community"
)

/**
 * Dark mode ColorMatrix filter for OpenStreetMap tiles.
 *
 * @deprecated Prefer [OsmDark] real dark tiles (see [defaultTileSource]).
 * Kept for back-compat only; new surfaces must not use it.
 */
@Deprecated("Use OsmDark tiles instead of a color-matrix filter.")
val OsmDarkColorFilter = android.graphics.ColorMatrixColorFilter(
    android.graphics.ColorMatrix(
        floatArrayOf(
            -0.8f, 0.0f, 0.0f, 0.0f, 230.0f,
            0.0f, -0.8f, 0.0f, 0.0f, 230.0f,
            0.0f, 0.0f, -0.8f, 0.0f, 230.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
    )
)

/** Attribution line drawn on every map (OSM licence requirement). */
const val MAP_ATTRIBUTION = "© OpenStreetMap · HOT / CARTO"

/**
 * Blue-dot "my location" icon: primary fill, white ring, soft halo.
 * Replaces the tiny default osmdroid pin which looked lost on the mini-map.
 */
fun userDotDrawable(context: Context, @ColorInt fillColor: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (26 * density).toInt().coerceAtLeast(40)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        alpha = 38
    }
    canvas.drawCircle(cx, cy, cx - 1f, halo)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(cx, cy, size * 0.30f, border)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        alpha = 255
    }
    canvas.drawCircle(cx, cy, size * 0.22f, dot)
    return BitmapDrawable(context.resources, bmp)
}

/**
 * osmdroid's own preferences file. Replaces the deprecated
 * `android.preference.PreferenceManager.getDefaultSharedPreferences`, without
 * pulling in the `androidx.preference` artifact for a single call.
 */
private const val OSMDROID_PREFS = "osmdroid"
