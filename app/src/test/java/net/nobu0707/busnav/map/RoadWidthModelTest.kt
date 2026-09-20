package net.nobu0707.busnav.map

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Test shipped style expressions, including hierarchy between stops. */
class RoadWidthModelTest {
    private fun style(name: String): JsonObject {
        val path = listOf(File("../tools/basemap/style/$name.json"),File("tools/basemap/style/$name.json")).first { it.exists() }
        return Json.parseToJsonElement(path.readText()).jsonObject
    }
    private fun width(layer: JsonObject, zoom: Double): Double {
        val e=layer["paint"]!!.jsonObject["line-width"]!!.jsonArray
        assertEquals("interpolate",e[0].jsonPrimitive.content)
        val stops=e.drop(3).chunked(2).map { it[0].jsonPrimitive.double to it[1].jsonPrimitive.double }
        if(zoom<=stops.first().first) return stops.first().second
        for((a,b) in stops.zipWithNext()) if(zoom<=b.first) return a.second+(b.second-a.second)*(zoom-a.first)/(b.first-a.first)
        return stops.last().second
    }
    @Test fun bothStylesPreserveHierarchyIncreaseAtHighZoomAndKeepLabels() {
        val names=listOf("service","minor","tertiary","secondary","primary","trunk","motorway")
        for(theme in listOf("busnav","busnav-light")) {
            val layers=style(theme)["layers"]!!.jsonArray.map { it.jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
            for(z in 4..22) {
                val widths=names.map { width(layers.getValue("roads-$it"),z.toDouble()) }
                assertTrue(widths.zipWithNext().all { (a,b)->b>a })
                for(name in names) {
                    val road=layers.getValue("roads-$name")
                    assertTrue(width(road,z+0.5)>=width(road,z.toDouble()))
                    assertTrue(width(layers.getValue("roads-$name-casing"),z.toDouble())>width(road,z.toDouble()))
                }
            }
            assertTrue(width(layers.getValue("roads-motorway"),20.0)>width(layers.getValue("roads-motorway"),16.0)*2)
            listOf("water","railways","buildings","road-labels","place-major","road-tunnels").forEach { assertTrue(layers.containsKey(it)) }
            assertTrue(layers.getValue("road-labels").toString().contains("name:ja"))
        }
    }
}
