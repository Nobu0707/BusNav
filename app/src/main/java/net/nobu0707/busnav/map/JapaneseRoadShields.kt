package net.nobu0707.busnav.map

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import net.nobu0707.busnav.R
import org.maplibre.android.maps.Style

/** Original vector backgrounds; numbers remain dynamic SymbolLayer text. Reinstall on every style load. */
object JapaneseRoadShields {
    val ids = listOf("jp-expressway", "jp-expressway-wide", "jp-national", "jp-prefectural")
    fun install(context: Context, style: Style) {
        val resources = listOf(R.drawable.road_shield_expressway, R.drawable.road_shield_expressway_wide,
            R.drawable.road_shield_national, R.drawable.road_shield_prefectural)
        val sizes = listOf(36 to 28, 46 to 28, 38 to 36, 38 to 34)
        ids.indices.forEach { index ->
            val (width, height) = sizes[index]
            val bitmap = createBitmap(width * 2, height * 2).apply { density = 320 }
            requireNotNull(ContextCompat.getDrawable(context, resources[index])).apply {
                setBounds(0, 0, bitmap.width, bitmap.height)
                draw(Canvas(bitmap))
            }
            style.addImage(ids[index], bitmap)
        }
    }
}
