package net.nobu0707.busnav.map

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class JapaneseRoadStyleTest {
    private fun layers(theme: String): List<JsonObject> {
        val file = listOf(File("../tools/basemap/style/$theme.json"), File("tools/basemap/style/$theme.json")).first { it.exists() }
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("layers").jsonArray.map { it.jsonObject }
    }
    @Test fun shieldsHaveParityCollisionZoomDensityAndCenteredDynamicText() {
        val light = layers("busnav-light")
        val dark = layers("busnav")
        val shields = light.filter { it.getValue("id").jsonPrimitive.content.startsWith("route-shield-") }
        assertEquals(4, shields.size)
        assertEquals(shields, dark.filter { it.getValue("id").jsonPrimitive.content.startsWith("route-shield-") })
        for ((i, shield) in shields.withIndex()) {
            assertEquals("transportation_name", shield.getValue("source-layer").jsonPrimitive.content)
            assertEquals(listOf(7, 12, 8, 13)[i], shield.getValue("minzoom").jsonPrimitive.int)
            val layout = shield.getValue("layout").jsonObject
            assertEquals("none", layout.getValue("visibility").jsonPrimitive.content)
            assertEquals(listOf(420, 420, 550, 680)[i], layout.getValue("symbol-spacing").jsonPrimitive.int)
            assertEquals(i, layout.getValue("symbol-sort-key").jsonPrimitive.int)
            assertEquals("line", layout.getValue("symbol-placement").jsonPrimitive.content)
            assertEquals("center", layout.getValue("text-anchor").jsonPrimitive.content)
            assertEquals("center", layout.getValue("icon-anchor").jsonPrimitive.content)
            for (prefix in listOf("icon", "text")) {
                assertFalse(layout.getValue("$prefix-allow-overlap").jsonPrimitive.boolean)
                assertFalse(layout.getValue("$prefix-ignore-placement").jsonPrimitive.boolean)
                assertFalse(layout.getValue("$prefix-optional").jsonPrimitive.boolean)
            }
            assertEquals("[\"get\",\"route_ref\"]", layout.getValue("text-field").toString())
        }
        assertFalse(light.any { it.getValue("id").jsonPrimitive.content == "motorway-refs" })
    }
    @Test fun detailLayersUseBackgroundsCollisionAndCorrectAnchor() {
        for (theme in listOf("busnav", "busnav-light")) {
            val all = layers(theme)
            val ids = all.map { it.getValue("id").jsonPrimitive.content }
            assertTrue(ids.indexOf("busnav-shield-anchor") < ids.indexOf("route-shield-urban_expressway"))
            for (group in listOf("junction", "access", "toll")) {
                val layer = all.first { it.getValue("id").jsonPrimitive.content == "facility-$group-label" }
                val layout = layer.getValue("layout").jsonObject
                assertEquals("both", layout.getValue("icon-text-fit").jsonPrimitive.content)
                assertEquals("jp-facility-label", layout.getValue("icon-image").jsonPrimitive.content)
                assertEquals("viewport", layout.getValue("text-rotation-alignment").jsonPrimitive.content)
                assertFalse(layout.getValue("text-allow-overlap").jsonPrimitive.boolean)
            }
            assertTrue(ids.containsAll(listOf("intersection-major", "intersection-normal")))
        }
    }
    @Test fun allRoadFillsAndStructuresUseEvidenceBasedPaletteWithNeutralFallback() {
        for ((theme, colors) in listOf("busnav-light" to listOf("#397BB8", "#D16D61", "#50956C"),
                                     "busnav" to listOf("#629AD0", "#D7857D", "#71AD86"))) {
            val roads = layers(theme).filter { val id = it.getValue("id").jsonPrimitive.content
                (id.startsWith("roads-") && !id.endsWith("-casing")) || id in listOf("road-tunnels", "road-bridges") }
            assertEquals(9, roads.size)
            roads.forEach {
                val color = it.getValue("paint").jsonObject.getValue("line-color").jsonArray
                assertEquals("[\"get\",\"route_network\"]", color[1].toString())
                val structure = it.getValue("id").jsonPrimitive.content in listOf("road-tunnels", "road-bridges")
                val expected = if (!structure) colors else if (theme == "busnav-light")
                    listOf("#84AED1", "#E3ADA5", "#91BEA2") else listOf("#99BDDF", "#E3B0AA", "#A3C9B0")
                assertEquals(expected, listOf(3, 5, 7).map { index -> color[index].jsonPrimitive.content })
                if (structure) assertTrue(expected.zip(colors).all { (stroke, fill) -> stroke != fill })
                assertNotNull(color[8])
            }
        }
    }
}
