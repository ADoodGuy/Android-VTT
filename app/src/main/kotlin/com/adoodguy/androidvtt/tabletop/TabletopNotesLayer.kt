package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun BoxScope.TabletopNotesLayer(state: TabletopState) {
    state.notes.forEach { note ->
        TabletopNoteCard(state = state, note = note)
    }
}

@Composable
private fun BoxScope.TabletopNoteCard(
    state: TabletopState,
    note: TabletopNote,
) {
    val density = LocalDensity.current
    val screenAnchor = state.worldToScreen(note.position)
    val cardWidth = noteCardWidth(note.text)
    val cardWidthPx = with(density) { cardWidth.toPx() }
    val editable = WorkspaceModeStore.mode == TabletopMode.TOOLS &&
        state.tool == TabletopTool.NOTES

    Card(
        modifier = Modifier
            .zIndex(20f)
            .noteOffsetInPixels(
                x = screenAnchor.x - cardWidthPx / 2f,
                y = screenAnchor.y,
            )
            .width(cardWidth),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF4B8),
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(
                        note.id,
                        editable,
                        state.pixelsPerWorldUnit,
                        state.snapEnabled,
                    ) {
                        if (!editable) return@pointerInput
                        detectDragGestures(
                            onDragEnd = { state.finishNoteMove(note.id) },
                            onDragCancel = { state.finishNoteMove(note.id) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                state.moveNoteByScreenDelta(note.id, dragAmount)
                            },
                        )
                    }
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Note ${note.id}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (editable) {
                    TextButton(onClick = { state.deleteNote(note.id) }) {
                        Text("Delete")
                    }
                }
            }

            if (editable) {
                BasicTextField(
                    value = note.text,
                    onValueChange = { state.updateNoteText(note.id, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (note.text.isEmpty()) {
                                Text(
                                    text = "Type note…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                Text(
                    text = note.text.ifBlank { "Empty note" },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun noteCardWidth(text: String): Dp {
    val longestLine = text.lineSequence().maxOfOrNull { it.length } ?: 0
    val characters = longestLine.coerceIn(0, 24)
    return (140 + characters * 6).coerceAtMost(284).dp
}

private fun Modifier.noteOffsetInPixels(x: Float, y: Float): Modifier =
    this.then(
        Modifier.offset {
            IntOffset(x.roundToInt(), y.roundToInt())
        },
    )
