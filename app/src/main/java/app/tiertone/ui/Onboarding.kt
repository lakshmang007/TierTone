package app.tiertone.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tiertone.core.Lane

private enum class Step { WELCOME, ACCESS, POST, DONE }

@Composable
fun Onboarding(model: AppModel) {
    val steps = buildList {
        add(Step.WELCOME)
        add(Step.ACCESS)
        if (Build.VERSION.SDK_INT >= 33) add(Step.POST)
        add(Step.DONE)
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val next = { index = (index + 1).coerceAtMost(steps.lastIndex) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (steps[index]) {
            Step.WELCOME -> WelcomeStep(next)
            Step.ACCESS -> AccessStep(model, next)
            Step.POST -> PostStep(model, next)
            Step.DONE -> DoneStep { model.update { it.copy(onboarded = true) } }
        }
    }
}

@Composable
private fun StepLayout(
    icon: ImageVector?,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            if (icon != null) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(primary) }
        if (secondary != null && onSecondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        }
    }
}

@Composable
private fun WelcomeStep(next: () -> Unit) {
    StepLayout(
        icon = null,
        title = "Hear who matters",
        body = "TierTone gives every notification a sound that tells you how much it matters, " +
            "before you even look at your phone.",
        primary = "Get started",
        onPrimary = next,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
            TierLine(Lane.VIP, "VIP contacts", "Their own sound, even when the app is muted")
            TierLine(Lane.IMPORTANT, "Important", "Apps you never want to miss")
            TierLine(Lane.NORMAL, "Normal", "Everything else, as usual")
            TierLine(Lane.MUTED, "Muted", "Still in your shade, never a sound")
        }
    }
}

@Composable
private fun TierLine(lane: Lane, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LaneIcon(lane, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessStep(model: AppModel, next: () -> Unit) {
    val context = LocalContext.current
    val granted = model.access.listener
    var showHelp by rememberSaveable { mutableStateOf(false) }
    StepLayout(
        icon = Icons.Filled.Lock,
        title = "Notification access",
        body = "To change a notification's sound, TierTone has to see it arrive. Android calls this notification access.",
        primary = if (granted) "Continue" else "Open notification access",
        onPrimary = { if (granted) next() else SystemScreens.notificationAccess(context) },
        secondary = if (granted) null else "The switch is greyed out?",
        onSecondary = { showHelp = !showHelp },
    ) {
        PrivacyList(
            title = "What TierTone reads",
            items = listOf(
                "Which app sent a notification",
                "The sender's name, to recognise your VIPs",
                "The notification itself, only so it can be re-posted with the right sound",
            ),
            positive = true,
        )
        PrivacyList(
            title = "What it never does",
            items = listOf(
                "Upload anything: it has no internet permission at all",
                "Keep message text: it is never saved or logged",
                "Read your contact list: you pick VIPs one at a time",
            ),
            positive = false,
        )
        Text(
            "Everything is processed on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF16A34A))
                Spacer(Modifier.width(8.dp))
                Text("Access granted", style = MaterialTheme.typography.titleMedium, color = Color(0xFF16A34A))
            }
        }
        if (showHelp && !granted) {
            TierCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "Android 13+ blocks this switch for apps installed outside the Play Store. " +
                        "Open App info, tap ⋮ in the top corner, choose \"Allow restricted settings\", then come back and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { SystemScreens.appDetails(context) }) { Text("Open App info") }
            }
        }
    }
}

@Composable
private fun PrivacyList(title: String, items: List<String>, positive: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        items.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (positive) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PostStep(model: AppModel, next: () -> Unit) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        model.refresh()
        if (ok) next() else denied = true
    }
    val granted = model.access.post
    StepLayout(
        icon = Icons.Filled.Notifications,
        title = "Let TierTone show notifications",
        body = "TierTone re-posts each sorted notification with its tier's sound, so it needs permission to show them. " +
            "Until it has it, TierTone leaves every notification untouched.",
        primary = when {
            granted -> "Continue"
            denied -> "Open settings"
            else -> "Allow"
        },
        onPrimary = {
            when {
                granted -> next()
                denied -> SystemScreens.appNotifications(context)
                Build.VERSION.SDK_INT >= 33 -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> next()
            }
        },
        secondary = if (granted) null else "Not now",
        onSecondary = next,
    )
}

@Composable
private fun DoneStep(finish: () -> Unit) {
    StepLayout(
        icon = Icons.Filled.Check,
        title = "You're set",
        body = "Nothing changes until you choose. Apps you haven't listed keep their own sound.",
        primary = "Start sorting",
        onPrimary = finish,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TierLine(Lane.IMPORTANT, "Pick your apps", "In Apps, mark what's Important and what's Muted")
            TierLine(Lane.VIP, "Add VIPs", "Choose people from your contacts and give each a sound")
            TierLine(Lane.NORMAL, "Pause anytime", "From Home, for 30 minutes, an hour or the night")
        }
    }
}
