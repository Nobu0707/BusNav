package net.nobu0707.busnav.data.routing.valhalla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingDiagnosticsTest {
    @Test
    fun `noop diagnostics do not evaluate expensive details`() {
        var evaluated = false

        NoOpRoutingDiagnostics.debug("response.received") {
            evaluated = true
            "bodySha256=expensive"
        }
        NoOpRoutingDiagnostics.error("response.parse.failed") {
            evaluated = true
            "expensive error details"
        }

        assertFalse(NoOpRoutingDiagnostics.isDebugEnabled)
        assertFalse(NoOpRoutingDiagnostics.isErrorEnabled)
        assertFalse(evaluated)
    }

    @Test
    fun `recording diagnostics evaluate and retain details`() {
        val diagnostics = RecordingRoutingDiagnostics()
        var debugEvaluated = false
        var errorEvaluated = false

        diagnostics.debug("response.received") {
            debugEvaluated = true
            "bodySha256=abc123"
        }
        diagnostics.error("response.parse.failed") {
            errorEvaluated = true
            "stage=JSON_DECODE"
        }

        assertTrue(diagnostics.isDebugEnabled)
        assertTrue(diagnostics.isErrorEnabled)
        assertTrue(debugEvaluated)
        assertTrue(errorEvaluated)
        assertEquals("bodySha256=abc123", diagnostics.debugEntries.single().details)
        assertEquals("stage=JSON_DECODE", diagnostics.errorEntries.single().details)
    }
}
