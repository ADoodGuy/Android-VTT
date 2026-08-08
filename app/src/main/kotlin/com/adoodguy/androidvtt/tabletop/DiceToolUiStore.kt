package com.adoodguy.androidvtt.tabletop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Keeps the app-level dice panel coordinated with the scene-local Tools selection. */
object DiceToolUiStore {
    var active by mutableStateOf(false)
        private set

    fun syncActive(isActive: Boolean) {
        if (isActive == active) return
        active = isActive
        if (isActive) {
            DiceRollerStore.openPanel()
        } else {
            DiceRollerStore.closePanel()
        }
    }
}
