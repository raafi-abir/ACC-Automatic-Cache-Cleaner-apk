package io.github.raafiabir.acc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.github.raafiabir.acc.ui.AccTheme
import io.github.raafiabir.acc.ui.App

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccTheme {
                App(
                    vm = vm,
                    onStatusAction = ::onStatusAction,
                    onScheduleEnabled = ::askNotifications,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun onStatusAction(ui: UiState) {
        when {
            ui.active != null ->
                if (!ui.usageAccess) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            ui.backend == Backend.Accessibility ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            ui.backend == Backend.Root -> vm.recheckRoot()
            ui.shizuku == ShizukuState.NotInstalled ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            ui.shizuku == ShizukuState.NotRunning ->
                packageManager.getLaunchIntentForPackage(ShizukuBridge.SHIZUKU_PACKAGE)?.let(::startActivity)
            ui.shizuku == ShizukuState.NeedsPermission -> vm.requestPermission()
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
