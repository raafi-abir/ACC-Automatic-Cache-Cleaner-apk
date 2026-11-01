package io.github.raafiabir.acc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raafiabir.acc.MainViewModel
import io.github.raafiabir.acc.UiState

/** Things only the activity can do (open system screens, ask for runtime permissions). */
class AppActions(
    val statusAction: (UiState) -> Unit,
    val requestNotifications: () -> Unit,
    val openAppInfo: () -> Unit,
    val openUsageAccess: () -> Unit,
    val openAccessibility: () -> Unit,
    val openShizuku: () -> Unit,
    val getShizuku: () -> Unit,
)

@Composable
fun App(vm: MainViewModel, actions: AppActions) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }

    LaunchedEffect(screen) { if (screen == "exclusions") vm.loadApps() }

    if (!ui.setupDone) {
        SetupScreen(
            ui = ui,
            act = SetupActions(
                chooseBackend = vm::setBackend,
                getShizuku = actions.getShizuku,
                openShizuku = actions.openShizuku,
                allowShizuku = vm::requestPermission,
                checkRoot = vm::recheckRoot,
                openAccessibility = actions.openAccessibility,
                openAppInfo = actions.openAppInfo,
                allowUsage = actions.openUsageAccess,
                allowNotifications = actions.requestNotifications,
                finish = vm::finishSetup,
            ),
        )
    } else {
        when (screen) {
            "clean" -> WhatToCleanScreen(
                ui = ui,
                onCache = vm::setCache,
                onJunk = vm::setJunk,
                onLeftover = vm::setLeftover,
                onBack = { screen = "home" },
            )
            "access" -> AccessScreen(ui = ui, onChoose = vm::setBackend, onBack = { screen = "home" })
            "exclusions" -> ExclusionsScreen(
                apps = apps,
                excluded = ui.excluded,
                onToggle = vm::toggleExcluded,
                onBack = { screen = "home" },
            )
            else -> HomeScreen(
                ui = ui,
                actions = HomeActions(
                    clean = vm::clean,
                    stop = vm::stop,
                    statusAction = { actions.statusAction(ui) },
                    setSchedule = { on ->
                        vm.setSchedule(on)
                        if (on && !ui.notifications) actions.requestNotifications()
                    },
                    setTime = vm::setTime,
                    open = { screen = it },
                ),
            )
        }
    }
}
