@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.AppController
import cc.xdan.frontend.input.Nav

/**
 * "Found on your machine" after a rescan. One item → yes/no; several → a checklist.
 * Works with mouse, keyboard and controller (navigation is captured while it's open).
 */
@Composable
fun DiscoveryDialog(app: AppController, items: List<AppController.Discovered>) {
    val single = items.size == 1
    var picked by remember(items) { mutableStateOf(items.map { it.key }.toSet()) }
    // Rows (checklist only), then the two buttons.
    val rows = if (single) 0 else items.size
    var focus by remember(items) { mutableIntStateOf(if (single) 0 else rows) }
    fun accept() = app.resolveDiscoveries(if (single) setOf(items[0].key) else picked)
    fun decline() = app.resolveDiscoveries(emptySet())
    fun toggle(k: String) { picked = if (k in picked) picked - k else picked + k }

    DisposableEffect(Unit) { app.controllers.modal = true; onDispose { app.controllers.modal = false } }
    LaunchedEffect(items) {
        app.controllers.modalEvents.collect { e ->
            when (e.nav) {
                Nav.UP -> focus = (focus - 1).coerceAtLeast(0)
                Nav.DOWN -> focus = (focus + 1).coerceAtMost(rows + 1)
                Nav.LEFT -> if (focus == rows + 1) focus = rows
                Nav.RIGHT -> if (focus == rows) focus = rows + 1
                Nav.CONFIRM -> when {
                    focus < rows -> toggle(items[focus].key)
                    focus == rows -> accept()
                    else -> decline()
                }
                Nav.BACK -> decline()
                else -> {}
            }
        }
    }

    val emulators = items.count { it.emulator != null }; val launchers = items.size - emulators
    val what = listOfNotNull(
        emulators.takeIf { it > 0 }?.let { if (it == 1) "1 emulator" else "$it emulators" },
        launchers.takeIf { it > 0 }?.let { if (it == 1) "1 launcher" else "$it launchers" },
    ).joinToString(" and ")
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = 0.4f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Surface(Modifier.width(480.dp), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (single) "${items[0].name} was found" else "$what found", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(if (single) "It's installed on this machine. Add it to Material Frontend?" else "They're installed on this machine. Which would you like to add?",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (!single) Column {
                    items.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (focus == i) cs.secondaryContainer else cs.surfaceContainerHigh)
                            .clickable { focus = i; toggle(d.key) }.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(d.key in picked, { toggle(d.key) })
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                                Text(d.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(::decline, shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.focusEmphasis(focus == rows + 1, 20.dp, 20.dp, 1f)) { Text(if (single) "No" else "Not now") }
                    Button(::accept, shapes = ButtonDefaults.shapes(), enabled = single || picked.isNotEmpty(),
                        modifier = Modifier.focusEmphasis(focus == rows, 20.dp, 20.dp, 1f)) { Text(if (single) "Yes, add it" else "Add selected") }
                }
            }
        }
    }
}
