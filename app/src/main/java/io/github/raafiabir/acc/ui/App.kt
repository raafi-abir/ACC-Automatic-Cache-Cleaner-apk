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

@Composable
fun App(
    vm: MainViewModel,
    onStatusAction: (UiState) -> Unit,
    onScheduleEnabled: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var exclusions by rememberSaveable { mutableStateOf(false) }

    if (exclusions) {
        LaunchedEffect(Unit) { vm.loadApps() }
        ExclusionsScreen(
            apps = apps,
            excluded = ui.excluded,
            onToggle = vm::toggleExcluded,
            onBack = { exclusions = false },
        )
    } else {
        HomeScreen(
            ui = ui,
            actions = HomeActions(
                clean = vm::clean,
                statusAction = { onStatusAction(ui) },
                setSchedule = { on ->
                    vm.setSchedule(on)
                    if (on) onScheduleEnabled()
                },
                setTime = vm::setTime,
                setCache = vm::setCache,
                setJunk = vm::setJunk,
                setLeftover = vm::setLeftover,
                openExclusions = { exclusions = true },
                cycleBackend = vm::cycleBackend,
            ),
        )
    }
}
