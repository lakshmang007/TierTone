package app.tiertone.ui

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tiertone.core.Entry
import app.tiertone.core.Lane
import java.time.LocalDate
import java.util.Date

@Composable
fun HomeScreen(model: AppModel, openApps: () -> Unit, openVips: () -> Unit) {
    val context = LocalContext.current
    val c = model.config
    val access = model.access
    val recent = model.stats.recent.take(15)
    // Ask directly; once Android stops showing the dialog (denied twice), send the user to settings.
    val askPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.refresh()
        if (!granted) SystemScreens.appNotifications(context)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("TierTone", "Hear who matters.") }
        item { StatusCard(model) }
        if (access.listener && !access.post) {
            item {
                WarningCard(
                    title = "TierTone can't show notifications",
                    body = "It needs this to re-post notifications with their tier's sound. Until then, everything is left untouched.",
                    action = "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) askPost.launch(Manifest.permission.POST_NOTIFICATIONS) else SystemScreens.appNotifications(context)
                }
            }
        }
        if (c.appTiers.isEmpty() && c.vips.isEmpty()) {
            item {
                TierCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Start with two taps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Mark a noisy app Muted, an essential one Important, and add the people you never want to miss.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = openApps) { Text("Sort apps") }
                        Button(
                            onClick = openVips,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) { Text("Add VIPs") }
                    }
                }
            }
        }
        item { WeekCard(model) }
        item {
            Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Kept on this phone only. Never includes message text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recent.isEmpty()) {
            item {
                Text(
                    "Nothing yet. New notifications will appear here as they're sorted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(recent) { RecentRow(it, model.now) }
    }
}

@Composable
private fun StatusCard(model: AppModel) {
    val context = LocalContext.current
    val c = model.config
    val now = model.now
    when {
        !model.access.listener -> WarningCard(
            title = "Notification access is off",
            body = "TierTone can't see notifications, so every app is using its own sound.",
            action = "Turn on access",
        ) { SystemScreens.notificationAccess(context) }

        else -> TierCard(container = MaterialTheme.colorScheme.surfaceContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val title = when {
                        !c.enabled -> "TierTone is off"
                        c.isPaused(now) -> "Paused until ${DateFormat.getTimeFormat(context).format(Date(c.pausedUntil))}"
                        else -> "Sorting is on"
                    }
                    val subtitle = when {
                        !c.enabled -> "Every app is using its own sound."
                        c.isPaused(now) -> "Apps keep their own sounds until then."
                        else -> "${plural(c.appTiers.size, "app")} sorted · ${plural(c.vips.size, "VIP")}"
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = c.enabled, onCheckedChange = model::setEnabled)
            }
            if (c.enabled) {
                if (c.isPaused(now)) {
                    FilledTonalButton(onClick = model::resume) { Text("Resume now") }
                } else {
                    PauseChips(model)
                }
            }
        }
    }
}

@Composable
fun PauseChips(model: AppModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Pause", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AssistChip(onClick = { model.pauseFor(30) }, label = { Text("30 min") })
        AssistChip(onClick = { model.pauseFor(60) }, label = { Text("1 hour") })
        AssistChip(onClick = { model.pauseFor(8 * 60) }, label = { Text("8 hours") })
    }
}

@Composable
private fun WeekCard(model: AppModel) {
    val today = LocalDate.now().toEpochDay()
    val counts = remember(model.stats, today) { model.stats.lastDays(today) }
    val vipSince = model.config.vips.minOfOrNull { it.addedAt }?.takeIf { it > 0 }
    TierCard {
        Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(Lane.VIP, Lane.IMPORTANT, Lane.NORMAL, Lane.MUTED).forEach { lane ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LaneIcon(lane, 40.dp)
                    Text(
                        counts[lane.ordinal].toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(lane.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val untouched = counts[Lane.UNTOUCHED.ordinal]
        if (untouched > 0) {
            Text(
                "$untouched from apps you haven't listed kept their own sound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (vipSince != null) {
            val days = ((model.now - vipSince) / DateUtils.DAY_IN_MILLIS).toInt()
            Text(
                "VIP rules on for ${if (days < 1) "less than a day" else plural(days, "day")}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(entry: Entry, now: Long) {
    val context = LocalContext.current
    val label = remember(entry.pkg) { appLabel(context, entry.pkg) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(entry.pkg, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            LaneBadge(entry.lane, if (entry.lane == Lane.VIP && entry.vip != null) "VIP · ${entry.vip}" else entry.lane.label)
        }
        Text(
            if (now - entry.time < DateUtils.MINUTE_IN_MILLIS) {
                "Just now"
            } else {
                DateUtils.getRelativeTimeSpanString(entry.time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WarningCard(title: String, body: String, action: String, onAction: () -> Unit) {
    TierCard(container = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onAction) { Text(action) }
    }
}

fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
