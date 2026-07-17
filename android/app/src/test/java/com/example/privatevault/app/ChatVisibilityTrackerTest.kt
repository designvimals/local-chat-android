package com.example.privatevault.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVisibilityTrackerTest {
    @Test
    fun chatIsOnlineOnlyWhenActivityIsForegroundAndChatIsSelected() {
        val tracker = ChatVisibilityTracker()

        assertFalse(tracker.isChatVisible)
        tracker.setActivityForeground(true)
        assertFalse(tracker.isChatVisible)

        tracker.setChatDestinationSelected(true)
        assertTrue(tracker.isChatVisible)

        tracker.setActivityForeground(false)
        assertFalse(tracker.isChatVisible)

        tracker.setActivityForeground(true)
        assertTrue(tracker.isChatVisible)
    }
}
