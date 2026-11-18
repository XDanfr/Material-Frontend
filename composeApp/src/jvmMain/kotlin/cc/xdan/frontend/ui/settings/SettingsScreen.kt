@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.brand.BrandArtwork
import cc.xdan.frontend.ui.brand.Wordmark
import com.materialkolor.dynamicColorScheme

enum class SettingsSection(val label: String, val icon: ImageVector) {
    GENERAL("General", Icons.Outlined.Palette),
    THEME("Theme", Icons.Outlined.Tune),
    LIBRARY("Library", Icons.Outlined.FolderOpen),
    EMULATORS("Emulators", Icons.Outlined.VideogameAsset),
    CORES("Cores", Icons.Outlined.Extension),
    PLAYBACK("In-game", Icons.Outlined.Tv),
    CONTROLLERS("Controllers", Icons.Outlined.Gamepad),
    SCRAPING("Scraping", Icons.Outlined.ImageSearch),
    BIOS("BIOS", Icons.Outlined.Memory),
    ABOUT("About", Icons.Outlined.SettingsApplications);

    /** The per-theme section is named after the theme it belongs to. */
    fun labelFor(mode: UiMode) = if (this != THEME) label else when (mode) {
        UiMode.LIST -> "Library view"; UiMode.BIGSCREEN -> "Big Screen"; UiMode.XMB -> "XMB"
    }
}

/** Settings for the Library view: the shared settings list, rendered for mouse and keyboard. */
@Composable
fun SettingsScreen(onBack: () -> Unit, initial: SettingsSection = SettingsSection.GENERAL) {
    val groups = settingsGroups(UiMode.LIST)
    var section by remember { mutableStateOf(initial) }
    if (groups.none { it.section == section }) section = SettingsSection.GENERAL
    // Own surface so text colours are right wherever Settings is shown.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(260.dp).fillMaxHeight()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                        Text("Settings", style = MaterialTheme.typography.titleLargeEmphasized)
                    }
                    groups.forEach { g ->
                        NavigationDrawerItem(label = { Text(g.section.labelFor(UiMode.LIST)) }, icon = { Icon(g.section.icon, null) },
                            selected = g.section == section, onClick = { section = g.section })
                    }
                }
            }
            val motion = MaterialTheme.motionScheme
            AnimatedContent(section, transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                modifier = Modifier.weight(1f).fillMaxHeight()) { sec ->
                val group = groups.firstOrNull { it.section == sec } ?: return@AnimatedContent
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
                    Column(Modifier.widthIn(max = 860.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sec.labelFor(UiMode.LIST), style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.padding(bottom = 12.dp))
                        if (sec == SettingsSection.ABOUT) {
                            BrandArtwork(Wordmark.STACKED, Modifier.height(180.dp))
                            Spacer(Modifier.height(8.dp))
                        }
                        group.items.forEach { SettingRow(it) }
                        if (sec == SettingsSection.ABOUT) {
                            Spacer(Modifier.height(12.dp))
                            Text("Data folder: ${AppPaths.root}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Runtime: Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Set in Google Sans Flex (SIL Open Font License). Icons built from Material 3 Expressive shapes.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** One setting, mouse/keyboard style. */
@Composable
fun SettingRow(item: SettingItem) {
    when (item) {
        is ToggleSetting -> ListItem(
            headlineContent = { Text(item.title) }, supportingContent = item.subtitle?.let { { Text(it) } },
            trailingContent = { Switch(item.value, item.onChange) }, colors = rowColors(),
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { item.onChange(!item.value) },
        )
        is ChoiceSetting -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when {
                item.style == ChoiceStyle.ACCENT -> AccentChips(item)
                item.labels.size <= 4 -> SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    item.labels.forEachIndexed { i, l ->
                        SegmentedButton(i == item.selected, { item.onSelect(i) }, SegmentedButtonDefaults.itemShape(i, item.labels.size)) {
                            // One line, never wrapped: segments share the full row width.
                            Text(l, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                else -> ChoiceDropdown(item)
            }
        }
        is ActionSetting -> ActionRow(item)
        is InfoSetting -> ListItem(headlineContent = { Text(item.title) }, supportingContent = item.subtitle?.let { { Text(it) } }, colors = rowColors(),
            modifier = Modifier.clip(RoundedCornerShape(16.dp)))
        is PanelSetting -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
            item.panel()
        }
    }
}

@Composable
private fun ChoiceDropdown(item: ChoiceSetting) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { open = true }) {
            Text(item.selectedLabel); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.KeyboardArrowDown, null)
        }
        DropdownMenu(open, { open = false }) {
            item.labels.forEachIndexed { i, l ->
                DropdownMenuItem(text = { Text(l) }, onClick = { item.onSelect(i); open = false },
                    leadingIcon = { if (i == item.selected) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) })
            }
        }
    }
}

@Composable
private fun ActionRow(item: ActionSetting) {
    var confirming by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.title) }, supportingContent = item.subtitle?.let { { Text(it) } }, colors = rowColors(),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        trailingContent = {
            if (item.destructive) OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { confirming = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(item.button) }
            else FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = item.run) { Text(item.button) }
        },
    )
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("${item.title}?") },
        text = { Text(item.confirm ?: item.subtitle ?: "") },
        confirmButton = {
            Button(shapes = ButtonDefaults.shapes(), onClick = { confirming = false; item.run() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text(item.title) }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirming = false }) { Text("Cancel") } },
    )
}

/**
 * Pixel-style colour chips: each shows the scheme a seed produces — primary on the top half,
 * secondary bottom-left, tertiary bottom-right — for the current light/dark mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentChips(item: ChoiceSetting, chip: androidx.compose.ui.unit.Dp = 64.dp) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item.values.forEachIndexed { i, seed -> AccentChip(seed, dark, i == item.selected, item.labels[i], chip) { item.onSelect(i) } }
    }
}

@Composable
fun AccentChip(seed: Long, dark: Boolean, selected: Boolean, name: String, size: androidx.compose.ui.unit.Dp = 64.dp, onClick: () -> Unit) {
    val scheme = remember(seed, dark) { dynamicColorScheme(seedColor = Color(seed), isDark = dark, isAmoled = false) }
    val cs = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 20.dp else size / 2, MaterialTheme.motionScheme.fastSpatialSpec())
    val bg by animateColorAsState(if (selected) cs.secondaryContainer else cs.surfaceContainerHigh, MaterialTheme.motionScheme.fastEffectsSpec())
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(bg)
            .then(if (selected) Modifier.border(2.dp, cs.primary, RoundedCornerShape(corner)) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.66f)) {
            val d = Size(this.size.minDimension, this.size.minDimension)
            drawArc(scheme.primary, 180f, 180f, useCenter = true, size = d)       // top half
            drawArc(scheme.tertiary, 0f, 90f, useCenter = true, size = d)         // bottom-right quarter
            drawArc(scheme.secondary, 90f, 90f, useCenter = true, size = d)       // bottom-left quarter
        }
        if (selected) Box(Modifier.align(Alignment.Center).size(size * 0.3f).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, Modifier.size(size * 0.2f), tint = cs.onPrimaryContainer)
        }
    }
}
