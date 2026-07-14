package com.example.privatevault.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendEmphasisStateTest {
    @Test fun maximumOnlyPopsOnceUntilRearmed() {
        val machine = SendEmphasisStateMachine()
        machine.press("hello")
        machine.beginHolding()
        val firstMaximum = machine.updateProgress(1f)
        val heldMaximum = machine.updateProgress(1f)

        assertEquals(SendInteractionPhase.AtMaximum, firstMaximum.phase)
        assertEquals(firstMaximum.popCount, heldMaximum.popCount)

        machine.updateProgress(0.5f)
        val rearmed = machine.updateProgress(1f)
        assertTrue(rearmed.popCount > firstMaximum.popCount)
    }

    @Test fun resetStopsMaximumLifecycle() {
        val machine = SendEmphasisStateMachine()
        machine.press("hello")
        machine.beginHolding()
        machine.updateProgress(1f)

        val reset = machine.reset()

        assertEquals(SendInteractionPhase.Idle, reset.phase)
        assertFalse(reset.showsPreview)
    }
}
