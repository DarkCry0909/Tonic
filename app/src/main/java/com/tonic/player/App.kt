package com.tonic.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch

/** Ширина, с которой плеер показывается справа постоянно (развёрнутый Mate X6, планшеты). */
private val WIDE = 720.dp

@Composable
fun App(vm: AppVm) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) {
            vm.syncStatus = "Окно входа закрыто или заблокировано Google (код 16). " +
                "Проверьте пункты ниже: Test users, включённые API и OAuth client."
        } else {
            try {
                val r = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(res.data)
                vm.onAuth(r.accessToken, r.toGoogleSignInAccount()?.email)
            } catch (e: Exception) {
                vm.syncStatus = "Вход не выполнен: ${e.message}"
            }
        }
    }
    val doSync = { vm.startSync { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) } }
    val doImport = { vm.importYoutube { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) } }
    val track = vm.currentTrack

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= WIDE
        LaunchedEffect(wide) { if (wide) progress.snapTo(0f) }
        if (vm.ui.glass.value) GlassBackdrop(vm, strong = false)

        val content: @androidx.compose.runtime.Composable () -> Unit = {
            if (settings) {
                SettingsScreen(vm, { settings = false }, { doSync() }, { doImport() })
            } else {
                HomeScreen(vm) { settings = true }
            }
        }

        if (wide) {
            // Развёрнутый складной экран: список слева, плеер справа
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding().navigationBarsPadding()) { content() }
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    if (track != null) {
                        ExpandedPlayer(vm)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Выберите трек", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) { content() }
                Spacer(Modifier.navigationBarsPadding().height(if (track != null) 64.dp else 0.dp))
            }
            PlayerSheet(vm, progress)
        }
    }
    BackHandler(enabled = settings) { settings = false }
    BackHandler(enabled = progress.value > 0.01f) { scope.launch { progress.animateTo(0f) } }
}
