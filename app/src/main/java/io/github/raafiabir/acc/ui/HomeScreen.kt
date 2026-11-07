package io.github.raafiabir.acc.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raafiabir.acc.Backend
import io.github.raafiabir.acc.ShizukuState
import io.github.raafiabir.acc.Slice
import io.github.raafiabir.acc.UiState

class HomeActions(
    val clean: () -> Unit,
    val stop: () -> Unit,
    val statusAction: () -> Unit,
    val setSchedule: (Boolean) -> Unit,
    val open: (String) -> Unit,
)

private val barAlphas = listOf(1f, 0.72f, 0.48f, 0.26f)

@Composable
fun HomeScreen(ui: UiState, actions: HomeActions) {
    val ctx = LocalContext.current
    val c = Acc.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).height(40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ACC", style = AccType.wordmark, color = c.ink)
            StatusChip(ui.active?.label)
        }

        if (ui.updateVersion != null) {
            Panel(Modifier.padding(top = 12.dp)) {
                ListRow(
                    "Update available",
                    "ACC ${ui.updateVersion} is ready to download",
                    { actions.open("updates") },
                ) { Chevron() }
            }
        }

        CacheFigure(ctx, ui)
        UsageBar(ctx, ui)

        Spacer(Modifier.height(28.dp))
        if (ui.cleaning && ui.active == Backend.Accessibility) {
            PrimaryButton("Stop", actions.stop, ghost = true)
        } else {
            PrimaryButton(
                text = if (ui.cleaning) (ui.progress ?: "Cleaning…") else "Clean now",
                onClick = actions.clean,
                enabled = !ui.cleaning,
            )
        }
        Notes(ctx, ui, actions.statusAction)

        Spacer(Modifier.height(28.dp))
        Panel {
            ListRow(
                title = "Scheduled cleaning",
                sub = if (ui.scheduleOn) "${repeatText(ui)} at ${formatTime(ui.hour, ui.minute)}" else "Off",
                onClick = { actions.open("schedule") },
            ) { AccSwitch(ui.scheduleOn, actions.setSchedule) }
            Hairline()
            ListRow("What to clean", cleanSummary(ui), { actions.open("clean") }) { Chevron() }
            Hairline()
            ListRow(
                "Excluded apps",
                if (ui.excluded.isEmpty()) "None" else if (ui.excluded.size == 1) "1 app" else "${ui.excluded.size} apps",
                { actions.open("exclusions") },
            ) { Chevron() }
            Hairline()
            ListRow("Access method", accessSummary(ui), { actions.open("access") }) { Chevron() }
            Hairline()
            ListRow(
                "Updates",
                if (ui.updateVersion != null) "${ui.updateVersion} available" else "Version ${ui.version}",
                { actions.open("updates") },
            ) { Chevron() }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatusChip(label: String?) {
    val c = Acc.colors
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (label != null) c.acc else c.mute))
        Text(label ?: "Not connected", style = AccType.smallMedium, color = c.ink)
    }
}

@Composable
private fun CacheFigure(ctx: Context, ui: UiState) {
    val c = Acc.colors
    val parts = ui.cacheBytes?.let { splitSize(Formatter.formatFileSize(ctx, it)) }
    Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.Bottom) {
        Text(parts?.first ?: "–", style = AccType.big, color = c.ink)
        if (parts != null && parts.second.isNotEmpty()) {
            Text(parts.second, style = AccType.unit, color = c.mute, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
        }
    }
    Text(
        text = when {
            ui.cacheBytes == null -> "Cache size unavailable"
            ui.cacheApps == 1 -> "of cache in 1 app"
            else -> "of cache across ${ui.cacheApps} apps"
        },
        style = AccType.caption,
        color = c.mute,
        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
    )
}

@Composable
private fun UsageBar(ctx: Context, ui: UiState) {
    val c = Acc.colors
    val total = ui.cacheBytes ?: 0L
    if (total <= 0L || ui.top.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(c.line))
        return
    }
    val others = (total - ui.top.sumOf { it.bytes }).coerceAtLeast(0L)
    val parts: List<Slice> = if (others > 0) ui.top + Slice("Others", others) else ui.top

    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        parts.forEachIndexed { i, s ->
            Box(
                Modifier
                    .weight(s.bytes.toFloat().coerceAtLeast(1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.acc.copy(alpha = barAlphas[i]))
            )
        }
    }
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEachIndexed { j, s -> LegendCell(ctx, s, barAlphas[row * 2 + j], Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendCell(ctx: Context, s: Slice, alpha: Float, modifier: Modifier) {
    val c = Acc.colors
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c.acc.copy(alpha = alpha)))
            Text(s.name, style = AccType.small, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Formatter.formatShortFileSize(ctx, s.bytes), style = AccType.smallMedium, color = c.ink, maxLines = 1)
    }
}

@Composable
private fun Notes(ctx: Context, ui: UiState, onAction: () -> Unit) {
    val c = Acc.colors
    val (text, action) = status(ui)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (text != null) Text(text, style = AccType.small, color = c.mute, textAlign = TextAlign.Center)
        if (action != null) {
            Text(
                action,
                style = AccType.smallMedium,
                color = c.acc,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        ui.message?.let { Text(it, style = AccType.small, color = c.ink, textAlign = TextAlign.Center) }
        lastRunText(ctx, ui)?.let {
            Text(it, style = AccType.small, color = c.mute, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Text shown under the button when something needs attention, plus an optional action label. */
private fun status(ui: UiState): Pair<String?, String?> {
    val neither = if (ui.backend == Backend.Auto) {
        "No Shizuku or root. Pick Accessibility under Access method to clean without them."
    } else null
    return when {
        ui.active != null && !ui.usageAccess ->
            "Usage access is off, so cache size can't be read." to "Open settings"
        ui.active == Backend.Accessibility && !ui.cleaning ->
            "Cleaning opens each app's storage page in Settings. Keep the phone unlocked." to null
        ui.active != null -> null to null
        ui.backend == Backend.Accessibility ->
            (if (ui.accessOffAfter) "Turn the accessibility service on to clean. ACC switches it off afterwards."
            else "The accessibility service is off.") to "Open settings"
        ui.backend == Backend.Root -> "Root access isn't available." to "Check again"
        ui.shizuku == ShizukuState.NotInstalled -> (neither ?: "Shizuku isn't installed.") to "Get Shizuku"
        ui.shizuku == ShizukuState.NotRunning -> (neither ?: "Shizuku isn't running.") to "Open Shizuku"
        else -> "ACC needs permission from Shizuku." to "Grant permission"
    }
}

private fun lastRunText(ctx: Context, ui: UiState): String? {
    if (ui.lastTime == 0L) return null
    val ago = DateUtils.getRelativeTimeSpanString(
        ui.lastTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString().lowercase()
    return when {
        ui.lastError != null -> "Last run $ago failed: ${ui.lastError}."
        ui.lastFreed >= 0 ->
            "Last cleaned $ago, freed ${Formatter.formatFileSize(ctx, ui.lastFreed)}" +
                (if (ui.lastRemaining >= 5L shl 20) ", ${Formatter.formatFileSize(ctx, ui.lastRemaining)} left" else "") + "."
        else -> "Last cleaned $ago."
    }
}

private fun cleanSummary(ui: UiState): String {
    val items = listOfNotNull(
        if (ui.cleanCache) "app cache" else null,
        if (ui.cleanJunk) "thumbnails and logs" else null,
        if (ui.cleanLeftover) "leftover folders" else null,
    )
    return if (items.isEmpty()) "Nothing selected" else items.joinToString(", ").replaceFirstChar { it.uppercase() }
}

private fun accessSummary(ui: UiState): String =
    if (ui.backend == Backend.Auto) "Auto" + (ui.active?.let { ", using ${it.label}" } ?: "") else ui.backend.label

fun repeatText(ui: UiState): String = when (ui.intervalDays) {
    1 -> "Every day"
    7 -> "Every week"
    30 -> "Every month"
    else -> "Every ${ui.intervalDays} days"
}

fun formatTime(h: Int, m: Int) = "%02d:%02d".format(h, m)

private fun splitSize(s: String): Pair<String, String> {
    val i = s.indexOfLast { it == ' ' || it == '\u00A0' }
    return if (i > 0) s.substring(0, i) to s.substring(i + 1) else s to ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(hour, minute, DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onConfirm(state.hour, state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
