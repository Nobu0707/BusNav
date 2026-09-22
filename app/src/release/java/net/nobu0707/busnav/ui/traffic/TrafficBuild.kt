package net.nobu0707.busnav.ui.traffic

import androidx.compose.runtime.Composable
import net.nobu0707.busnav.domain.traffic.*

fun createTrafficProvider(): TrafficInformationProvider = NoOpTrafficInformationProvider()
@Composable fun TrafficDeveloperControls(provider: TrafficInformationProvider) = Unit
