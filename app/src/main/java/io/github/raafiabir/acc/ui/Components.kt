package io.github.raafiabir.acc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Rounded surface that groups rows. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Acc.colors.surface),
        content = content,
    )
}

@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Acc.colors.line))
}

@Composable
fun ListRow(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Acc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AccType.bodyMedium, color = c.ink)
            if (sub != null) Text(sub, style = AccType.small, color = c.mute)
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Acc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AccType.bodyMedium, color = c.ink)
            if (sub != null) Text(sub, style = AccType.small, color = c.mute)
        }
        AccSwitch(checked, null)
    }
}

/** Pass [onChange] for a switch that handles its own taps, null when the row does. */
@Composable
fun AccSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    val c = Acc.colors
    val thumbX by animateDpAsState(if (checked) 21.dp else 3.dp, label = "thumb")
    val tap = if (onChange != null) {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 11.dp, horizontal = 4.dp)
    } else Modifier
    Box(tap) {
        Box(
            Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(if (checked) c.acc else c.line)
        ) {
            Box(
                Modifier.offset(x = thumbX, y = 3.dp).size(20.dp).clip(CircleShape)
                    .background(if (checked) c.onAcc else Color.White)
            )
        }
    }
}

@Composable
fun Chevron() {
    val color = Acc.colors.mute
    Canvas(Modifier.size(8.dp, 13.dp)) {
        val w = 2.dp.toPx()
        val p = Path().apply {
            moveTo(w / 2, w / 2)
            lineTo(size.width - w / 2, size.height / 2)
            lineTo(w / 2, size.height - w / 2)
        }
        drawPath(p, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val c = Acc.colors
    Box(
        Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) c.acc else c.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.acc))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    ghost: Boolean = false,
) {
    val c = Acc.colors
    val shape = RoundedCornerShape(16.dp)
    val bg = when { ghost -> Color.Transparent; enabled -> c.acc; else -> c.line }
    val fg = when { ghost -> c.ink; enabled -> c.onAcc; else -> c.mute }
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(bg)
            .then(if (ghost) Modifier.border(1.dp, c.line, shape) else Modifier)
            .clickable(enabled = enabled || ghost, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AccType.button, color = fg, maxLines = 1)
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, ghost: Boolean = false) {
    val c = Acc.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (ghost) Color.Transparent else c.acc)
            .then(if (ghost) Modifier.border(1.dp, c.line, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AccType.title, color = if (ghost) c.ink else c.onAcc, maxLines = 1)
    }
}

enum class BadgeState { Done, Current, Off }

@Composable
fun StepBadge(state: BadgeState, number: Int) {
    val c = Acc.colors
    val ring = if (state == BadgeState.Off) c.line else c.acc
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (state == BadgeState.Done) c.acc else Color.Transparent)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (state == BadgeState.Done) {
            Canvas(Modifier.size(14.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.74f)
                    lineTo(size.width * 0.8f, size.height * 0.3f)
                }
                drawPath(p, c.onAcc, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        } else {
            Text("$number", style = AccType.smallSemi, color = if (state == BadgeState.Current) c.acc else c.mute)
        }
    }
}

@Composable
fun Tag(text: String) {
    val c = Acc.colors
    Text(
        text,
        style = AccType.small,
        color = c.mute,
        modifier = Modifier
            .border(1.dp, c.line, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = Acc.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(shape)
                    .background(if (on) c.surface else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, c.line, shape) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (on) AccType.smallSemi else AccType.small,
                    color = if (on) c.ink else c.mute,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Full-screen page with a back link and a large title. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Acc.colors
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        Text(
            "Back",
            style = AccType.bodyMedium,
            color = c.acc,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onBack).padding(vertical = 10.dp, horizontal = 4.dp),
        )
        Text(title, style = AccType.h1, color = c.ink, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        content()
    }
}
