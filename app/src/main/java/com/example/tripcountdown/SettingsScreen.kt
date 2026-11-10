package com.example.tripcountdown

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .cardShadow()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun OptionRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .border(1.dp, Color.Black, shape)
    ) {
        options.forEachIndexed { i, label ->
            if (i > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (i == selected) Color(0xFF1565C0) else Color.White)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (i == selected) Color.White else Color(0xFF1B1F24)
                )
            }
        }
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { v -> onChange(v) })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val versionText = remember {
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "Travel Tracker version " + (info.versionName ?: "?") + " (build " + code + ")"
        } catch (e: Exception) {
            "Travel Tracker"
        }
    }
    var enabled by remember { mutableStateOf(NotifSettings.getBool(ctx, "enabled", true)) }
    var early by remember { mutableStateOf(NotifSettings.getInt(ctx, "earlyMonths", 1)) }
    var finalStep by remember { mutableStateOf(NotifSettings.getInt(ctx, "finalStep", 7)) }
    val todoDefault = NotifSettings.getBool(ctx, "todo", true)
    var todo7 by remember { mutableStateOf(NotifSettings.getBool(ctx, "todo7", todoDefault)) }
    var todo1 by remember { mutableStateOf(NotifSettings.getBool(ctx, "todo1", todoDefault)) }
    var todo0 by remember { mutableStateOf(NotifSettings.getBool(ctx, "todo0", todoDefault)) }
    var allowed by remember { mutableStateOf(Reminders.canNotify(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = Reminders.canNotify(ctx)
    }
    val earlyValues = listOf(1, 2, 3, 0)
    val finalValues = listOf(1, 3, 7, 0)
    val dim = if (enabled) 1f else 0.5f

    Column(Modifier.fillMaxSize().background(Color(0xFFE8EEF4))) {
        AppBar(title = "Settings", backLabel = "Back", onBack = onBack, showGear = false)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Turn all reminders on or off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF455A64)
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { v ->
                            enabled = v
                            NotifSettings.setBool(ctx, "enabled", v)
                            if (v && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) {
                                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
                if (enabled && !allowed) {
                    Text(
                        "Notifications are blocked for this app in your phone's settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB3261E)
                    )
                    FieldButton(onClick = {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Open phone settings") }
                }
            }
            SettingsCard(Modifier.alpha(dim)) {
                Text("Trip countdown reminders", style = MaterialTheme.typography.titleMedium)
                Text("Before the final month", style = MaterialTheme.typography.bodyMedium)
                OptionRow(
                    listOf("Monthly", "2 months", "3 months", "Off"),
                    earlyValues.indexOf(early).coerceAtLeast(0)
                ) { i ->
                    early = earlyValues[i]
                    NotifSettings.setInt(ctx, "earlyMonths", early)
                }
                Text("During the final month", style = MaterialTheme.typography.bodyMedium)
                OptionRow(
                    listOf("Daily", "3 days", "Weekly", "Off"),
                    finalValues.indexOf(finalStep).coerceAtLeast(0)
                ) { i ->
                    finalStep = finalValues[i]
                    NotifSettings.setInt(ctx, "finalStep", finalStep)
                }
                Text(
                    "Example: for a trip on Nov 1, Monthly and Weekly remind you on Oct 1, Sep 1 and so on, then on Oct 4, 11, 18 and 25.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF455A64)
                )
            }
            SettingsCard(Modifier.alpha(dim)) {
                Text("To-do reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose when to be reminded about a to-do that has a due date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF455A64)
                )
                CheckRow("1 week before it's due", todo7) { v ->
                    todo7 = v
                    NotifSettings.setBool(ctx, "todo7", v)
                }
                CheckRow("1 day before it's due", todo1) { v ->
                    todo1 = v
                    NotifSettings.setBool(ctx, "todo1", v)
                }
                CheckRow("On the day it's due", todo0) { v ->
                    todo0 = v
                    NotifSettings.setBool(ctx, "todo0", v)
                }
            }
            FieldButton(onClick = {
                allowed = Reminders.canNotify(ctx)
                Reminders.sendTest(ctx)
            }) { Text("Send a test notification") }
            Text(
                versionText,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF455A64),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }
    }
}
