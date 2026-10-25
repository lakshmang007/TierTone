package app.tiertone.service

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * Receives taps on action buttons ("Reply", "Mark as read") of TierTone's copies, forwards them
 * to the source app's own action (including any typed reply), then removes TierTone's copy.
 */
class ActionRelay : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val target = IntentCompat.getParcelableExtra(intent, EXTRA_TARGET, PendingIntent::class.java) ?: return
        val tag = intent.getStringExtra(EXTRA_TAG)

        val fillIn = Intent()
        val results = RemoteInput.getResultsFromIntent(intent)
        val inputs = IntentCompat.getParcelableArrayExtra(intent, EXTRA_INPUTS, RemoteInput::class.java)
            ?.filterIsInstance<RemoteInput>()?.toTypedArray()
        if (results != null && !inputs.isNullOrEmpty()) {
            RemoteInput.addResultsToIntent(inputs, fillIn, results)
            if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(fillIn, RemoteInput.getResultsSource(intent))
        }

        try {
            target.send(context, 0, fillIn, null, null, null, activityOptions())
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "The app's action is no longer valid", e)
        }
        // The app acted on its own notification, which TierTone already replaced, so remove the copy.
        if (tag != null) context.getSystemService(NotificationManager::class.java).cancel(tag, Reposter.CHILD_ID)
    }

    /** Lets an action that opens the app do so: the user just tapped it. */
    private fun activityOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            .toBundle()
        Build.VERSION.SDK_INT >= 34 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            .toBundle()
        else -> null
    }

    companion object {
        const val EXTRA_TARGET = "app.tiertone.target"
        const val EXTRA_TAG = "app.tiertone.tag"
        const val EXTRA_INPUTS = "app.tiertone.inputs"
        private const val TAG = "TierTone"
    }
}
