package com.example.privatevault.app

/** Process-wide visibility state shared by the activity and background relay. */
class ChatVisibilityTracker {
    @Volatile
    private var activityForeground = false

    @Volatile
    private var chatDestinationSelected = false

    val isChatVisible: Boolean
        get() = activityForeground && chatDestinationSelected

    fun setActivityForeground(foreground: Boolean) {
        activityForeground = foreground
    }

    fun setChatDestinationSelected(selected: Boolean) {
        chatDestinationSelected = selected
    }
}
