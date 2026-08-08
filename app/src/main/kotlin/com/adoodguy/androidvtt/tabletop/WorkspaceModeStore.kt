package com.adoodguy.androidvtt.tabletop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Top-level interaction mode. This is intentionally separate from the utility tool
 * selected inside Tools mode.
 */
object WorkspaceModeStore {
    var mode by mutableStateOf(TabletopMode.TOKENS)
        private set

    fun select(mode: TabletopMode, state: TabletopState) {
        this.mode = mode
        when (mode) {
            TabletopMode.TOKENS -> {
                TabletopMapStore.clearSelection()
            }

            TabletopMode.MAPS -> {
                state.clearTokenSelection()
            }

            TabletopMode.TOOLS -> {
                state.clearTokenSelection()
                TabletopMapStore.clearSelection()
            }
        }
    }
}
