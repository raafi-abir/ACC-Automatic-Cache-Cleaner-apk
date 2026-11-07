package io.github.raafiabir.acc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raafiabir.acc.AppEntry
import io.github.raafiabir.acc.Backend
import io.github.raafiabir.acc.UiState

@Composable
fun WhatToCleanScreen(
    ui: UiState,
    onCache: (Boolean) -> Unit,
    onJunk: (Boolean) -> Unit,
    onLeftover: (Boolean) -> Unit,
    onAll: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SubScreen("What to clean", onBack) {
        Panel {
            SwitchRow("App cache", "Internal and external cache of every app", ui.cleanCache, onCache)
            Hairline()
            SwitchRow("Thumbnails and logs", "Gallery thumbnails and the system log", ui.cleanJunk, onJunk)
            Hairline()
            SwitchRow(
                "Leftover folders",
                "Deletes data folders of uninstalled apps. Can't be undone.",
                ui.cleanLeftover,
                onLeftover,
            )
        }
        Panel(Modifier.padding(top = 12.dp)) {
            SwitchRow(
                "Clean every app",
                "Accessibility mode only. Removes the limit of 40 apps and the 1 MB minimum, so it takes much longer.",
                ui.a11yAll,
                onAll,
            )
        }
        Text(
            "Accessibility mode can only clear app cache. The rest needs Shizuku or root.",
            style = AccType.small,
            color = Acc.colors.mute,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
}

private const val GRANT_COMMAND =
    "adb shell pm grant io.github.raafiabir.acc android.permission.WRITE_SECURE_SETTINGS"

@Composable
fun AccessScreen(ui: UiState, onChoose: (Backend) -> Unit, onOffAfter: (Boolean) -> Unit, onBack: () -> Unit) {
    val c = Acc.colors
    val clipboard = LocalClipboardManager.current
    val options = listOf(
        Backend.Auto to "Tries Shizuku, then root, then accessibility.",
        Backend.Shizuku to "Fast and silent. Needs the Shizuku app running.",
        Backend.Root to "Fast and silent. Needs root.",
        Backend.Accessibility to "No extra tools. Slower, and it takes over the screen.",
    )
    SubScreen("Access method", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel {
                options.forEachIndexed { i, (backend, description) ->
                    if (i > 0) Hairline()
                    ListRow(backend.label, description, onClick = { onChoose(backend) }) {
                        RadioDot(ui.backend == backend)
                    }
                }
            }
            Text(
                text = ui.active?.let { "Using ${it.label} right now." } ?: "Nothing is available right now.",
                style = AccType.small,
                color = c.mute,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )

            Text(
                "Accessibility mode",
                style = AccType.smallMedium,
                color = c.mute,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
            )
            Panel {
                SwitchRow(
                    "Turn it off after cleaning",
                    "Banking apps such as bKash can refuse to run while an accessibility service is on.",
                    ui.accessOffAfter,
                    onOffAfter,
                )
            }
            if (ui.canSecure) {
                Text(
                    "ACC switches the service on by itself for each clean, so it only runs while cleaning.",
                    style = AccType.small,
                    color = c.mute,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            } else {
                Text(
                    "To let ACC switch it on by itself, run this once from a computer with USB debugging:",
                    style = AccType.small,
                    color = c.mute,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
                Panel(Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SelectionContainer {
                            Text(
                                GRANT_COMMAND,
                                style = AccType.small.copy(fontFamily = FontFamily.Monospace),
                                color = c.ink,
                            )
                        }
                        Row(Modifier.padding(top = 12.dp)) {
                            SmallButton("Copy command", { clipboard.setText(AnnotatedString(GRANT_COMMAND)) }, ghost = true)
                        }
                    }
                }
            }

            Text(
                "Diagnostics",
                style = AccType.smallMedium,
                color = c.mute,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
            )
            Panel {
                val hasLog = ui.lastLog.isNotBlank()
                ListRow(
                    "Copy last clean log",
                    if (hasLog) "Paste it into a bug report" else "Nothing recorded yet",
                    onClick = if (hasLog) ({ clipboard.setText(AnnotatedString(ui.lastLog)) }) else null,
                )
            }
        }
    }
}

@Composable
fun ScheduleScreen(
    ui: UiState,
    onToggle: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
    onRepeat: (String) -> Unit,
    onCustomDays: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val c = Acc.colors
    var pickTime by remember { mutableStateOf(false) }
    val modes = listOf(
        Triple("daily", "Every day", null),
        Triple("weekly", "Every week", "Every 7 days"),
        Triple("monthly", "Every month", "Every 30 days"),
        Triple("custom", "Custom", "Every ${ui.customDays} days"),
    )
    SubScreen("Scheduled cleaning", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel { SwitchRow("Clean automatically", if (ui.scheduleOn) "On" else "Off", ui.scheduleOn, onToggle) }

            Text(
                "Repeat",
                style = AccType.smallMedium,
                color = c.mute,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
            )
            Panel {
                modes.forEachIndexed { i, (mode, label, sub) ->
                    if (i > 0) Hairline()
                    ListRow(label, sub, onClick = { onRepeat(mode) }) { RadioDot(ui.repeatMode == mode) }
                }
                if (ui.repeatMode == "custom") {
                    Hairline()
                    DaysStepper(ui.customDays, onCustomDays)
                }
            }

            Panel(Modifier.padding(top = 12.dp)) {
                ListRow("Time", onClick = { pickTime = true }) {
                    Text(formatTime(ui.hour, ui.minute), style = AccType.body, color = c.mute)
                }
            }
            Text(
                "The first clean runs at the next ${formatTime(ui.hour, ui.minute)}, then repeats. " +
                    "Android may delay it a little to save battery.",
                style = AccType.small,
                color = c.mute,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
    }

    if (pickTime) {
        TimeDialog(
            hour = ui.hour,
            minute = ui.minute,
            onDismiss = { pickTime = false },
            onConfirm = { h, m ->
                onTime(h, m)
                pickTime = false
            },
        )
    }
}

@Composable
private fun DaysStepper(days: Int, onChange: (Int) -> Unit) {
    val c = Acc.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Days between cleans", style = AccType.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        SmallButton("\u2212", { onChange((days - 1).coerceAtLeast(1)) }, ghost = true)
        Text(
            "$days",
            style = AccType.title,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        SmallButton("+", { onChange((days + 1).coerceAtMost(365)) }, ghost = true)
    }
}

@Composable
fun ExclusionsScreen(
    apps: List<AppEntry>,
    excluded: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Acc.colors
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    // Ignores `excluded` on purpose so rows don't jump when you tick one.
    val shown = remember(apps, query, showSystem) {
        apps.filter { a ->
            (showSystem || !a.system) &&
                (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }
    }

    SubScreen("Excluded apps", onBack) {
        Text(
            "Their cache and folders are left alone by every clean.",
            style = AccType.desc,
            color = c.mute,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search apps", style = AccType.body) },
            textStyle = AccType.body,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.surface,
                unfocusedContainerColor = c.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = c.acc,
                focusedTextColor = c.ink,
                unfocusedTextColor = c.ink,
                focusedPlaceholderColor = c.mute,
                unfocusedPlaceholderColor = c.mute,
            ),
        )
        Panel(Modifier.padding(top = 12.dp)) {
            SwitchRow("Show system apps", null, showSystem) { showSystem = it }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
            items(shown, key = { it.pkg }) { app ->
                AppRow(app, app.pkg in excluded) { onToggle(app.pkg) }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    val c = Acc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, style = AccType.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.pkg, style = AccType.small, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = c.acc, checkmarkColor = c.onAcc, uncheckedColor = c.mute),
        )
    }
}
