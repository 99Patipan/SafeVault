package app.safevault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.safevault.data.Categories
import app.safevault.data.VaultEntry
import app.safevault.logic.EntrySearch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: VaultViewModel, nav: NavController) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    val corrupted by vm.corrupted.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.APP_NAME) },
                actions = {
                    IconButton(onClick = { nav.navigate("list/all") }) { Icon(Icons.Filled.Search, Strings.SEARCH) }
                    IconButton(onClick = { vm.lockNow() }) { Icon(Icons.Filled.Lock, Strings.LOCK_NOW) }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, Strings.SETTINGS) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("edit/0") }) { Icon(Icons.Filled.Add, Strings.ADD_PASSWORD) }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            if (corrupted.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(Strings.corruptedWarning(corrupted.size), Modifier.padding(16.dp))
                }
            }
            if (audit.hasIssues) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.auditSummary(audit.duplicateGroups.size, audit.weakIds.size))
                        TextButton(onClick = { nav.navigate("list/audit") }) { Text(Strings.VIEW) }
                    }
                }
            }
            item { CategoryCard("🗂️", Strings.ALL, entries.size) { nav.navigate("list/all") } }
            items(Categories.ALL) { cat ->
                CategoryCard(categoryEmoji(cat), Strings.categoryName(cat), entries.count { it.content.category == cat }) {
                    nav.navigate("list/cat:$cat")
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(emoji: String, name: String, count: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Strings.accounts(count), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: VaultViewModel, nav: NavController, filter: String) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

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
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.BACK) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("edit/0") }) { Icon(Icons.Filled.Add, Strings.ADD_PASSWORD) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(Strings.SEARCH_HINT) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Text(Strings.EMPTY, Modifier.padding(24.dp))
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(shown, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth().clickable { nav.navigate("detail/${e.id}") }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(categoryEmoji(e.content.category), style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f)) {
                                Text(e.content.service, fontWeight = FontWeight.Bold)
                                val sub = e.content.username.ifEmpty { e.content.email }
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                                // The password is never shown in lists.
                                if (e.id in audit.duplicateIds) Text(Strings.BADGE_DUP, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                if (e.id in audit.weakIds) Text(Strings.BADGE_WEAK, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
