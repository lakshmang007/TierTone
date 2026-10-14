package app.tiertone.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tiertone.BuildConfig
import app.tiertone.core.Tier
import java.util.Date

@Composable
fun SettingsScreen(model: AppModel) {
    val context = LocalContext.current
    val c = model.config
    val access = model.access
    var pickSoundFor by remember { mutableStateOf<Tier?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Settings")

        SectionLabel("Sorting")
        SwitchRow(
            title = "TierTone is on",
            subtitle = "Off: every app goes back to its own sound, and TierTone stops running in the background.",
            checked = c.enabled,
            onChange = model::setEnabled,
        )
        if (c.enabled) {
            if (c.isPaused(model.now)) {
                ClickRow(
                    title = "Paused until ${DateFormat.getTimeFormat(context).format(Date(c.pausedUntil))}",
                    subtitle = "Apps keep their own sounds until then.",
                    trailing = { FilledTonalButton(onClick = model::resume) { Text("Resume") } },
                    onClick = null,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                PauseChips(model)
            }
        }
        SwitchRow(
            title = "Sort apps you haven't listed",
            subtitle = "On: they use the Normal sound. Off: they keep their own sound.",
            checked = c.sortUnlisted,
            onChange = { on -> model.update { it.copy(sortUnlisted = on) } },
        )

        SectionLabel("Sounds")
        ClickRow("Important", rememberSoundLabel(c.importantSound), onClick = { pickSoundFor = Tier.IMPORTANT })
        ClickRow("Normal", rememberSoundLabel(c.normalSound), onClick = { pickSoundFor = Tier.NORMAL })
        ClickRow("Muted", "Always silent", onClick = null)
        ClickRow(
            "Vibration, pop-ups and lock screen",
            "Fine-tune each tier in Android's notification settings",
            onClick = { SystemScreens.appNotifications(context) },
        )

        SectionLabel("Keep sorting reliable")
        ClickRow(
            "Notification access",
            if (access.listener) "On" else "Off: TierTone can't see notifications",
            trailing = { StatusIcon(access.listener) },
            onClick = { SystemScreens.notificationAccess(context) },
        )
        ClickRow(
            "Show notifications",
            if (access.post) "Allowed" else "Blocked: everything is left untouched",
            trailing = { StatusIcon(access.post) },
            onClick = { SystemScreens.appNotifications(context) },
        )
        ClickRow(
            "Battery optimization",
            "Samsung, Xiaomi, OnePlus and others may stop background apps. If sorting stops, exclude TierTone here.",
            onClick = { SystemScreens.battery(context) },
        )
        Text(
            "A short blip of an app's own sound before TierTone's is expected: the app's sound starts before TierTone can replace it. " +
                "On Android 15 and newer, turning off \"Notification cooldown\" keeps every sound at full volume.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        SectionLabel("Privacy")
        listOf(
            "No internet permission, so nothing can leave your phone.",
            "Reads the app, the sender's name and the notification only to sort and re-post it.",
            "Never stores message text. The activity list keeps only the app, tier and time.",
            "No contacts permission: you pick each VIP yourself.",
        ).forEach { line ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionLabel("Reset")
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Reset all rules") }

        SectionLabel("About")
        val crash = model.crashReport
        if (crash != null) {
            ClickRow(
                "Last crash report",
                "Saved on this phone. Share it with the developer if you like; it contains no messages.",
                onClick = null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, crash)
                    context.startActivity(Intent.createChooser(send, "Share crash report"))
                }) { Text("Share") }
                TextButton(onClick = model::clearCrashReport) { Text("Delete") }
            }
        }
        Text(
            "TierTone ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }

    pickSoundFor?.let { tier ->
        SoundPickerDialog(
            title = "${tier.label} sound",
            current = c.soundFor(tier),
            onDismiss = { pickSoundFor = null },
            onPick = { token ->
                model.update { if (tier == Tier.IMPORTANT) it.copy(importantSound = token) else it.copy(normalSound = token) }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all rules?") },
            text = { Text("This removes every app tier, every VIP and your sound choices. Apps go back to their own sounds.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    model.resetRules()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusIcon(ok: Boolean) {
    if (ok) {
        Icon(Icons.Filled.Check, contentDescription = "On", tint = MaterialTheme.colorScheme.primary)
    } else {
        Icon(Icons.Filled.Warning, contentDescription = "Needs attention", tint = MaterialTheme.colorScheme.error)
    }
}
