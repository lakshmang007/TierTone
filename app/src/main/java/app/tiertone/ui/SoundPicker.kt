package app.tiertone.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import app.tiertone.core.SoundUris
import app.tiertone.core.Sounds

/** Human name for a sound token. May query the media store, so call it from `remember`. */
fun soundLabel(context: Context, token: String): String = Sounds.builtInLabel(token) ?: when (token) {
    Sounds.DEFAULT -> "Phone default"
    Sounds.SILENT -> "Silent"
    else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(token))?.getTitle(context) }.getOrNull() ?: "Custom sound"
}

@Composable
fun rememberSoundLabel(token: String): String {
    val context = LocalContext.current
    return remember(token) { soundLabel(context, token) }
}

/** Plays one preview at a time. */
object SoundPreview {
    private var current: Ringtone? = null

    fun play(context: Context, token: String) {
        stop()
        val uri = SoundUris.of(context, token) ?: return
        current = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }

    fun stop() {
        current?.stop()
        current = null
    }
}

/** Android's own notification-sound picker. Returns a sound token, or null if cancelled. */
class PickRingtone : ActivityResultContract<String, String?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, SoundUris.of(context, input))

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val uri = IntentCompat.getParcelableExtra(intent, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) ?: return null
        return if (uri == Settings.System.DEFAULT_NOTIFICATION_URI) Sounds.DEFAULT else uri.toString()
    }
}

@Composable
fun SoundPickerDialog(title: String, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    val phonePicker = rememberLauncherForActivityResult(PickRingtone()) { token ->
        if (token != null) {
            selected = token
            SoundPreview.play(context, token)
        }
    }
    DisposableEffect(Unit) { onDispose { SoundPreview.stop() } }

    val options = buildList {
        Sounds.BUILT_IN.forEach { add(Sounds.APP_PREFIX + it.first) }
        add(Sounds.DEFAULT)
        if (selected !in this) add(selected)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { token ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == token, role = Role.RadioButton) {
                                selected = token
                                SoundPreview.play(context, token)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == token, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(rememberSoundLabel(token))
                    }
                }
                TextButton(onClick = { phonePicker.launch(selected) }) { Text("Choose from phone sounds…") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(selected)
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
