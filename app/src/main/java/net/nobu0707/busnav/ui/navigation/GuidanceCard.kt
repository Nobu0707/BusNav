package net.nobu0707.busnav.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun GuidanceCard(state: GuidanceUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.testTag(NavigationTestTags.NEXT_GUIDANCE)) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.symbol, style = MaterialTheme.typography.titleLarge)
                state.distanceText?.let { Text(it, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("guidance_distance")) }
            }
            Text(state.primaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("guidance_instruction"))
            state.secondaryText?.let { Text(it, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis) }
            state.nextNextInstruction?.let { Text("その次: " + it, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("guidance_next_next")) }
        }
    }
}
