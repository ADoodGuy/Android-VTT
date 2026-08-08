package com.adoodguy.androidvtt.tabletop

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawImage
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_MAP_WIDTH_CELLS = 20.0
private const val MAX_MAP_DIMENSION_CELLS = 100_000.0
private const val MAX_DECODED_IMAGE_DIMENSION = 4096

/**
 * A map is positioned by its center in world/cell coordinates and sized in cells.
 * The image URI comes from Android's document picker and is persisted separately
 * from the source image bytes.
 */
data class TabletopMapConfiguration(
    val imageUri: String? = null,
    val widthCells: Double = DEFAULT_MAP_WIDTH_CELLS,
    val heightCells: Double = DEFAULT_MAP_WIDTH_CELLS,
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
) {
    val hasImage: Boolean get() = !imageUri.isNullOrBlank()
}

object TabletopMapStore {
    private const val PREFS_NAME = "tabletop_map"
    private const val KEY_URI = "image_uri"
    private const val KEY_WIDTH = "width_cells"
    private const val KEY_HEIGHT = "height_cells"
    private const val KEY_CENTER_X = "center_x"
    private const val KEY_CENTER_Y = "center_y"

    private var appContext: Context? = null

    var configuration by mutableStateOf(TabletopMapConfiguration())
        private set

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configuration = TabletopMapConfiguration(
            imageUri = prefs.getString(KEY_URI, null),
            widthCells = prefs.getString(KEY_WIDTH, null)?.toDoubleOrNull()
                ?.takeIf(::isValidDimension)
                ?: DEFAULT_MAP_WIDTH_CELLS,
            heightCells = prefs.getString(KEY_HEIGHT, null)?.toDoubleOrNull()
                ?.takeIf(::isValidDimension)
                ?: DEFAULT_MAP_WIDTH_CELLS,
            centerX = prefs.getString(KEY_CENTER_X, null)?.toDoubleOrNull()
                ?.takeIf(Double::isFinite)
                ?: 0.0,
            centerY = prefs.getString(KEY_CENTER_Y, null)?.toDoubleOrNull()
                ?.takeIf(Double::isFinite)
                ?: 0.0,
        )
    }

    fun importImage(uri: Uri, aspectRatio: Double?) {
        val context = appContext ?: return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant readable document URIs without a persistable grant.
            // The current session can still use the image; a later restart may require
            // selecting it again if the provider revokes access.
        }

        val old = configuration
        configuration = if (old.hasImage) {
            old.copy(imageUri = uri.toString())
        } else {
            val safeRatio = aspectRatio?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            old.copy(
                imageUri = uri.toString(),
                widthCells = DEFAULT_MAP_WIDTH_CELLS,
                heightCells = (DEFAULT_MAP_WIDTH_CELLS / safeRatio)
                    .coerceIn(0.1, MAX_MAP_DIMENSION_CELLS),
            )
        }
        persist()
    }

    fun updateGeometry(
        widthCells: Double,
        heightCells: Double,
        centerX: Double,
        centerY: Double,
    ): Boolean {
        if (!isValidDimension(widthCells) || !isValidDimension(heightCells)) return false
        if (!centerX.isFinite() || !centerY.isFinite()) return false
        configuration = configuration.copy(
            widthCells = widthCells,
            heightCells = heightCells,
            centerX = centerX,
            centerY = centerY,
        )
        persist()
        return true
    }

    fun removeMap() {
        val context = appContext
        val uri = configuration.imageUri?.let(Uri::parse)
        if (context != null && uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // No persisted grant existed; clearing the stored configuration is enough.
            }
        }
        configuration = TabletopMapConfiguration()
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val config = configuration
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (config.imageUri == null) remove(KEY_URI) else putString(KEY_URI, config.imageUri)
                putString(KEY_WIDTH, config.widthCells.toString())
                putString(KEY_HEIGHT, config.heightCells.toString())
                putString(KEY_CENTER_X, config.centerX.toString())
                putString(KEY_CENTER_Y, config.centerY.toString())
            }
            .apply()
    }

    private fun isValidDimension(value: Double): Boolean =
        value.isFinite() && value in 0.1..MAX_MAP_DIMENSION_CELLS
}

fun readMapImageAspectRatio(context: Context, uri: Uri): Double? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return options.outWidth.toDouble() / options.outHeight.toDouble()
}

@Composable
fun rememberTabletopMapImage(imageUri: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUri) {
        image = withContext(Dispatchers.IO) {
            imageUri?.let { decodeTabletopMap(context, Uri.parse(it)) }
        }
    }
    return image
}

private fun decodeTabletopMap(context: Context, uri: Uri): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    var sampledWidth = bounds.outWidth
    var sampledHeight = bounds.outHeight
    while (max(sampledWidth, sampledHeight) > MAX_DECODED_IMAGE_DIMENSION) {
        sampleSize *= 2
        sampledWidth = bounds.outWidth / sampleSize
        sampledHeight = bounds.outHeight / sampleSize
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
    }
}

fun DrawScope.drawTabletopMap(
    image: ImageBitmap?,
    configuration: TabletopMapConfiguration,
    state: TabletopState,
) {
    if (image == null || !configuration.hasImage) return

    val halfWidthWorld = configuration.widthCells * state.cellSizeWorldUnits / 2.0
    val halfHeightWorld = configuration.heightCells * state.cellSizeWorldUnits / 2.0
    val topLeft = state.worldToScreen(
        WorldPoint(
            x = configuration.centerX - halfWidthWorld,
            y = configuration.centerY - halfHeightWorld,
        ),
    )
    val bottomRight = state.worldToScreen(
        WorldPoint(
            x = configuration.centerX + halfWidthWorld,
            y = configuration.centerY + halfHeightWorld,
        ),
    )

    val left = minOf(topLeft.x, bottomRight.x)
    val top = minOf(topLeft.y, bottomRight.y)
    val width = kotlin.math.abs(bottomRight.x - topLeft.x).roundToInt().coerceAtLeast(1)
    val height = kotlin.math.abs(bottomRight.y - topLeft.y).roundToInt().coerceAtLeast(1)

    drawImage(
        image = image,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.Medium,
    )
}
