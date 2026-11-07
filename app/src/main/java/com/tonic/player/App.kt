package com.tonic.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.Identity

@Composable
fun App(vm: AppVm) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (vm.ui.glass.value) GlassBackdrop(vm, strong = false)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                if (settings) {
                    SettingsScreen(vm, { settings = false }, { doSync() }, { doImport() })
                } else {
                    HomeScreen(vm) { settings = true }
                }
            }
            Column(Modifier.navigationBarsPadding()) { MiniPlayer(vm) { full = true } }
        }
        AnimatedVisibility(
            visible = full,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) { FullPlayer(vm) { full = false } }
    }
    BackHandler(enabled = full) { full = false }
    BackHandler(enabled = settings && !full) { settings = false }
}
