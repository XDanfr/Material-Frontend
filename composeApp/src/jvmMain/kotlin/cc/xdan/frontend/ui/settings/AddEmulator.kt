@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.emulators.EmulatorCatalog
import cc.xdan.frontend.emulators.EmulatorDef
import cc.xdan.frontend.emulators.EmulatorDetector
import cc.xdan.frontend.model.CustomSystem
import cc.xdan.frontend.model.EmulatorConfig
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.ui.components.FilePicker
import cc.xdan.frontend.ui.theme.LocalApp
import java.io.File

private enum class AddStep { WHICH, SYSTEMS, CUSTOM, CONFIRM }

/** Pure logic for the wizard (tested): turn the answers into an emulator (+ a custom system if needed). */
object AddEmulatorLogic {
    fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "system" }

    fun parseExtensions(text: String): List<String> =
        text.split(Regex("[\\s,;]+")).map { it.trim().removePrefix("*").removePrefix(".").lowercase() }.filter { it.isNotEmpty() }.distinct()

    fun build(path: String, name: String, args: String, def: EmulatorDef?, systems: List<String>, customName: String?, customExts: List<String>): Pair<EmulatorConfig, CustomSystem?> {
        val custom = if (def == null && systems.isEmpty() && customName != null && customExts.isNotEmpty())
            CustomSystem("custom-${slug(customName)}", customName.trim(), customExts) else null
        val platforms = when {
            custom != null -> listOf(custom.id)
            systems.isNotEmpty() -> systems
            else -> def?.platforms.orEmpty()
        }
        return EmulatorConfig(
            id = (def?.id ?: "custom") + "-" + Integer.toHexString(path.hashCode()),
            defId = def?.id ?: "custom", name = name.ifBlank { def?.name ?: File(path).nameWithoutExtension },
            path = path, args = args.ifBlank { def?.args ?: "\"{rom}\"" }, platforms = platforms,
        ) to custom
    }
}

/**
 * Add an emulator, step by step: pick it → if unrecognised, "what is it?" → if still unknown,
 * "what does it emulate?" → if that's unknown too, the system's name and file extensions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEmulatorDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    var path by remember { mutableStateOf<String?>(null) }
    var def by remember { mutableStateOf<EmulatorDef?>(null) }
    var step by remember { mutableStateOf(AddStep.WHICH) }
    var search by remember { mutableStateOf("") }
    val systems = remember { mutableStateOf(listOf<String>()) }
    var customName by remember { mutableStateOf("") }
    var customExts by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }

    fun choose() {
        FilePicker.pickFile("Choose the emulator")?.let { f ->
            path = f.path
            val known = EmulatorDetector().recognize(f)
            def = known
            if (known != null) { name = known.name; args = known.args; step = AddStep.CONFIRM } else { name = f.nameWithoutExtension; step = AddStep.WHICH }
        } ?: if (path == null) onDismiss() else Unit
    }
    LaunchedEffect(Unit) { if (path == null) choose() }
    val p = path ?: return

    fun save() {
        val (cfg, custom) = AddEmulatorLogic.build(p, name, args, def, systems.value, customName.takeIf { it.isNotBlank() }, AddEmulatorLogic.parseExtensions(customExts))
        app.updateSettings { s -> s.copy(emulators = s.emulators.filterNot { it.path == p } + cfg, customSystems = s.customSystems.filterNot { it.id == custom?.id } + listOfNotNull(custom)) }
        app.toast("Added ${cfg.name}")
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (step) {
                AddStep.WHICH -> "Which emulator is this?"
                AddStep.SYSTEMS -> "What does it emulate?"
                AddStep.CUSTOM -> "Tell us about the system"
                AddStep.CONFIRM -> if (def != null) "Add ${def!!.name}" else "Add emulator"
            })
        },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(File(p).name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (step) {
                    AddStep.WHICH -> {
                        Text("We didn't recognise it. Pick it from the list, or choose “Something else”.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(search, { search = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val list = EmulatorCatalog.all.filter { search.isBlank() || it.name.contains(search, true) }
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(list, key = { it.id }) { d ->
                                Text(d.name + "  ·  " + d.platforms.joinToString { if (it == "*") "All" else Platforms.get(it).shortName },
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                        def = d; name = d.name; args = d.args; step = AddStep.CONFIRM
                                    }.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { def = null; step = AddStep.SYSTEMS }) { Text("Something else") }
                    }
                    AddStep.SYSTEMS -> {
                        Text("Pick every system it runs. If it isn't listed, choose “Not listed”.", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(Modifier.heightIn(max = 320.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Platforms.portTargets.forEach { pl ->
                                val on = pl.id in systems.value
                                FilterChip(on, { systems.value = if (on) systems.value - pl.id else systems.value + pl.id }, label = { Text(pl.shortName) })
                            }
                        }
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { systems.value = emptyList(); step = AddStep.CUSTOM }) { Text("Not listed") }
                    }
                    AddStep.CUSTOM -> {
                        OutlinedTextField(customName, { customName = it }, label = { Text("System or device name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(customExts, { customExts = it }, label = { Text("File extensions") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Separated by commas, e.g. pico8, p8.png") })
                    }
                    AddStep.CONFIRM -> {
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(args, { args = it }, label = { Text("Arguments") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Tokens: {rom} {romdir} {romname} {core}") })
                        val sys = when {
                            customName.isNotBlank() -> customName
                            systems.value.isNotEmpty() -> systems.value.joinToString { Platforms.get(it).shortName }
                            else -> def?.platforms?.joinToString { if (it == "*") "All systems" else Platforms.get(it).shortName } ?: ""
                        }
                        Text("Runs: $sys", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (step) {
                    AddStep.SYSTEMS -> Button(shapes = ButtonDefaults.shapes(), enabled = systems.value.isNotEmpty(), onClick = { args = "\"{rom}\""; step = AddStep.CONFIRM }) { Text("Next") }
                    AddStep.CUSTOM -> Button(shapes = ButtonDefaults.shapes(), enabled = customName.isNotBlank() && AddEmulatorLogic.parseExtensions(customExts).isNotEmpty(),
                        onClick = { args = "\"{rom}\""; step = AddStep.CONFIRM }) { Text("Next") }
                    AddStep.CONFIRM -> Button(shapes = ButtonDefaults.shapes(), enabled = name.isNotBlank(), onClick = ::save) { Text("Add") }
                    AddStep.WHICH -> {}
                }
            }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}
