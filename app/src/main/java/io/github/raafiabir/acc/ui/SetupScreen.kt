package io.github.raafiabir.acc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.raafiabir.acc.Backend
import io.github.raafiabir.acc.ShizukuState
import io.github.raafiabir.acc.UiState

class SetupActions(
    val chooseBackend: (Backend) -> Unit,
    val getShizuku: () -> Unit,
    val openShizuku: () -> Unit,
    val allowShizuku: () -> Unit,
    val checkRoot: () -> Unit,
    val openAccessibility: () -> Unit,
    val openAppInfo: () -> Unit,
    val allowUsage: () -> Unit,
    val allowNotifications: () -> Unit,
    val finish: () -> Unit,
)

@Composable
fun SetupScreen(ui: UiState, act: SetupActions) {
    val c = Acc.colors
    // While the method is still on Auto, show whichever one is working (Shizuku if none is).
    val shown = if (ui.backend == Backend.Auto) (ui.active ?: Backend.Shizuku) else ui.backend
    val accessOk = ui.active != null && ui.active == shown
    val done = listOf(accessOk, ui.usageAccess, ui.notifications).count { it }

    val usageState = when {
        ui.usageAccess -> BadgeState.Done
        accessOk -> BadgeState.Current
        else -> BadgeState.Off
    }
    val notifState = when {
        ui.notifications -> BadgeState.Done
        accessOk && ui.usageAccess -> BadgeState.Current
        else -> BadgeState.Off
    }

    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= done) c.acc else c.line)
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Set up ACC", style = AccType.h1, color = c.ink, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
            Text(
                "ACC clears other apps' cache, so it needs one way in. You can change this later.",
                style = AccType.caption,
                color = c.mute,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            Step(if (accessOk) BadgeState.Done else BadgeState.Current, 1, "Access method") {
                AccessBlock(shown, accessOk, ui, act)
            }
            Step(usageState, 2, "Usage access") {
                if (ui.usageAccess) {
                    Note("Granted.")
                } else {
                    Note("Lets ACC see which apps hold cache. With Shizuku or root it's granted automatically.")
                    Buttons { SmallButton("Allow", act.allowUsage, ghost = usageState != BadgeState.Current) }
                }
            }
            Step(notifState, 3, "Notifications", tag = "Optional") {
                if (ui.notifications) {
                    Note("Allowed.")
                } else {
                    Note("A note after a scheduled clean, or when one is skipped.")
                    Buttons { SmallButton("Allow", act.allowNotifications, ghost = true) }
                }
            }
        }

        PrimaryButton(
            text = "Continue",
            onClick = act.finish,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            enabled = accessOk,
        )
    }
}

@Composable
private fun AccessBlock(shown: Backend, accessOk: Boolean, ui: UiState, act: SetupActions) {
    Column {
        Box(Modifier.padding(top = 12.dp)) {
            Segmented(
                options = listOf(
                    Backend.Shizuku to "Shizuku",
                    Backend.Root to "Root",
                    Backend.Accessibility to "Accessibility",
                ),
                selected = shown,
                onSelect = act.chooseBackend,
            )
        }
        when (shown) {
            Backend.Shizuku -> when (ui.shizuku) {
                ShizukuState.Ready -> Note("Shizuku is running and ACC has permission.", false)
                ShizukuState.NotInstalled -> {
                    Note("Shizuku isn't installed.", false)
                    Buttons(false) { SmallButton("Get Shizuku", act.getShizuku) }
                }
                ShizukuState.NotRunning -> {
                    Note("Shizuku is installed but not running. Start it from its app.", false)
                    Buttons(false) { SmallButton("Open Shizuku", act.openShizuku) }
                }
                ShizukuState.NeedsPermission -> {
                    Note("Shizuku is running. Allow ACC in its popup.", false)
                    Buttons(false) { SmallButton("Allow", act.allowShizuku) }
                }
            }
            Backend.Root -> if (accessOk) {
                Note("Root access granted.", false)
            } else {
                Note("Allow root when your root manager asks.", false)
                Buttons(false) { SmallButton("Check root", act.checkRoot) }
            }
            Backend.Accessibility -> if (accessOk) {
                Note("Accessibility is on.", false)
            } else {
                Note(
                    "Turn on ACC in Accessibility settings. On Android 13 and later, first allow restricted " +
                        "settings in App info (three-dot menu). Banking apps such as bKash can refuse to run " +
                        "while it is on, so ACC switches it off after each clean.",
                    false,
                )
                Buttons(false) {
                    SmallButton("Open settings", act.openAccessibility)
                    SmallButton("Open app info", act.openAppInfo, ghost = true)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun Step(
    state: BadgeState,
    number: Int,
    title: String,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Acc.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBadge(state, number)
            Text(title, style = AccType.title, color = c.ink, modifier = Modifier.weight(1f))
            if (tag != null) Tag(tag)
        }
        content()
    }
}

@Composable
private fun Note(text: String, indent: Boolean = true) {
    Text(
        text,
        style = AccType.desc,
        color = Acc.colors.mute,
        modifier = Modifier.padding(top = 8.dp, start = if (indent) 40.dp else 0.dp),
    )
}

@Composable
private fun Buttons(indent: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.padding(top = 12.dp, start = if (indent) 40.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
