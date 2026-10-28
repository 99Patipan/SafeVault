package app.safevault.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.safevault.crypto.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun AppRoot(vm: VaultViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val hasPendingImport by vm.hasPendingImport.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    // Launchers live at the root so results still arrive after the picker backgrounded (and locked) the app.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val bytes = vm.takeExportBytes()
        if (uri != null && bytes != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }.isSuccess
                }
                vm.showMessage(if (ok) Strings.EXPORT_SAVED else Strings.EXPORT_FAILED)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { readLimited(ctx, uri, BackupCrypto.MAX_FILE_BYTES) }
                if (bytes == null) vm.showMessage(Strings.FILE_UNREADABLE) else vm.setPendingImport(bytes)
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is UiEvent.ExportReady -> exportLauncher.launch(ev.fileName)
                UiEvent.PickImport -> importLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            AppState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            AppState.NeedSetup -> SetupScreen(vm)
            AppState.Locked -> UnlockScreen(vm)
            AppState.Unlocked -> UnlockedApp(vm)
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (state == AppState.Unlocked && hasPendingImport) ImportPasswordDialog(vm)
}

@Composable
fun UnlockedApp(vm: VaultViewModel) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm, nav) }
        composable("list/{filter}", arguments = listOf(navArgument("filter") { type = NavType.StringType })) { e ->
            ListScreen(vm, nav, e.arguments?.getString("filter") ?: "all")
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            DetailScreen(vm, nav, e.arguments?.getLong("id") ?: 0L)
        }
        composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            EditScreen(vm, nav, e.arguments?.getLong("id") ?: 0L)
        }
        composable("settings") { SettingsScreen(vm, nav) }
    }
}

/** Reads at most [limit] bytes; returns null if unreadable or larger (never loads an unbounded file). */
private fun readLimited(ctx: android.content.Context, uri: Uri, limit: Int): ByteArray? =
    try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    }
