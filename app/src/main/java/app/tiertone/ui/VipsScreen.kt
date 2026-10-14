package app.tiertone.ui

import android.content.ActivityNotFoundException
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tiertone.BuildConfig
import app.tiertone.core.Lane
import app.tiertone.core.Vip

@Composable
fun VipsScreen(model: AppModel) {
    val context = LocalContext.current
    val c = model.config
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (BuildConfig.DEBUG) Log.d("TierTone", "Contact picker returned $uri")
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = readPickedContact(context, uri)
        if (picked == null) {
            Toast.makeText(context, "Couldn't read that contact", Toast.LENGTH_SHORT).show()
        } else {
            editing = model.addVip(picked).id
        }
    }
    val pickContact = {
        try {
            picker.launch(Unit)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ScreenTitle("VIP contacts", "Their chats get their own sound, even if the app is Muted.") }
            item {
                TierCard {
                    SwitchRow(
                        title = "Ring through Silent & Do Not Disturb",
                        subtitle = "VIP messages still make a sound when your phone is silenced. They play at alarm volume.",
                        checked = c.vipBypassDnd,
                        onChange = { on -> model.update { it.copy(vipBypassDnd = on) } },
                    )
                    if (c.vipBypassDnd && !model.access.dnd) {
                        Text(
                            "Needs Do Not Disturb access, which Android usually grants with notification access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { SystemScreens.dndAccess(context) }) { Text("Allow Do Not Disturb access") }
                    }
                }
            }
            item {
                Text(
                    "Works with WhatsApp and Telegram for now. TierTone matches the name the chat app shows, " +
                        "or the phone number when the app shares it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (c.vips.isEmpty()) {
                item {
                    TierCard(container = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        LaneIcon(Lane.VIP, 48.dp)
                        Text("No VIPs yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add the people you never want to miss. You choose each contact; TierTone never reads your whole contact list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(c.vips, key = { it.id }) { vip -> VipRow(vip) { editing = vip.id } }
            item { Spacer(Modifier.height(88.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = pickContact,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add VIP") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    val vip = c.vips.firstOrNull { it.id == editing }
    if (vip != null) VipEditor(vip, model, onDismiss = { editing = null })
}

@Composable
private fun VipRow(vip: Vip, onClick: () -> Unit) {
    val sound = rememberSoundLabel(vip.sound)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(vip.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(vip.name, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(vip.phones.firstOrNull(), vip.aliases.takeIf { it.isNotEmpty() }?.joinToString(prefix = "also ")).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LaneBadge(Lane.VIP, sound)
    }
}

@Composable
private fun Avatar(name: String) {
    val color = laneColor(Lane.VIP)
    Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun VipEditor(vip: Vip, model: AppModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var aliases by remember(vip.id) { mutableStateOf(vip.aliases.joinToString(", ")) }
    var pickingSound by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val save = {
        val list = aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (list != vip.aliases) model.updateVip(vip.copy(aliases = list))
    }

    AlertDialog(
        onDismissRequest = {
            save()
            onDismiss()
        },
        title = { Text(vip.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vip.phones.isNotEmpty()) {
                    Text(vip.phones.joinToString("\n"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ClickRow(
                    title = "Sound",
                    subtitle = rememberSoundLabel(vip.sound),
                    trailing = {
                        IconButton(onClick = { SoundPreview.play(context, vip.sound) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play sound")
                        }
                    },
                    onClick = { pickingSound = true },
                )
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("Also match these names") },
                    supportingText = { Text("If a chat app shows them differently, e.g. their Telegram name. Separate with commas.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { confirmRemove = true }) {
                    Text("Remove VIP", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                save()
                onDismiss()
            }) { Text("Done") }
        },
    )

    if (pickingSound) {
        SoundPickerDialog(
            title = "Sound for ${vip.name}",
            current = vip.sound,
            onDismiss = { pickingSound = false },
            onPick = { token -> model.updateVip(vip.copy(sound = token)) },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${vip.name}?") },
            text = { Text("Their chats will follow the app's tier again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    model.removeVip(vip.id)
                    onDismiss()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
