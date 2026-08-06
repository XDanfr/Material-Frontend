package app.materialfrontend.ui.settings

import app.materialfrontend.ui.brand.CartridgeLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.materialfrontend.data.AppPaths
import app.materialfrontend.emulators.BiosCatalog
import app.materialfrontend.emulators.CoreCatalog
import app.materialfrontend.emulators.CoreDef
import app.materialfrontend.emulators.DetectedEmulator
import app.materialfrontend.emulators.EmulatorCatalog
import app.materialfrontend.input.Pad
import app.materialfrontend.model.EmulatorConfig
import app.materialfrontend.model.Platforms
import app.materialfrontend.model.RomFolder
import app.materialfrontend.ui.components.ButtonGlyph
import app.materialfrontend.ui.components.FilePicker
import app.materialfrontend.ui.theme.LocalApp
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 16.dp, bottom = 8.dp))

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** Small anchored menu to pick a platform. */
@Composable
fun PlatformPicker(selected: String?, onPick: (String?) -> Unit, allowAuto: Boolean = true, label: String = "Auto-detect") {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(selected?.let { Platforms.get(it).name } ?: label) }
        DropdownMenu(open, { open = false }) {
            if (allowAuto) DropdownMenuItem(text = { Text(label) }, onClick = { onPick(null); open = false })
            Platforms.portTargets.forEach { p ->
                DropdownMenuItem(text = { Text("${p.name}  ·  ${p.manufacturer}") }, onClick = { onPick(p.id); open = false })
            }
        }
    }
}

// ---------------- ROM folders ----------------

@Composable
fun RomFoldersPanel() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add the folders that hold your games. Sub-folders named after systems (snes, psx, switch…) are detected automatically; " +
            "otherwise pick the system for the folder.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        settings.romFolders.forEachIndexed { i, f ->
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Text(f.path, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PlatformPicker(f.platformOverride, { p -> app.updateSettings { s -> s.copy(romFolders = s.romFolders.toMutableList().also { it[i] = f.copy(platformOverride = p) }) } })
                    IconButton(onClick = { app.updateSettings { s -> s.copy(romFolders = s.romFolders.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Outlined.Delete, "Remove") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                FilePicker.pickFolder("Add game folder")?.let { dir ->
                    app.updateSettings { s -> if (s.romFolders.any { it.path == dir.path }) s else s.copy(romFolders = s.romFolders + RomFolder(dir.path)) }
                }
            }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add folder") }
        }
        SwitchRow("Import Steam library", "Installed Steam games appear with their library art", settings.importSteam) { v -> app.updateSettings { it.copy(importSteam = v) } }
        SwitchRow("Use existing ES-DE / Pegasus metadata", "Reads gamelist.xml and metadata.pegasus.txt next to your games", settings.importGamelists) { v -> app.updateSettings { it.copy(importGamelists = v) } }
    }
}

// ---------------- Emulators ----------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmulatorsPanel(autoScan: Boolean = false) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<EmulatorConfig?>(null) }

    fun scan() {
        scanning = true
        scope.launch {
            val found: List<DetectedEmulator> = app.detectEmulators { progress = it }
            app.updateSettings { s ->
                val existing = s.emulators.map { it.path }.toSet()
                s.copy(emulators = s.emulators + found.filter { it.path !in existing }.map { it.toConfig() })
            }
            scanning = false
            app.toast(if (found.isEmpty()) "No emulators found — add them manually" else "Found ${found.size} emulator(s)")
        }
    }
    LaunchedEffect(autoScan) { if (autoScan && settings.emulators.isEmpty()) scan() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = ::scan, enabled = !scanning) { Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text("Scan for emulators") }
            OutlinedButton(onClick = {
                editing = EmulatorConfig("custom-" + System.currentTimeMillis(), "custom", "Custom emulator", "", "\"{rom}\"", emptyList())
            }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add manually") }
            if (scanning) { CartridgeLoader(Modifier.size(44.dp)); Text(progress, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
        if (settings.emulators.isEmpty() && !scanning) {
            Text("No standalone emulators configured yet. In-app cores still work for retro systems.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        settings.emulators.forEach { e ->
            ListItem(
                leadingContent = { Checkbox(e.enabled, { v -> app.updateSettings { s -> s.copy(emulators = s.emulators.map { if (it.id == e.id) it.copy(enabled = v) else it }) } }) },
                headlineContent = { Text(e.name) },
                supportingContent = { Text("${e.platforms.joinToString { if (it == "*") "All systems" else Platforms.get(it).shortName }}  ·  ${e.flatpakId ?: e.path}", maxLines = 1) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editing = e }) { Icon(Icons.Outlined.Edit, "Edit") }
                        IconButton(onClick = { app.updateSettings { s -> s.copy(emulators = s.emulators.filterNot { it.id == e.id }) } }) { Icon(Icons.Outlined.Delete, "Remove") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }

    editing?.let { e -> EmulatorEditDialog(e, onDismiss = { editing = null }) { updated ->
        app.updateSettings { s -> s.copy(emulators = if (s.emulators.any { it.id == updated.id }) s.emulators.map { if (it.id == updated.id) updated else it } else s.emulators + updated) }
        editing = null
    } }
}

@Composable
fun EmulatorEditDialog(initial: EmulatorConfig, onDismiss: () -> Unit, onSave: (EmulatorConfig) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var path by remember { mutableStateOf(initial.path) }
    var args by remember { mutableStateOf(initial.args) }
    var platforms by remember { mutableStateOf(initial.platforms) }
    val def = EmulatorCatalog.get(initial.defId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.path.isEmpty()) "Add emulator" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(520.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(path, { path = it }, label = { Text("Executable / AppImage / .app") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { FilePicker.pickFile("Choose emulator")?.let { path = it.path } }) { Icon(Icons.Outlined.Folder, "Browse") }
                }
                OutlinedTextField(args, { args = it }, label = { Text("Arguments") }, supportingText = { Text("Tokens: {rom} {romdir} {romname} {core}") }, modifier = Modifier.fillMaxWidth())
                def?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                Text("Systems: " + platforms.joinToString { Platforms.get(it).shortName }.ifEmpty { "none" }, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlatformPicker(null, { p -> if (p != null && p !in platforms) platforms = platforms + p }, allowAuto = false, label = "Add system")
                    if (platforms.isNotEmpty()) TextButton(onClick = { platforms = platforms.dropLast(1) }) { Text("Remove last") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(initial.copy(name = name, path = path, args = args, platforms = platforms)) }, enabled = path.isNotBlank() && platforms.isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- Cores ----------------

/** Recommended: first non-HW core of each platform that has cores. */
fun recommendedCores(): List<CoreDef> = Platforms.all.mapNotNull { p -> p.cores.mapNotNull { CoreCatalog.get(it) }.firstOrNull { !it.hw } }.distinct()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoresPanel(preselectRecommended: Boolean = false) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val recommended = remember { recommendedCores().map { it.id }.toSet() }
    val selected = remember { mutableStateMapOf<String, Boolean>().apply { if (preselectRecommended) recommended.forEach { if (!CoreCatalog.isInstalled(it)) put(it, true) } } }
    val progress = remember { mutableStateMapOf<String, Float>() }
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val supported = CoreCatalog.downloadUrl("mesen") != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cores run games inside Material Frontend, so overlays, save states, rewind and controller menus work everywhere. " +
            "Cores marked “GPU” need hardware rendering and launch through RetroArch until in-app GPU support lands.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!supported) Text("The libretro buildbot has no builds for this OS/architecture; place cores in ${AppPaths.cores} manually.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !busy && selected.values.any { it } && supported, onClick = {
                busy = true
                scope.launch {
                    selected.filterValues { it }.keys.toList().forEach { id ->
                        progress[id] = 0f
                        app.coreInstaller.install(id) { p -> progress[id] = p }
                            .onSuccess { selected.remove(id) }
                            .onFailure { app.toast("${CoreCatalog.get(id)?.name}: ${it.message}") }
                        progress.remove(id)
                        refresh++
                    }
                    busy = false
                    app.toast("Cores installed")
                }
            }) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Install selected (${selected.values.count { it }})") }
            TextButton(onClick = { recommended.forEach { if (!CoreCatalog.isInstalled(it)) selected[it] = true } }) { Text("Select recommended") }
        }
        val _r = refresh
        CoreCatalog.all.filter { it.platforms.isNotEmpty() }.forEach { core ->
            val installed = CoreCatalog.isInstalled(core.id)
            ListItem(
                leadingContent = {
                    if (installed) Icon(Icons.Outlined.CheckCircle, "Installed", tint = MaterialTheme.colorScheme.primary)
                    else Checkbox(selected[core.id] == true, { selected[core.id] = it }, enabled = !busy)
                },
                headlineContent = { Text(core.name + if (core.id in recommended) "  ·  Recommended" else "") },
                supportingContent = {
                    Column {
                        Text(core.platforms.joinToString { Platforms.get(it).name } + (if (core.hw) "  ·  GPU" else "") + "  ·  ~${core.sizeMb} MB")
                        AnimatedVisibility(core.id in progress) { LinearWavyProgressIndicator(progress = { progress[core.id] ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
                    }
                },
                trailingContent = { if (installed) TextButton(onClick = { app.coreInstaller.uninstall(core.id); refresh++ }) { Text("Remove") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}

// ---------------- BIOS ----------------

@Composable
fun BiosPanel() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val dir = File(settings.biosDir ?: AppPaths.system.path)
    var refresh by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BIOS and firmware files are never downloaded. Copy your own dumps into the folder below; they're checked against known-good hashes.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dir.path, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = { FilePicker.pickFolder("BIOS folder")?.let { d -> app.updateSettings { it.copy(biosDir = d.path) } } }) { Text("Change") }
            OutlinedButton(onClick = { refresh++ }) { Text("Re-check") }
        }
        val _r = refresh
        BiosCatalog.all.groupBy { it.platformId }.forEach { (pid, files) ->
            Text(Platforms.get(pid).name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            files.forEach { b ->
                val st = remember(b, dir, refresh) { BiosCatalog.status(dir, b) }
                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (st) {
                        BiosCatalog.Status.OK -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        BiosCatalog.Status.UNKNOWN_VERSION -> Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
                        BiosCatalog.Status.MISSING -> Icon(Icons.Outlined.ErrorOutline, null, tint = if (b.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                    }
                    Text(b.file, style = MaterialTheme.typography.bodyMedium)
                    Text(listOfNotNull(if (b.required) "required" else "optional", b.note.ifBlank { null }, if (st == BiosCatalog.Status.UNKNOWN_VERSION) "unrecognised dump" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------- Scraping ----------------

@Composable
fun ScraperPanel() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val s = settings.scraper
    fun upd(fn: (app.materialfrontend.model.ScraperSettings) -> app.materialfrontend.model.ScraperSettings) = app.updateSettings { it.copy(scraper = fn(it.scraper)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("libretro-thumbnails and Steam work without an account. Add a SteamGridDB key for logos and hero art, and IGDB for descriptions and metadata.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("SteamGridDB")
        OutlinedTextField(s.steamGridDbKey, { v -> upd { it.copy(steamGridDbKey = v.trim()) } }, label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), supportingText = { Text("steamgriddb.com → Preferences → API") })
        SectionTitle("IGDB (Twitch)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(s.igdbClientId, { v -> upd { it.copy(igdbClientId = v.trim()) } }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(s.igdbClientSecret, { v -> upd { it.copy(igdbClientSecret = v.trim()) } }, label = { Text("Client secret") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
        }
        SwitchRow("Download hero art", "Wide banners used in Big Screen and the details pane", s.downloadHeroes) { v -> upd { it.copy(downloadHeroes = v) } }
        SwitchRow("Scrape new games automatically", null, s.autoScrapeNew) { v -> upd { it.copy(autoScrapeNew = v) } }
    }
}

// ---------------- Controllers ----------------

@Composable
fun ControllersPanel() {
    val app = LocalApp.current
    val pads by app.controllers.controllers.collectAsState()
    val settings by app.settings.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!app.controllers.sdlAvailable) Text("SDL couldn't start — only keyboard input is available.", color = MaterialTheme.colorScheme.error)
        if (pads.isEmpty()) Text("No controllers connected. Plug one in or pair it over Bluetooth — it's picked up instantly.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        pads.forEachIndexed { i, p ->
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.Gamepad, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Player ${i + 1} · ${p.typeLabel}", style = MaterialTheme.typography.titleMedium)
                        Text(p.name + (p.battery?.let { " · $it%" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    listOf(Pad.SOUTH, Pad.EAST, Pad.WEST, Pad.NORTH).forEach { ButtonGlyph(it, p.family) }
                }
            }
        }
        SwitchRow("Nintendo-style confirm", "Use the right face button to confirm and the bottom one to go back", settings.nintendoConfirm) { v -> app.updateSettings { it.copy(nintendoConfirm = v) } }
        SwitchRow("Open Big Screen when a controller connects", null, settings.bigScreenOnController) { v -> app.updateSettings { it.copy(bigScreenOnController = v) } }
        Text("In game: Guide (or Select + Start) opens the quick menu · Select + RB toggles fast-forward · hold Select + LB to rewind. " +
            "Keyboard: arrows, Z/X/A/S face buttons, Q/W shoulders, Enter start, Shift select, Esc menu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
