package net.nobu0707.busnav.ui.search

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.search.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaceSearchDialogTest {
    @get:Rule val compose = createComposeRule()

    private val station = PlaceSearchItem(PlaceObjectId("N123"), "東京駅", "東京駅, 東京都",
        GeoPoint(35.681, 139.767), PlaceCategory.STATION, "railway", "station",
        PlaceAddress("千代田区、東京都", emptyMap()), null, emptyMap(), emptyMap(), 2500.0)

    @Test fun japaneseQueryAndCandidateDetailsRequireSelection() {
        var query = ""
        var selected: PlaceSearchItem? = null
        compose.setContent {
            PlaceSearchDialog(PlaceSearchUiState(items = listOf(station), searched = true), false,
                onQuery = { query = it }, onBias = {}, onSubmit = {}, onSelect = { selected = it }, onDismiss = {})
        }
        compose.onNodeWithTag("place_search_field").performTextInput("東京駅")
        assertEquals("東京駅", query)
        compose.onNodeWithText("駅", substring = false).assertExists()
        compose.onNodeWithText("千代田区、東京都").assertExists()
        compose.onNodeWithText("© OpenStreetMap contributors").assertExists()
        compose.onNodeWithTag("place_search_result_N123").performClick()
        assertEquals(station, selected)
    }

    @Test fun drivingLocksTextEntryAndShowsSafetyMessage() {
        compose.setContent {
            PlaceSearchDialog(PlaceSearchUiState(), true, {}, {}, {}, {}, {})
        }
        compose.onNodeWithTag("place_search_field").assertIsNotEnabled()
        compose.onNodeWithText("安全な場所に停車して検索してください").assertExists()
    }
}
