package io.github.raafiabir.acc.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.raafiabir.acc.Backend
import io.github.raafiabir.acc.ShizukuState
import io.github.raafiabir.acc.UiState

class HomeActions(
    val clean: () -> Unit,
    val statusAction: () -> Unit,
    val setSchedule: (Boolean) -> Unit,
    val setTime: (Int, Int) -> Unit,
    val setCache: (Boolean) -> Unit,
    val setJunk: (Boolean) -> Unit,
    val setLeftover: (Boolean) -> Unit,
    val openExclusions: () -> Unit,
    val cycleBackend: () -> Unit,
)

@Composable
fun HomeScreen(ui: UiState, actions: HomeActions) {
    val ctx = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("ACC", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(48.dp))
        Text(
            text = ui.cacheBytes?.let { Formatter.formatFileSize(ctx, it) } ?: "–",
            style = SizeFigure,
        )
        Text(
            text = when {
                ui.cacheBytes == null -> "Cache size unavailable"
                ui.cacheApps == 1 -> "of cache in 1 app"
                else -> "of cache in ${ui.cacheApps} apps"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = actions.clean,
            enabled = !ui.cleaning,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                if (ui.cleaning) (ui.progress ?: "Cleaning…") else "Clean now",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(16.dp))
        val (statusText, statusAction) = status(ui)
        Text(statusText, style = MaterialTheme.typography.bodyMedium, color = muted)
        if (statusAction != null) {
            Text(
                statusAction,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = actions.statusAction).padding(top = 6.dp, bottom = 6.dp),
            )
        }
        ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        lastRunText(ctx, ui)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = muted)
        }

        Spacer(Modifier.height(32.dp))
        Rule()
        Item(
            title = "Scheduled cleaning",
            sub = if (ui.scheduleOn) "Every day" else "Off",
            onClick = { actions.setSchedule(!ui.scheduleOn) },
        ) { AccSwitch(ui.scheduleOn, actions.setSchedule) }
        if (ui.scheduleOn) {
            Item(
                title = "Time",
                onClick = { pickTime = true },
            ) { Text(formatTime(ui.hour, ui.minute), style = MaterialTheme.typography.bodyLarge) }
        }
        Rule()
        Item("App cache", "Internal and external cache of every app", { actions.setCache(!ui.cleanCache) }) {
            AccSwitch(ui.cleanCache, actions.setCache)
        }
        Item("Thumbnails and logs", "Gallery thumbnails and the system log", { actions.setJunk(!ui.cleanJunk) }) {
            AccSwitch(ui.cleanJunk, actions.setJunk)
        }
        Item(
            "Leftover folders",
            "Deletes data folders of uninstalled apps. Can't be undone.",
            { actions.setLeftover(!ui.cleanLeftover) },
        ) { AccSwitch(ui.cleanLeftover, actions.setLeftover) }
        Rule()
        Item("Excluded apps", onClick = actions.openExclusions) {
            Text(
                if (ui.excluded.isEmpty()) "None" else ui.excluded.size.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
        }
        Item("Access method", "Auto tries Shizuku, then root, then accessibility", actions.cycleBackend) {
            Text(ui.backend.label, style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        Rule()
        Spacer(Modifier.height(32.dp))
    }

    if (pickTime) {
        TimeDialog(
            hour = ui.hour,
            minute = ui.minute,
            onDismiss = { pickTime = false },
            onConfirm = { h, m -> actions.setTime(h, m); pickTime = false },
        )
    }
}

private fun status(ui: UiState): Pair<String, String?> {
    val neither = if (ui.backend == Backend.Auto) {
        "No Shizuku or root. Pick Accessibility under Access method to clean without them."
    } else null
    return when {
        ui.active != null && !ui.usageAccess ->
            "Usage access is off, so cache size can't be read." to "Open settings"
        ui.active == Backend.Root -> "Connected via root." to null
        ui.active == Backend.Shizuku -> "Connected via Shizuku." to null
        ui.active == Backend.Accessibility ->
            "Accessibility is on. A clean takes over the screen for a while." to null
        ui.backend == Backend.Accessibility -> "The accessibility service is off." to "Open settings"
        ui.backend == Backend.Root -> "Root access isn't available." to "Check again"
        ui.shizuku == ShizukuState.NotInstalled -> (neither ?: "Shizuku isn't installed.") to "Get Shizuku"
        ui.shizuku == ShizukuState.NotRunning -> (neither ?: "Shizuku isn't running.") to "Open Shizuku"
        else -> "ACC needs permission from Shizuku." to "Grant permission"
    }
}

private fun lastRunText(ctx: android.content.Context, ui: UiState): String? {
    if (ui.lastTime == 0L) return null
    val ago = DateUtils.getRelativeTimeSpanString(
        ui.lastTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString().lowercase()
    return when {
        ui.lastError != null -> "Last run $ago failed: ${ui.lastError}."
        ui.lastFreed >= 0 -> "Last cleaned $ago, freed ${Formatter.formatFileSize(ctx, ui.lastFreed)}."
        else -> "Last cleaned $ago."
    }
}

private fun formatTime(h: Int, m: Int) = "%02d:%02d".format(h, m)

@Composable
private fun Rule() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun Item(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
private fun AccSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.background,
            checkedTrackColor = c.onBackground,
            checkedBorderColor = c.onBackground,
            uncheckedThumbColor = c.outline,
            uncheckedTrackColor = c.background,
            uncheckedBorderColor = c.outline,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(hour, minute, DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onConfirm(state.hour, state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
