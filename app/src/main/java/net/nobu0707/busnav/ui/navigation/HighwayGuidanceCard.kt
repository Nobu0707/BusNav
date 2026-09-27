package net.nobu0707.busnav.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nobu0707.busnav.ui.theme.navigationWarningColor

@Composable
fun NavigationGuidanceCard(state: NavigationUiState, modifier: Modifier = Modifier, compact: Boolean = false,
    landscape: Boolean = false, availableHeight: androidx.compose.ui.unit.Dp = 700.dp) {
    val highway = state.highwayGuidance
    if (!compact) {
        if (highway == null) GuidanceCard(state.guidance, modifier) else HighwayGuidanceCard(highway, modifier)
        return
    }
    val primary = highway?.primaryText ?: state.guidance.primaryText
    val distance = if (highway != null) highway.distanceText else state.guidance.distanceText
    val portraitMinHeight = (availableHeight * 0.22f).coerceAtMost(180.dp)
    val cardModifier = if (landscape) modifier else modifier.heightIn(min = portraitMinHeight,
        max = availableHeight * 0.28f)
    Card(cardModifier.testTag(if (highway == null) NavigationTestTags.NEXT_GUIDANCE else "highway_guidance")
        .semantics { if (highway != null) contentDescription = highway.contentDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth()
            .then(if (landscape) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(if (landscape) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 2.dp else 6.dp)) {
            state.deviation.message?.let { warning ->
                val warningColor = navigationWarningColor(state.deviation.isProminent,
                    MaterialTheme.colorScheme.background.luminance() < 0.5f,
                    MaterialTheme.colorScheme.onSurfaceVariant)
                Text(warning, Modifier.fillMaxWidth().testTag("deviation_banner")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    color = warningColor, fontSize = if (landscape) 18.sp else 21.sp,
                    fontWeight = if (state.deviation.isProminent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state.deviation.message == null || state.guidance.status != GuidanceStatus.NO_ROUTE || highway != null) {
                if (landscape) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (highway?.schematic != null) JunctionSchematic(highway.schematic, Modifier.size(36.dp))
                    else if (highway == null) Text(state.guidance.symbol, fontSize = 28.sp)
                    distance?.let { Text(it, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(if (highway == null) "guidance_distance" else "highway_distance")) }
                    Text(primary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("guidance_instruction"))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (highway?.schematic != null) JunctionSchematic(highway.schematic, Modifier.size(52.dp))
                        else if (highway == null) Text(state.guidance.symbol, fontSize = 40.sp)
                        Text(primary, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).testTag("guidance_instruction"))
                    }
                    distance?.let { Text(it, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(if (highway == null) "guidance_distance" else "highway_distance")) }
                }
            }

            if (highway != null) {
                val facility = listOfNotNull(highway.sign.exitNumber?.let { "出口 $it" },
                    highway.sign.facilityNames.joinToString(" / ").ifEmpty { null }).joinToString(" · ")
                if (facility.isNotEmpty()) Text(facility, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (highway.sign.toward.isNotEmpty()) Text(highway.sign.toward.joinToString(" / "),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    highway.sign.routeRefs.take(3).forEach { ref ->
                        Text(ref, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
            (if (highway != null) highway.secondaryText else state.guidance.secondaryText)?.let {
                Text(it, fontSize = if (landscape) 14.sp else 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            (if (highway != null) highway.nextText else state.guidance.nextNextInstruction)?.let {
                Text("その次: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(if (highway == null) "guidance_next_next" else "highway_next"))
            }
        }
    }
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
