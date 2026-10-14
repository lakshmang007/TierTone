package app.tiertone.sender

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Posts chat notifications shaped like WhatsApp's: one MessagingStyle child per chat in a shared
 * group, a group summary, and Reply / Mark as read actions. A reply is echoed back as the user's
 * own message, the way chat apps update their notification after an inline reply.
 */
object Chats {
    const val TAG = "TierToneSender"
    private const val CHANNEL_LOUD = "chats"
    private const val CHANNEL_SILENT = "muted_chats"
    private const val GROUP = "messages"
    private const val SUMMARY_ID = 0
    const val KEY_REPLY = "reply"

    fun post(
        context: Context,
        from: String,
        text: String,
        chat: String?,
        silent: Boolean,
        fromSelf: Boolean = false,
        conversation: String = chat ?: from,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_LOUD, "Chats", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SILENT, "Muted chats", NotificationManager.IMPORTANCE_LOW))

        val id = conversation.hashCode() and 0x7fffffff
        val me = Person.Builder().setName("Me").setKey("me").build()
        val style = Notification.MessagingStyle(me)
        if (chat != null) {
            style.conversationTitle = chat
            style.isGroupConversation = true
        }
        val sender = if (fromSelf) null else Person.Builder().setName(from).setKey("k:$from").build()
        style.addMessage(Notification.MessagingStyle.Message(text, System.currentTimeMillis(), sender))

        val channel = if (silent) CHANNEL_SILENT else CHANNEL_LOUD
        val child = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setGroup(GROUP)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open(context, id))
            .addAction(replyAction(context, conversation, chat, id))
            .addAction(readAction(context, conversation, id))
            .build()
        val summary = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Test Sender")
            .setContentText("New messages")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        nm.notify(id, child)
        nm.notify(SUMMARY_ID, summary)
        Log.i(TAG, "posted id=$id from=$from chat=$chat silent=$silent self=$fromSelf")
    }

    private fun open(context: Context, id: Int): PendingIntent =
        PendingIntent.getActivity(
            context, id, Intent(context, PostActivity::class.java).putExtra("opened", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun replyAction(context: Context, conversation: String, chat: String?, id: Int): Notification.Action {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction("reply:$id")
            .putExtra("conversation", conversation)
            .putExtra("chat", chat)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, id, intent, flags)
        return Notification.Action.Builder(null, "Reply", pi)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build())
            .build()
    }

    private fun readAction(context: Context, conversation: String, id: Int): Notification.Action {
        val intent = Intent(context, ActionReceiver::class.java).setAction("read:$id").putExtra("conversation", conversation)
        val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, "Mark as read", pi).build()
    }
}

class PostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opened = intent.getIntExtra("opened", -1)
        if (opened >= 0) {
            Log.i(Chats.TAG, "opened chat id=$opened")
        } else {
            Chats.post(
                this,
                from = intent.getStringExtra("from") ?: "Mom",
                text = intent.getStringExtra("text") ?: "Hello from the test sender",
                chat = intent.getStringExtra("chat"),
                silent = intent.getBooleanExtra("silent", false),
            )
        }
        finish()
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val conversation = intent.getStringExtra("conversation") ?: return
        if (action.startsWith("reply:")) {
            val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Chats.KEY_REPLY)?.toString()
            Log.i(Chats.TAG, "got reply in $conversation: $reply")
            // Like WhatsApp: update the chat's notification with the user's own message.
            Chats.post(
                context, from = "Me", text = reply ?: "", chat = intent.getStringExtra("chat"),
                silent = false, fromSelf = true, conversation = conversation,
            )
        } else if (action.startsWith("read:")) {
            Log.i(Chats.TAG, "marked read: $conversation")
            context.getSystemService(NotificationManager::class.java).cancel(action.substringAfter(':').toInt())
        }
    }
}
