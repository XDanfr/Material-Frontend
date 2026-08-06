package app.materialfrontend.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.materialfrontend.data.Ports
import app.materialfrontend.model.Game
import app.materialfrontend.model.Platforms
import app.materialfrontend.ui.components.CoverArt
import app.materialfrontend.ui.components.FilePicker
import app.materialfrontend.ui.settings.PlatformPicker
import app.materialfrontend.ui.theme.LocalApp
import java.io.File

/** Add a recompilation / decompilation / source port. Name and system are guessed but editable. */
@Composable
fun AddPortDialog(onDismiss: () -> Unit, onAdded: (Game) -> Unit) {
    val app = LocalApp.current
    var exe by remember { mutableStateOf<File?>(null) }
    var title by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf<String?>(null) }
    var args by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Port") }

    fun choose() {
        FilePicker.pickFile("Choose the port's executable")?.let { f ->
            exe = f
            val g = Ports.guess(f)
            title = g.title; platform = g.platformId; kind = g.kind
        }
    }
    LaunchedEffect(Unit) { if (exe == null) choose() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add port or recomp") },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recompilations, decompilations and source ports run natively. Pick the executable — we'll guess the game and its original system.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exe?.path ?: "No file chosen", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = ::choose) { Text("Browse") }
                }
                if (exe != null) AssistChip(onClick = {}, label = { Text("Detected: $kind" + (platform?.let { " of a ${Platforms.get(it).shortName} game" } ?: "")) })
                OutlinedTextField(title, { title = it }, label = { Text("Game title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Used for artwork lookup — use the original game's name") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Original system", style = MaterialTheme.typography.bodyLarge)
                    PlatformPicker(platform, { platform = it }, allowAuto = false, label = "Choose…")
                }
                OutlinedTextField(args, { args = it }, label = { Text("Launch arguments (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = exe != null && title.isNotBlank() && platform != null, onClick = {
                onAdded(app.addPort(exe!!, title.trim(), platform!!, args))
            }) { Text("Add to library") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Ctrl+K quick launcher: type, arrows, Enter selects, Shift+Enter plays. */
@Composable
fun CommandPalette(games: List<Game>, onDismiss: () -> Unit, onPick: (Game, Boolean) -> Unit) {
    var q by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    val results = remember(q, games) {
        (if (q.isBlank()) games.sortedByDescending { it.lastPlayed ?: 0 } else LibraryQuery.filter(games, Collection.All, q)).take(50)
    }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(index) { if (results.isNotEmpty()) listState.animateScrollToItem(index.coerceIn(0, results.lastIndex)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(640.dp)) {
            Column(Modifier.padding(12.dp)) {
                TextField(q, { q = it; index = 0 }, Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { index = (index + 1).coerceAtMost(results.lastIndex); true }
                        Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                        Key.Enter -> { results.getOrNull(index)?.let { onPick(it, e.isShiftPressed) }; true }
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                }, placeholder = { Text("Jump to a game…   Enter = open · Shift+Enter = play") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp), state = listState) {
                    itemsIndexed(results, key = { _, g -> g.id }) { i, g ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (i == index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { onPick(g, false) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CoverArt(g, Modifier.size(32.dp, 44.dp).clip(RoundedCornerShape(6.dp)), maxDim = 96)
                            Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(Platforms.get(g.platformId).shortName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
