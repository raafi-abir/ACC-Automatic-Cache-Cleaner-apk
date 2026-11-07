package io.github.raafiabir.acc.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raafiabir.acc.UiState

@Composable
fun UpdatesScreen(
    ui: UiState,
    onCheck: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Acc.colors
    val ctx = LocalContext.current
    SubScreen("Updates", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel {
                ListRow("Version", ui.version)
                Hairline()
                ListRow("Status", statusText(ctx, ui)) {
                    SmallButton(
                        if (ui.checkingUpdate) "Checking…" else "Check now",
                        { if (!ui.checkingUpdate) onCheck() },
                        ghost = true,
                    )
                }
            }

            if (ui.updateVersion != null) {
                val download = ui.updateApk ?: ui.updatePage
                Panel(Modifier.padding(top = 12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("ACC ${ui.updateVersion}", style = AccType.title, color = c.ink)
                        if (ui.updateNotes.isNotBlank()) {
                            Text(
                                ui.updateNotes,
                                style = AccType.desc,
                                color = c.mute,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (download != null) SmallButton("Download", { onOpen(download) })
                            if (ui.updatePage != null) SmallButton("Release page", { onOpen(ui.updatePage) }, ghost = true)
                        }
                    }
                }
            }

            Panel(Modifier.padding(top = 12.dp)) {
                SwitchRow(
                    "Check automatically",
                    "About once a day, when there is a network connection. You get a notification.",
                    ui.updateCheck,
                    onToggle,
                )
            }
            Text(
                "Only api.github.com is contacted. It sees your IP address and the app version, nothing else. " +
                    "Updates are never installed by themselves: Download opens the file in your browser.",
                style = AccType.small,
                color = c.mute,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
    }
}

private fun statusText(ctx: Context, ui: UiState): String {
    val ago = if (ui.lastUpdateCheck > 0) {
        DateUtils.getRelativeTimeSpanString(
            ui.lastUpdateCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString().lowercase()
    } else null
    return when {
        ui.checkingUpdate -> "Checking…"
        ui.updateVersion != null -> "${ui.updateVersion} is available"
        ui.updateError != null -> "Couldn't check: ${ui.updateError}"
        ago == null -> "Not checked yet"
        else -> "Up to date, checked $ago"
    }
}
