package app.tiertone.core

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

/**
 * Rules live in a small JSON file. The UI process is the only writer; the listener process reads
 * it and reloads when the file changes or when the UI broadcasts [ACTION_CONFIG_CHANGED].
 */
class ConfigStore(private val context: Context) {
    private val base = File(context.filesDir, "rules.json")
    private val file = AtomicFile(base)

    fun load(): Config = try {
        ConfigJson.decode(file.readFully().decodeToString())
    } catch (_: FileNotFoundException) {
        Config()
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable rules file, using defaults", e)
        Config()
    }

    fun save(config: Config) {
        writeAtomically(file, ConfigJson.encode(config))
        context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(context.packageName))
    }

    /** Cheap change check for the listener: one stat() call. */
    fun stamp(): Long = base.lastModified()

    companion object {
        const val ACTION_CONFIG_CHANGED = "app.tiertone.CONFIG_CHANGED"
    }
}

/** Activity counts. The listener is the only writer; the UI only reads. */
class StatsStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "activity.json"))

    fun load(): Stats = try {
        Stats.decode(file.readFully().decodeToString())
    } catch (_: FileNotFoundException) {
        Stats()
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable activity file, starting fresh", e)
        Stats()
    }

    fun save(json: String) = writeAtomically(file, json)

    fun clear() = file.delete()
}

private const val TAG = "TierTone"

private fun writeAtomically(file: AtomicFile, text: String) {
    val out = file.startWrite()
    try {
        out.write(text.encodeToByteArray())
        file.finishWrite(out)
    } catch (e: Exception) {
        file.failWrite(out)
        throw e
    }
}
