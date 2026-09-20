package net.nobu0707.busnav.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PresentationComposeTest {
    @get:Rule val rule=createComposeRule()
    @Test fun portrait320LightAndDarkSingleLineLabels() = labels(false)
    @Test fun landscapeNarrowColumnLightAndDarkSingleLineLabels() = labels(true)
    private fun labels(vertical:Boolean) {
        val dark=mutableStateOf(false)
        val scale=mutableStateOf(1f)
        rule.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.value)) {
                BusNavTheme(dark.value) {
                    AuxiliaryControls({},if(vertical) Modifier.requiredSize(108.dp,360.dp)
                        else Modifier.requiredSize(304.dp,72.dp),vertical)
                }
            }
        }
        for(mode in listOf(false,true)) for(fontScale in listOf(1f,1.3f)) {
            rule.runOnIdle { dark.value=mode;scale.value=fontScale }
            BottomLabelLayout.labels.forEach { label ->
                val results=mutableListOf<TextLayoutResult>()
                rule.onNodeWithText(label).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                assertEquals(1,results.single().lineCount)
                assertFalse(label+" overflow mode="+mode+" scale="+fontScale+" size="+results.single().size+" width="+results.single().didOverflowWidth+" height="+results.single().didOverflowHeight+" input="+results.single().layoutInput.constraints+" style="+results.single().layoutInput.style,results.single().hasVisualOverflow)
            }
        }
    }
    @Test fun bareTextInheritsReadableContentColorAcrossRuntimeSwitch() {
        val dark=mutableStateOf(false)
        var foreground=Color.Unspecified
        var background=Color.Unspecified
        rule.setContent {
            BusNavTheme(dark.value) {
                foreground=LocalContentColor.current
                background=MaterialTheme.colorScheme.background
                Text("文字色")
            }
        }
        for(mode in listOf(false,true,false)) {
            rule.runOnIdle { dark.value=mode }
            rule.runOnIdle {
                val a=foreground.luminance();val b=background.luminance()
                assertTrue((maxOf(a,b)+.05)/(minOf(a,b)+.05)>7)
            }
        }
    }
}
