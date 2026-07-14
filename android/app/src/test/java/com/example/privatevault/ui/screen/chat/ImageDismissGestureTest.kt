package com.example.privatevault.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageDismissGestureTest {
    @Test fun fastFlickDismissesWithoutCrossingDistanceThreshold() {
        assertEquals(1_000f, target(offset = 12f, velocity = 950f))
        assertEquals(-1_000f, target(offset = -12f, velocity = -950f))
    }

    @Test fun projectedMomentumCanCommitAShortFlick() {
        assertEquals(1_000f, target(offset = 24f, velocity = 500f))
        assertEquals(-1_000f, target(offset = -24f, velocity = -500f))
    }

    @Test fun slowShortDragReturnsToCenter() {
        assertNull(target(offset = 24f, velocity = 100f))
    }

    @Test fun distanceThresholdDismissesInDragDirection() {
        assertEquals(1_000f, target(offset = 96f, velocity = 0f))
        assertEquals(-1_000f, target(offset = -96f, velocity = 0f))
    }

    private fun target(offset: Float, velocity: Float): Float? = imageDismissTarget(
        offset = offset,
        velocity = velocity,
        distanceThreshold = 96f,
        velocityThreshold = 900f,
        viewportHeight = 1_000f
    )
}
