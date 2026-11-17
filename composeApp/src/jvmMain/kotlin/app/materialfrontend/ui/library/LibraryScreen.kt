@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.materialfrontend.ui.library

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import app.materialfrontend.ui.components.pressBounce
import app.materialfrontend.ui.components.platformIcon
import app.materialfrontend.ui.brand.Wordmark
import app.materialfrontend.ui.brand.BrandArtwork
import app.materialfrontend.ui.theme.FlexRecipes
import app.materialfrontend.ui.theme.reactive
import app.materialfrontend.ui.theme.flex
import app.materialfrontend.ui.brand.CartridgeLoader
import app.materialfrontend.ui.brand.BrandLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed as ptrCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as ptrMeta
import androidx.compose.ui.input.pointer.isShiftPressed as ptrShift
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.materialfrontend.model.Game
import app.materialfrontend.model.GameKind
import app.materialfrontend.model.LibraryView
import app.materialfrontend.model.Platforms
import app.materialfrontend.model.SortOrder
import app.materialfrontend.model.UiMode
import app.materialfrontend.ui.components.ArtworkSeedEffect
import app.materialfrontend.ui.components.CoverArt
import app.materialfrontend.ui.components.focusEmphasis
import app.materialfrontend.ui.components.formatAgo
import app.materialfrontend.ui.components.formatPlayTime
import app.materialfrontend.ui.settings.SettingsScreen
import app.materialfrontend.ui.settings.SettingsSection
import app.materialfrontend.ui.theme.LocalApp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val games by app.library.state.collectAsState()
    val settings by app.settings.state.collectAsState()
    val scrape by app.scraper.progress.collectAsState()
    val scan by app.scan.collectAsState()

    var collection by remember { mutableStateOf<Collection>(Collection.All) }
    var search by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val multi = remember { mutableStateListOf<String>() }
    var showSettings by remember { mutableStateOf<SettingsSection?>(null) }
    var showAddPort by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    val visible by remember(games, collection, search, settings.sort, settings.sortDescending) {
        derivedStateOf { LibraryQuery.sort(LibraryQuery.filter(games, collection, search), settings.sort, settings.sortDescending) }
    }
    val selected = games.firstOrNull { it.id == selectedId }
    ArtworkSeedEffect(selected?.assets?.cover)

    showSettings?.let { sec -> SettingsScreen(onBack = { showSettings = null }, initial = sec); return }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val mod = e.isCtrlPressed || e.isMetaPressed
        when {
            mod && e.key == Key.K -> { showPalette = true; true }
            mod && e.key == Key.F -> { searchFocus.requestFocus(); true }
            mod && e.key == Key.Comma -> { showSettings = SettingsSection.GENERAL; true }
            e.key == Key.Escape && multi.isNotEmpty() -> { multi.clear(); true }
            e.key == Key.Escape && selectedId != null -> { selectedId = null; true }
            else -> false
        }
    }) {
        Row(Modifier.fillMaxSize()) {
            // ---------- Sidebar ----------
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(264.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    // Brand mark: hover plays the icon cycle.
                    val logoHover = remember { MutableInteractionSource() }
                    val logoHovered by logoHover.collectIsHoveredAsState()
                    Row(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 16.dp).hoverable(logoHover), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrandLoader(Modifier.size(52.dp), intervalMillis = 900, running = logoHovered)
                        BrandArtwork(Wordmark.TEXT, Modifier.height(22.dp))
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SidebarItem(Collection.All, Icons.Outlined.Apps, games.count { !it.hidden }, collection) { collection = it }
                        SidebarItem(Collection.Recent, Icons.Outlined.History, games.count { it.lastPlayed != null && !it.hidden }, collection) { collection = it }
                        SidebarItem(Collection.Favorites, Icons.Outlined.Favorite, games.count { it.favorite && !it.hidden }, collection) { collection = it }
                        val ports = games.count { it.kind == GameKind.PORT && !it.hidden }
                        SidebarItem(Collection.Ports, Icons.Outlined.Build, ports, collection) { collection = it }
                        Text("Systems", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                        LibraryQuery.platformsIn(games).forEach { (pid, count) ->
                            SidebarItem(Collection.OfPlatform(pid), platformIcon(pid), count, collection) { collection = it }
                        }
                        if (games.any { it.hidden }) SidebarItem(Collection.Hidden, Icons.Outlined.VisibilityOff, games.count { it.hidden }, collection) { collection = it }
                    }
                    // Background work status.
                    AnimatedVisibility(scrape.running || scan.running) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CartridgeLoader(Modifier.size(28.dp))
                                    Text(if (scan.running) "Scanning library" else "Fetching artwork ${scrape.done}/${scrape.total}", style = MaterialTheme.typography.labelLargeEmphasized)
                                }
                                Text((if (scan.running) scan.current else scrape.current).orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                if (scan.running) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                                else LinearWavyProgressIndicator(progress = { if (scrape.total == 0) 0f else scrape.done / scrape.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            // ---------- Main ----------
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Toolbar
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(220.dp)) {
                        Text(collection.label, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${visible.size} games", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        search, { search = it }, Modifier.weight(1f).focusRequester(searchFocus),
                        placeholder = { Text("Search  ·  Ctrl+K", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (search.isNotEmpty()) IconButton(shapes = IconButtonDefaults.shapes(), onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                        singleLine = true, shape = RoundedCornerShape(28.dp),
                    )
                    SortMenu(settings.sort, settings.sortDescending) { o, d -> app.updateSettings { it.copy(sort = o, sortDescending = d) } }
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(settings.libraryView == LibraryView.GRID, { app.updateSettings { it.copy(libraryView = LibraryView.GRID) } }, SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}) { Icon(Icons.Outlined.GridView, "Grid") }
                        SegmentedButton(settings.libraryView == LibraryView.LIST, { app.updateSettings { it.copy(libraryView = LibraryView.LIST) } }, SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {}) { Icon(Icons.AutoMirrored.Outlined.ViewList, "List") }
                    }
                    Tip("Add port / recomp") { IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showAddPort = true }) { Icon(Icons.Outlined.Add, "Add port") } }
                    Tip("Rescan library") { IconButton(shapes = IconButtonDefaults.shapes(), onClick = { app.rescan() }, enabled = !scan.running) { Icon(Icons.Outlined.Refresh, "Rescan") } }
                    Tip("XMB mode") { IconButton(shapes = IconButtonDefaults.shapes(), onClick = { app.mode.value = UiMode.XMB; app.fullscreen.value = true }) { Icon(Icons.Outlined.ViewCarousel, "XMB") } }
                    Tip("Big Screen (F11)") { FilledIconButton(shapes = IconButtonDefaults.shapes(), onClick = { app.mode.value = UiMode.BIGSCREEN; app.fullscreen.value = true }) { Icon(Icons.Outlined.Tv, "Big Screen") } }
                    Tip("Settings (Ctrl+,)") { IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showSettings = SettingsSection.GENERAL }) { Icon(Icons.Outlined.Settings, "Settings") } }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (visible.isEmpty()) EmptyState(games.isEmpty(), search.isNotBlank(), onSettings = { showSettings = SettingsSection.LIBRARY }, onAddPort = { showAddPort = true })
                    else if (settings.libraryView == LibraryView.GRID) {
                        LazyVerticalGrid(GridCells.Adaptive(168.dp), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            items(visible, key = { it.id }) { g ->
                                GameCard(g, selected = g.id == selectedId || g.id in multi,
                                    onClick = { mods -> handleSelect(g, mods, visible, multi, selectedId) { selectedId = it } },
                                    onDoubleClick = { app.play(g) }, modifier = Modifier.animateItem())
                            }
                        }
                    } else {
                        GameTable(visible, selectedId, multi, onSelect = { g, mods -> handleSelect(g, mods, visible, multi, selectedId) { selectedId = it } }, onPlay = { app.play(it) })
                    }

                    // Multi-select floating toolbar
                    androidx.compose.animation.AnimatedVisibility(multi.isNotEmpty(), Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
                        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut()) {
                        HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                            Text("${multi.size} selected", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { multi.forEach { app.toggleFavorite(it) } }) { Icon(Icons.Outlined.FavoriteBorder, "Favourite") }
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { app.scraper.scrape(games.filter { it.id in multi }, force = true) }) { Icon(Icons.Outlined.ImageSearch, "Re-scrape") }
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { app.setHidden(multi.toSet(), collection != Collection.Hidden); multi.clear() }) { Icon(Icons.Outlined.VisibilityOff, "Hide") }
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { app.removeGames(multi.toSet()); multi.clear() }) { Icon(Icons.Outlined.Delete, "Remove") }
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { multi.clear() }) { Icon(Icons.Outlined.Close, "Clear selection") }
                        }
                    }
                }
            }

            // ---------- Details ----------
            AnimatedVisibility(selected != null,
                enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
                val g = selected ?: return@AnimatedVisibility
                DetailPane(g, onClose = { selectedId = null }, modifier = Modifier.width(420.dp).fillMaxHeight())
            }
        }
    }

    if (showAddPort) AddPortDialog(onDismiss = { showAddPort = false }) { g -> showAddPort = false; selectedId = g.id; collection = Collection.Ports }
    if (showPalette) CommandPalette(games.filter { !it.hidden }, onDismiss = { showPalette = false }) { g, play -> showPalette = false; selectedId = g.id; if (play) app.play(g) }
}

private fun handleSelect(g: Game, mods: PointerKeyboardModifiers?, visible: List<Game>, multi: MutableList<String>, current: String?, setSelected: (String?) -> Unit) {
    val ctrl = mods?.let { it.ptrCtrl || it.ptrMeta } == true
    val shift = mods?.ptrShift == true
    when {
        ctrl -> { if (g.id in multi) multi.remove(g.id) else multi.add(g.id); current?.let { if (it !in multi) multi.add(it) } }
        shift && current != null -> {
            val a = visible.indexOfFirst { it.id == current }; val b = visible.indexOf(g)
            if (a >= 0 && b >= 0) { multi.clear(); multi.addAll(visible.subList(minOf(a, b), maxOf(a, b) + 1).map { it.id }) }
        }
        else -> { multi.clear(); setSelected(g.id) }
    }
}

@Composable
private fun SidebarItem(c: Collection, icon: ImageVector, count: Int, current: Collection, onClick: (Collection) -> Unit) {
    NavigationDrawerItem(
        label = { Text(c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge.reactive(c.key == current.key) { copy(wght = 700f, rond = 100f) }) },
        icon = { Icon(icon, null) },
        badge = { Text(count.toString(), style = MaterialTheme.typography.labelMedium) },
        selected = c.key == current.key,
        onClick = { onClick(c) },
        modifier = Modifier.height(48.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below), tooltip = { PlainTooltip { Text(text) } }, state = rememberTooltipState()) { content() }
}

@Composable
private fun SortMenu(order: SortOrder, desc: Boolean, onChange: (SortOrder, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, null); Spacer(Modifier.width(8.dp)); Text(order.label + if (desc) " ↓" else "")
        }
        DropdownMenu(open, { open = false }) {
            SortOrder.entries.forEach { o ->
                DropdownMenuItem(text = { Text(o.label) }, onClick = { onChange(o, if (o == order) !desc else false); open = false },
                    trailingIcon = { if (o == order) Text(if (desc) "↓" else "↑") })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(g: Game, selected: Boolean, onClick: (PointerKeyboardModifiers?) -> Unit, onDoubleClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val windowInfo = LocalWindowInfo.current
    Column(modifier.hoverable(interaction)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .pressBounce(interaction = interaction, depth = 0.94f)
                .focusEmphasis(hovered || selected, baseCorner = 16.dp, focusedCorner = 28.dp, scale = if (selected) 1.0f else 1.04f)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = { onClick(windowInfo.keyboardModifiers) }, onDoubleClick = onDoubleClick),
        ) {
            CoverArt(g, Modifier.fillMaxSize())
            if (g.favorite) Icon(Icons.Outlined.Favorite, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            if (g.kind == GameKind.PORT) Badge(Modifier.align(Alignment.TopStart).padding(8.dp), containerColor = MaterialTheme.colorScheme.tertiary) { Text("PORT") }
        }
        Text(g.title, style = MaterialTheme.typography.titleSmall.reactive(hovered || selected, FlexRecipes.hover), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(Platforms.get(g.platformId).shortName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameTable(games: List<Game>, selectedId: String?, multi: List<String>, onSelect: (Game, PointerKeyboardModifiers?) -> Unit, onPlay: (Game) -> Unit) {
    val windowInfo = LocalWindowInfo.current
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        items(games, key = { it.id }) { g ->
            val sel = g.id == selectedId || g.id in multi
            val bg by animateFloatAsState(if (sel) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec())
            Row(
                Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = bg))
                    .combinedClickable(onClick = { onSelect(g, windowInfo.keyboardModifiers) }, onDoubleClick = { onPlay(g) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoverArt(g, Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(8.dp)), maxDim = 120)
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(g.meta.releaseYear?.toString(), g.meta.developer).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Platforms.get(g.platformId).shortName, Modifier.width(90.dp), style = MaterialTheme.typography.bodyMedium)
                Text(formatAgo(g.lastPlayed), Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPlayTime(g.playTimeSeconds), Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (g.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (g.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(libraryEmpty: Boolean, searching: Boolean, onSettings: () -> Unit, onAddPort: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandLoader(Modifier.size(160.dp), intervalMillis = 2600)
        Spacer(Modifier.height(24.dp))
        Text(when { searching -> "No matches"; libraryEmpty -> "Your library is empty"; else -> "Nothing here yet" }, style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(if (libraryEmpty) "Add a game folder, import Steam, or add a recompiled/decompiled port." else "Try another collection or search.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        if (libraryEmpty) Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onSettings) { Text("Add game folders") }
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onAddPort) { Text("Add a port") }
        }
    }
}
