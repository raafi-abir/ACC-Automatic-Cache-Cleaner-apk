package io.github.raafiabir.acc.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import io.github.raafiabir.acc.AppEntry

@Composable
fun ExclusionsScreen(
    apps: List<AppEntry>,
    excluded: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    // Filter ignores `excluded` on purpose so rows don't jump when you tick one.
    val shown = remember(apps, query, showSystem) {
        apps.filter { a ->
            (showSystem || !a.system) &&
                (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Excluded apps", style = MaterialTheme.typography.titleLarge)
            Text(
                "Done",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        Text(
            "Their cache and folders are left alone by every clean.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = c.onBackground,
                unfocusedIndicatorColor = c.outlineVariant,
                cursorColor = c.onBackground,
            ),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showSystem = !showSystem }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show system apps", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = showSystem,
                onCheckedChange = { showSystem = it },
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
        HorizontalDivider(color = c.outlineVariant)

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(app.pkg) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            app.pkg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Checkbox(
                        checked = app.pkg in excluded,
                        onCheckedChange = { onToggle(app.pkg) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = c.onBackground,
                            checkmarkColor = c.background,
                            uncheckedColor = c.outline,
                        ),
                    )
                }
            }
        }
    }
}
