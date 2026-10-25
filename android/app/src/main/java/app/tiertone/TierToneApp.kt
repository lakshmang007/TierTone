package app.tiertone

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/** Kept tiny: it runs in both the listener process and the UI process. */
class TierToneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/**
 * Basic, local-only crash reporting: the last crash's stack trace is saved on the device and the
 * user can choose to share it from Settings. Stack traces never include notification content.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    buildString {
                        appendLine("TierTone ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        val process = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else "?"
                        appendLine("Process: $process, thread: ${thread.name}")
                        appendLine("Time: ${System.currentTimeMillis()}")
                        appendLine()
                        append(error.stackTraceToString())
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
