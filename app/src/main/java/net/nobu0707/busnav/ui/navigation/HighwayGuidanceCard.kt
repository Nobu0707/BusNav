package net.nobu0707.busnav.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NavigationGuidanceCard(state: NavigationUiState, modifier: Modifier = Modifier) {
    val highway = state.highwayGuidance
    if (highway == null) GuidanceCard(state.guidance, modifier) else HighwayGuidanceCard(highway, modifier)
}

@Composable
fun HighwayGuidanceCard(state: HighwayGuidanceUiState, modifier: Modifier = Modifier) {
    Card(modifier.testTag("highway_guidance").semantics { contentDescription = state.contentDescription }) {
        Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.schematic?.let { JunctionSchematic(it, Modifier.size(64.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(state.primaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    state.distanceText?.let { Text(it, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (state.emphasized) FontWeight.ExtraBold else FontWeight.Bold,
                        modifier = Modifier.testTag("highway_distance")) }
                }
            }
            val facility = listOfNotNull(state.sign.exitNumber?.let { "出口 $it" },
                state.sign.facilityNames.joinToString(" / ").ifEmpty { null }).joinToString(" · ")
            if (facility.isNotEmpty()) Text(facility, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.sign.toward.isNotEmpty()) Text(state.sign.toward.joinToString(" / "),
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.sign.routeRefs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.sign.routeRefs.take(3).forEach { ref ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f, fill = false)) {
                        Text(ref, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            state.secondaryText?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            state.nextText?.let { Text("その次: $it", style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("highway_next")) }
        }
    }
}

/** Topology only: no lane markings, counts, or claims about the actual junction geometry. */
@Composable
fun JunctionSchematic(model: JunctionSchematicModel, modifier: Modifier = Modifier) {
    val selected = MaterialTheme.colorScheme.primary
    val other = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.testTag("highway_schematic")) {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        val start = point(.5f, .94f)
        val fork = point(.5f, .55f)
        val end = when (model.selectedBranch) {
            SchematicDirection.LEFT -> point(.12f, .12f)
            SchematicDirection.RIGHT -> point(.88f, .12f)
            else -> point(.5f, .08f)
        }
        val background = Path().apply { moveTo(start.x, start.y); lineTo(fork.x, fork.y); lineTo(size.width * .5f, size.height * .08f) }
        drawPath(background, other, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        if (!model.isExit) {
            val side = if (model.selectedBranch == SchematicDirection.LEFT) .88f else .12f
            drawLine(other, fork, point(side, .12f), 7.dp.toPx(), StrokeCap.Round)
        }
        if (model.selectedBranch == SchematicDirection.MERGE) {
            drawLine(other, point(.12f, .94f), fork, 7.dp.toPx(), StrokeCap.Round)
        }
        val route = Path().apply { moveTo(start.x, start.y); lineTo(fork.x, fork.y); lineTo(end.x, end.y) }
        drawPath(route, selected, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        val delta = end - fork
        val length = delta.getDistance()
        val unit = delta / length
        val base = end - unit * 13.dp.toPx()
        val normal = Offset(-unit.y, unit.x) * 7.dp.toPx()
        drawPath(Path().apply { moveTo(end.x, end.y); lineTo((base + normal).x, (base + normal).y)
            lineTo((base - normal).x, (base - normal).y); close() }, selected)
    }
}
