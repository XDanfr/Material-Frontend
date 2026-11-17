package app.materialfrontend.ui.settings

import androidx.compose.foundation.layout.height
import app.materialfrontend.ui.brand.Wordmark
import app.materialfrontend.ui.brand.BrandArtwork
import app.materialfrontend.ui.brand.BrandLoader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.materialfrontend.data.AppPaths
import app.materialfrontend.model.ThemeMode
import app.materialfrontend.model.UiMode
import app.materialfrontend.model.VideoFilter
import app.materialfrontend.ui.theme.LocalApp

enum class SettingsSection(val label: String, val icon: ImageVector) {
    GENERAL("Appearance", Icons.Outlined.Palette),
    LIBRARY("Library", Icons.Outlined.FolderOpen),
    EMULATORS("Emulators", Icons.Outlined.VideogameAsset),
    CORES("Cores", Icons.Outlined.Extension),
    PLAYBACK("In-game", Icons.Outlined.Tv),
    CONTROLLERS("Controllers", Icons.Outlined.Gamepad),
    SCRAPING("Scraping", Icons.Outlined.ImageSearch),
    BIOS("BIOS", Icons.Outlined.Memory),
    ABOUT("About", Icons.Outlined.SettingsApplications),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, initial: SettingsSection = SettingsSection.GENERAL) {
    var section by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(260.dp).fillMaxHeight()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                    Text("Settings", style = MaterialTheme.typography.titleLargeEmphasized)
                }
                SettingsSection.entries.forEach { s ->
                    NavigationDrawerItem(label = { Text(s.label) }, icon = { Icon(s.icon, null) }, selected = s == section, onClick = { section = s })
                }
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(section, transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            modifier = Modifier.weight(1f).fillMaxHeight()) { s ->
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
                Column(Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                    Text(s.label, style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.padding(bottom = 16.dp))
                    when (s) {
                        SettingsSection.GENERAL -> GeneralPanel()
                        SettingsSection.LIBRARY -> RomFoldersPanel()
                        SettingsSection.EMULATORS -> EmulatorsPanel()
                        SettingsSection.CORES -> CoresPanel()
                        SettingsSection.PLAYBACK -> PlaybackPanel()
                        SettingsSection.CONTROLLERS -> ControllersPanel()
                        SettingsSection.SCRAPING -> ScraperPanel()
                        SettingsSection.BIOS -> BiosPanel()
                        SettingsSection.ABOUT -> AboutPanel()
                    }
                }
            }
        }
    }
}

private val seedChoices = listOf(0xFF6750A4, 0xFF006A6A, 0xFF8A4FFF, 0xFFB3261E, 0xFF386A20, 0xFFE8A33D, 0xFF0061A4, 0xFFC2185B)

@Composable
fun GeneralPanel() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Theme")
        SingleChoiceSegmentedButtonRow {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(settings.themeMode == m, { app.updateSettings { it.copy(themeMode = m) } }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() }.replace("Amoled", "AMOLED"))
                }
            }
        }
        SectionTitle("Accent colour")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            seedChoices.forEach { c ->
                val selected = settings.seedColor == c
                Box(Modifier.size(if (selected) 44.dp else 36.dp).clip(CircleShape).background(Color(c)).clickable { app.updateSettings { it.copy(seedColor = c) } })
            }
        }
        SwitchRow("Colour from artwork", "Tint the interface from the focused game's box art", settings.colorFromArtwork) { v -> app.updateSettings { it.copy(colorFromArtwork = v) } }
        SwitchRow("Reduce motion", "Use the standard (non-bouncy) motion scheme", settings.reduceMotion) { v -> app.updateSettings { it.copy(reduceMotion = v) } }
        SwitchRow("Launch animation", "Play the logo animation when the app opens (skipped with reduced motion)", settings.launchAnimation) { v -> app.updateSettings { it.copy(launchAnimation = v) } }
        SectionTitle("Start-up")
        SingleChoiceSegmentedButtonRow {
            UiMode.entries.forEachIndexed { i, m ->
                SegmentedButton(settings.defaultMode == m, { app.updateSettings { it.copy(defaultMode = m) } }, SegmentedButtonDefaults.itemShape(i, UiMode.entries.size)) {
                    Text(when (m) { UiMode.LIST -> "Library"; UiMode.BIGSCREEN -> "Big Screen"; UiMode.XMB -> "XMB" })
                }
            }
        }
        SwitchRow("Start in fullscreen", null, settings.startFullscreen) { v -> app.updateSettings { it.copy(startFullscreen = v) } }
        SwitchRow("Minimise while an external emulator runs", null, settings.minimizeOnExternalLaunch) { v -> app.updateSettings { it.copy(minimizeOnExternalLaunch = v) } }
    }
}

@Composable
fun PlaybackPanel() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Video")
        SingleChoiceSegmentedButtonRow {
            VideoFilter.entries.forEachIndexed { i, f ->
                SegmentedButton(settings.videoFilter == f, { app.updateSettings { it.copy(videoFilter = f) } }, SegmentedButtonDefaults.itemShape(i, VideoFilter.entries.size)) { Text(f.label) }
            }
        }
        SwitchRow("Integer scaling", "Pixel-perfect sizes; may leave borders", settings.integerScale) { v -> app.updateSettings { it.copy(integerScale = v) } }
        SectionTitle("States")
        SwitchRow("Rewind", "Keeps a short history in memory (cores with small save states)", settings.rewind) { v -> app.updateSettings { it.copy(rewind = v) } }
        SwitchRow("Auto-save state on exit", "Resume exactly where you left off", settings.autoSaveState) { v -> app.updateSettings { it.copy(autoSaveState = v) } }
    }
}

@Composable
fun AboutPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrandArtwork(Wordmark.STACKED, Modifier.height(200.dp))
        Text("Version 0.1 (work in progress)", style = MaterialTheme.typography.titleMediumEmphasized)
        Text("Set in Google Sans Flex (SIL Open Font License). Icons built from Material 3 Expressive shapes.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Data folder: ${AppPaths.root}", style = MaterialTheme.typography.bodyMedium)
        Text("Cores: ${AppPaths.cores}", style = MaterialTheme.typography.bodyMedium)
        Text("Saves: ${AppPaths.saves} · States: ${AppPaths.states}", style = MaterialTheme.typography.bodyMedium)
        Text("Runtime: Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
