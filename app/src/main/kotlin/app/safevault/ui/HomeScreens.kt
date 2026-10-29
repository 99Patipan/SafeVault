package app.safevault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.safevault.data.Categories
import app.safevault.data.VaultEntry
import app.safevault.logic.EntrySearch

private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
fun HomeScreen(vm: VaultViewModel, nav: NavController) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    val corrupted by vm.corrupted.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = { HomeHeader(onLock = { vm.lockNow() }, onSettings = { nav.navigate("settings") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("edit/0") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(Strings.ADD_PASSWORD) },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = FullSpan) { SearchPill { nav.navigate("list/all") } }
            item(span = FullSpan) { VaultStatusCard(count = entries.size, healthy = !audit.hasIssues) }
            if (corrupted.isNotEmpty()) item(span = FullSpan) {
                Banner(Icons.Filled.Warning, Strings.corruptedWarning(corrupted.size), scheme.errorContainer, scheme.onErrorContainer)
            }
            if (audit.hasIssues) item(span = FullSpan) {
                Banner(
                    Icons.Filled.Warning,
                    Strings.auditSummary(audit.duplicateGroups.size, audit.weakIds.size),
                    scheme.tertiaryContainer,
                    scheme.onTertiaryContainer,
                    action = {
                        TextButton(
                            onClick = { nav.navigate("list/audit") },
                            colors = ButtonDefaults.textButtonColors(contentColor = scheme.onTertiaryContainer),
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text(Strings.VIEW, style = MaterialTheme.typography.labelLarge) }
                    },
                )
            }
            if (entries.isEmpty()) {
                item(span = FullSpan) { EmptyState("🗝️", Strings.EMPTY_TITLE, Strings.EMPTY_BODY) }
            } else {
                item(span = FullSpan) { SectionTitle(Strings.CATEGORIES, Modifier.padding(top = 4.dp)) }
                item { CategoryCard("🗂️", scheme.primary, Strings.ALL, entries.size) { nav.navigate("list/all") } }
                items(Categories.ALL) { cat ->
                    CategoryCard(categoryEmoji(cat), categoryTint(cat), Strings.categoryName(cat), entries.count { it.content.category == cat }) {
                        nav.navigate("list/cat:$cat")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onLock: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(40.dp)
        Text(
            Strings.APP_NAME,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        FilledTonalIconButton(onClick = onLock) { Icon(Icons.Filled.Lock, Strings.LOCK_NOW) }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, Strings.SETTINGS) }
    }
}

/** Looks like a search bar but just opens the list, where the real field lives. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = scheme.onSurfaceVariant)
        Text(
            Strings.SEARCH_HINT,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VaultStatusCard(count: Int, healthy: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val fg = scheme.onPrimaryContainer
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (count == 0) Strings.VAULT_READY else Strings.protectedCount(count),
                    style = MaterialTheme.typography.titleLarge,
                    color = fg,
                )
                Text(Strings.HOME_STATUS_NOTE, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f))
            }
            Box(
                Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(fg.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lock, null, tint = fg, modifier = Modifier.size(28.dp)) }
        }
        if (count > 0 && healthy) {
            Row(
                Modifier.clip(CircleShape).background(fg.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.size(16.dp))
                Text(Strings.ALL_GOOD, style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

@Composable
private fun CategoryCard(emoji: String, tint: androidx.compose.ui.graphics.Color, name: String, count: Int, onClick: () -> Unit) {
    SvCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EmojiTile(emoji, tint, size = 44.dp)
            Spacer(Modifier.height(10.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Strings.accounts(count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ListScreen(vm: VaultViewModel, nav: NavController, filter: String) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    val base: List<VaultEntry> = when {
        filter.startsWith("cat:") -> entries.filter { it.content.category == filter.removePrefix("cat:") }
        filter == "audit" -> entries.filter { it.id in audit.duplicateIds || it.id in audit.weakIds }
        else -> entries
    }
    val shown = remember(base, query) { EntrySearch.filter(base, query) }
    val title = when {
        filter.startsWith("cat:") -> Strings.categoryName(filter.removePrefix("cat:"))
        filter == "audit" -> Strings.NEEDS_ATTENTION
        else -> Strings.ALL
    }

    Scaffold(
        topBar = { SvTopBar(title, onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("edit/0") }) { Icon(Icons.Filled.Add, Strings.ADD_PASSWORD) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(Strings.SEARCH_HINT, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, Strings.SEARCH_CLEAR) }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) {
                if (query.isNotBlank()) EmptyState("🔍", Strings.NO_RESULTS)
                else EmptyState("🗂️", Strings.EMPTY)
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(shown, key = { it.id }) { e ->
                    SvCard(Modifier.fillMaxWidth(), onClick = { nav.navigate("detail/${e.id}") }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CategoryTile(e.content.category, 44.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(e.content.service, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = e.content.username.ifEmpty { e.content.email }
                                if (sub.isNotEmpty()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                // The password is never shown in lists.
                                val dup = e.id in audit.duplicateIds
                                val weak = e.id in audit.weakIds
                                if (dup || weak) {
                                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (dup) Pill(Strings.BADGE_DUP, scheme.errorContainer, scheme.onErrorContainer)
                                        if (weak) Pill(Strings.BADGE_WEAK, scheme.errorContainer, scheme.onErrorContainer)
                                    }
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.outline)
                        }
                    }
                }
            }
        }
    }
}
