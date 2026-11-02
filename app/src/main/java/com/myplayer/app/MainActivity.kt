package com.myplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermission = isGranted(audioPermission())

        setContent {
            PlayerApp(
                vm = vm,
                hasPermission = hasPermission,
                onRequestPermission = { permissionLauncher.launch(wantedPermissions()) },
            )
        }

        if (savedInstanceState == null) {
            val missing = wantedPermissions().filter { !isGranted(it) }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshPermission()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val mime = intent.type ?: contentResolver.getType(uri) ?: ""
        vm.playExternal(uri, mime)
    }

    private fun refreshPermission() {
        hasPermission = isGranted(audioPermission())
        if (hasPermission) vm.loadLibrary()
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun audioPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun wantedPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

@Composable
fun PlayerApp(
    vm: PlayerViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (vm.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    AppTheme(dark = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize()) {
                LibraryScreen(
                    vm = vm,
                    dark = dark,
                    hasPermission = hasPermission,
                    onRequestPermission = onRequestPermission,
                )
                if (vm.showNowPlaying && vm.currentItem != null) {
                    NowPlayingScreen(vm)
                }
            }
        }
    }
}
