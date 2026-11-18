@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.components.LocalPressPulse
import cc.xdan.frontend.ui.components.pressBounce
import cc.xdan.frontend.ui.theme.LocalApp
import cc.xdan.frontend.ui.theme.reactive

/** A focusable row (section headers are drawn above the first row of each section). */
private class CtlRow(val item: SettingItem, val section: SettingsSection, val firstInSection: Boolean)

/** What a controller press does to a setting. Returns a message to show, if any. */
fun activateSetting(item: SettingItem, nav: Nav, armed: String?, setArmed: (String?) -> Unit, editInLibrary: (SettingsSection) -> Unit, section: SettingsSection) {
    when (item) {
        is ToggleSetting -> if (nav in setOf(Nav.CONFIRM, Nav.LEFT, Nav.RIGHT)) item.onChange(!item.value)
        is ChoiceSetting -> {
            val n = item.labels.size
            when (nav) {
                Nav.LEFT -> item.onSelect((item.selected - 1 + n) % n)
                Nav.RIGHT, Nav.CONFIRM -> item.onSelect((item.selected + 1) % n)
                else -> {}
            }
        }
        is ActionSetting -> if (nav == Nav.CONFIRM) {
            // Destructive actions need a second press while armed.
            if (item.destructive && armed != item.key) setArmed(item.key) else { setArmed(null); item.run() }
        }
        is PanelSetting -> if (nav == Nav.CONFIRM) editInLibrary(section)
        is InfoSetting -> {}
    }
}

/**
 * Settings for Big Screen: the same settings list as everywhere else, as big controller rows.
 * Up/Down move, Left/Right change a value, A toggles/opens/runs. Destructive actions ask for a
 * second press. Desktop-only editors open in the Library view.
 */
@Composable
fun ControllerSettings(mode: UiMode, active: Boolean, modifier: Modifier = Modifier, onExitTop: (() -> Unit)? = null) {
    val app = LocalApp.current
    val groups = settingsGroups(mode)
    val rows = groups.flatMap { g -> g.items.mapIndexed { i, it -> CtlRow(it, g.section, i == 0) } }
    var focus by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf<String?>(null) }
    val latestRows by rememberUpdatedState(rows)
    val list = rememberLazyListState()
    LaunchedEffect(focus) { if (rows.isNotEmpty()) list.animateScrollToItem((focus - 2).coerceAtLeast(0)) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        app.controllers.events.collect { e ->
            val r = latestRows
            when (e.nav) {
                Nav.UP -> { if (focus == 0 && onExitTop != null) onExitTop() else focus = (focus - 1).coerceAtLeast(0); armed = null }
                Nav.DOWN -> { focus = (focus + 1).coerceAtMost(r.lastIndex.coerceAtLeast(0)); armed = null }
                Nav.CONFIRM, Nav.LEFT, Nav.RIGHT -> r.getOrNull(focus)?.let { row ->
                    activateSetting(row.item, e.nav, armed, { armed = it }, { app.editInLibraryView(it) }, row.section)
                }
                else -> {}
            }
        }
    }
    LazyColumn(modifier, state = list, contentPadding = PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(rows, key = { _, r -> r.item.key }) { i, row ->
            Column {
                if (row.firstInSection) Text(row.section.labelFor(mode), style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = if (i == 0) 0.dp else 20.dp, bottom = 6.dp))
                ControllerSettingRow(row.item, focused = i == focus && active, armed = armed == row.item.key) {
                    focus = i
                    activateSetting(row.item, Nav.CONFIRM, armed, { armed = it }, { app.editInLibraryView(it) }, row.section)
                }
            }
        }
    }
}

@Composable
private fun ControllerSettingRow(item: SettingItem, focused: Boolean, armed: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (focused) cs.secondaryContainer else cs.surfaceContainer.copy(alpha = 0.7f), MaterialTheme.motionScheme.fastEffectsSpec())
    val corner by animateDpAsState(if (focused) 18.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    val fg = if (focused) cs.onSecondaryContainer else cs.onSurface
    Row(
        Modifier.widthIn(max = 1100.dp).fillMaxWidth().pressBounce(LocalPressPulse.current, focused, depth = 0.97f)
            .clip(RoundedCornerShape(corner)).background(bg).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, color = fg, style = MaterialTheme.typography.titleLarge.reactive(focused) { copy(wght = 700f, rond = 100f) })
            val sub = when (item) { is PanelSetting -> item.summary + " · edit in the Library view"; else -> item.subtitle }
            sub?.let { Text(it, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        when (item) {
            is ToggleSetting -> Switch(item.value, onCheckedChange = null)
            is ChoiceSetting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.style == ChoiceStyle.ACCENT) {
                    val dark = cs.surface.luminance() < 0.5f
                    AccentChip(item.values[item.selected], dark, selected = false, name = item.selectedLabel, size = 40.dp) {}
                }
                Text("‹  ${item.selectedLabel}  ›", color = fg, style = MaterialTheme.typography.titleMedium.reactive(focused) { copy(wght = 720f, wdth = 108f) })
            }
            is ActionSetting -> Box(Modifier.clip(CircleShape).background(when {
                armed -> cs.error; item.destructive -> cs.errorContainer; else -> cs.primaryContainer
            }).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(if (armed) "Press again to confirm" else item.button, style = MaterialTheme.typography.labelLarge,
                    color = when { armed -> cs.onError; item.destructive -> cs.onErrorContainer; else -> cs.onPrimaryContainer })
            }
            is PanelSetting -> Text("Open ›", color = fg, style = MaterialTheme.typography.titleMedium)
            is InfoSetting -> Box(Modifier.size(1.dp).background(Color.Transparent))
        }
    }
}
