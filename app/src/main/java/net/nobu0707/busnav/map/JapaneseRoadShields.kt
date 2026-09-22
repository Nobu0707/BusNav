package net.nobu0707.busnav.map

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import net.nobu0707.busnav.R
import org.maplibre.android.maps.Style

/** Original vector backgrounds; numbers remain dynamic SymbolLayer text. Reinstall on every style load. */
object JapaneseRoadShields {
    val ids = listOf("jp-expressway", "jp-expressway-wide", "jp-national", "jp-prefectural", "jp-urban", "jp-urban-ring", "jp-facility-label",
        "jp-facility-access", "jp-facility-junction", "jp-facility-toll", "jp-intersection-light", "jp-intersection-dark")
    fun install(context: Context, style: Style) {
        val resources = listOf(R.drawable.road_shield_expressway, R.drawable.road_shield_expressway_wide,
            R.drawable.road_shield_national, R.drawable.road_shield_prefectural,
            R.drawable.road_shield_urban,
            R.drawable.road_shield_urban_ring,
            R.drawable.road_shield_facility_label,
            R.drawable.road_shield_facility_access,
            R.drawable.road_shield_facility_junction,
            R.drawable.road_shield_facility_toll,
            R.drawable.road_shield_intersection_light,
            R.drawable.road_shield_intersection_dark)
        val sizes = listOf(36 to 28, 46 to 28, 38 to 36, 38 to 34, 38 to 36, 38 to 36, 16 to 16, 22 to 22, 22 to 22, 22 to 22, 16 to 16, 16 to 16)
        ids.indices.forEach { index ->
            val (width, height) = sizes[index]
            val bitmap = createBitmap(width * 2, height * 2).apply { density = 320 }
            requireNotNull(ContextCompat.getDrawable(context, resources[index])).apply {
                setBounds(0, 0, bitmap.width, bitmap.height)
                draw(Canvas(bitmap))
            }
            if (ids[index] == "jp-facility-label" || ids[index].startsWith("jp-intersection-")) {
                // Stretch the center only; keep the border/corners crisp at arbitrary text lengths.
                style.addImage(ids[index], bitmap,
                    listOf(org.maplibre.android.maps.ImageStretches(8f, 24f)),
                    listOf(org.maplibre.android.maps.ImageStretches(8f, 24f)),
                    org.maplibre.android.maps.ImageContent(4f, 4f, 28f, 28f))
            } else style.addImage(ids[index], bitmap)
        }
    }
}
