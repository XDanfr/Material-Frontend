@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.library

import cc.xdan.frontend.emulators.launchLabel
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import cc.xdan.frontend.ui.components.pressBounce
import cc.xdan.frontend.ui.theme.FlexRecipes
import cc.xdan.frontend.ui.theme.reactive
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.ui.components.CoverArt
import cc.xdan.frontend.ui.components.MorphShape
import cc.xdan.frontend.ui.components.formatAgo
import cc.xdan.frontend.ui.components.formatPlayTime
import cc.xdan.frontend.ui.components.rememberImage
import cc.xdan.frontend.ui.components.rememberMorph
import cc.xdan.frontend.ui.theme.LocalApp
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun DetailPane(game: Game, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val options = remember(game, settings) { Launchers.options(game, settings) }
    val current = remember(game, settings) { Launchers.resolve(game, settings) }
    var menuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val hero by rememberImage(game.assets.hero ?: game.assets.screenshot, 1280)
    val logo by rememberImage(game.assets.logo, 600)
    val platform = Platforms.get(game.platformId)

    Surface(modifier.padding(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Hero with logo overlay
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 16.dp, bottomEnd = 16.dp))) {
                val h = hero
                if (h != null) Image(h, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CoverArt(game, Modifier.fillMaxSize(), preferWide = true)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to MaterialTheme.colorScheme.surfaceContainerLow)))
                logo?.let { Image(it, game.title, Modifier.align(Alignment.BottomStart).padding(16.dp).heightIn(max = 72.dp).widthIn(max = 260.dp), contentScale = ContentScale.Fit) }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Outlined.Close, "Close", Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(6.dp))
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(game.title, style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(listOfNotNull(platform.name, game.meta.releaseYear?.toString(), game.meta.developer).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Play split button: primary action + "launch with" menu.
                    Box {
                        SplitButtonLayout(
                            leadingButton = {
                                val playInteraction = remember { MutableInteractionSource() }
                                val playHovered by playInteraction.collectIsHoveredAsState()
                                val playPressed by playInteraction.collectIsPressedAsState()
                                SplitButtonDefaults.LeadingButton(onClick = { app.play(game) }, interactionSource = playInteraction) {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(SplitButtonDefaults.LeadingIconSize)); Spacer(Modifier.width(8.dp))
                                    Text(if (game.installed) "Play" else "Install", style = MaterialTheme.typography.labelLarge.reactive(playHovered || playPressed) { if (playPressed) FlexRecipes.press(this) else copy(wdth = 118f, wght = 700f, rond = 100f) })
                                }
                            },
                            trailingButton = {
                                SplitButtonDefaults.TrailingButton(checked = menuOpen, onCheckedChange = { menuOpen = it }, enabled = options.size > 1) {
                                    val r by animateFloatAsState(if (menuOpen) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
                                    Icon(Icons.Outlined.KeyboardArrowDown, "Launch with", Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = r })
                                }
                            },
                        )
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            Text("Launch with", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            options.forEach { (key, target) ->
                                DropdownMenuItem(
                                    text = { Text(target.label) },
                                    leadingIcon = { if (target == current) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) },
                                    onClick = { app.setLauncher(game.id, key); menuOpen = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Make default for ${platform.shortName}") }, onClick = {
                                options.firstOrNull { it.second == current }?.let { (k, _) -> app.updateSettings { s -> s.copy(defaultLauncher = s.defaultLauncher + (game.platformId to k)) } }
                                menuOpen = false
                            })
                        }
                    }
                    FavoriteToggle(game.favorite) { app.toggleFavorite(game.id) }
                    Box {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { moreOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(moreOpen, { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { renaming = true; moreOpen = false })
                            DropdownMenuItem(text = { Text("Re-fetch artwork") }, onClick = { app.scraper.scrape(listOf(game), force = true); moreOpen = false })
                            if (File(game.path).exists()) DropdownMenuItem(text = { Text("Show in folder") }, onClick = { Launchers.reveal(File(game.path)); moreOpen = false })
                            DropdownMenuItem(text = { Text(if (game.hidden) "Unhide" else "Hide") }, onClick = { app.setHidden(setOf(game.id), !game.hidden); moreOpen = false })
                            DropdownMenuItem(text = { Text("Remove from library") }, onClick = { app.removeGames(setOf(game.id)); moreOpen = false; onClose() })
                        }
                    }
                }
                Text(launchLabel(current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (game.playTimeSeconds > 0) AssistChip(onClick = {}, label = { Text(formatPlayTime(game.playTimeSeconds)) })
                    AssistChip(onClick = {}, label = { Text(game.lastPlayed?.let { "Last played " + formatAgo(it).replaceFirstChar { c -> c.lowercase() } } ?: "Never played") })
                    game.meta.genres.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    game.meta.players?.let { AssistChip(onClick = {}, label = { Text("$it players") }) }
                }
                cc.xdan.frontend.ui.components.AchievementsStrip(game, big = false)
                game.meta.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                game.meta.publisher?.let { Text("Publisher: $it", style = MaterialTheme.typography.bodySmall) }
                Text(game.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (renaming) {
        var t by remember { mutableStateOf(game.title) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Rename") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { Button(shapes = ButtonDefaults.shapes(), onClick = { app.rename(game.id, t.trim()); renaming = false }, enabled = t.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { renaming = false }) { Text("Cancel") } })
    }
}

/** Favourite toggle whose container morphs circle → cookie on select (one of the few "hero" micro-interactions). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteToggle(checked: Boolean, onToggle: () -> Unit) {
    val morph = rememberMorph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided)
    val p by animateFloatAsState(if (checked) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    val bg = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val interaction = remember { MutableInteractionSource() }
    IconToggleButton(checked, { onToggle() }, Modifier.size(48.dp).pressBounce(interaction = interaction, depth = 0.85f).graphicsLayer { rotationZ = p * 40f }.clip(MorphShape(morph, p)).background(bg), interactionSource = interaction) {
        Icon(if (checked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favourite", Modifier.graphicsLayer { rotationZ = -p * 40f },
            tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
