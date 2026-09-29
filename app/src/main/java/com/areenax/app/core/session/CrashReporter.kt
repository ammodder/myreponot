package com.areenax.app.core.session

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A13-02 — local crash capture (allowlist-clean: zero new dependencies, no
 * network). An uncaught exception is written to `filesDir/crash/last_crash.txt`
 * (timestamp + stack trace; no user data is added), then the PREVIOUS default
 * handler still runs so the process dies exactly as the platform expects.
 *
 * AREENAX distributes as a web-APK (audit A11-01 Path A) — Google Play vitals
 * do not exist for this channel, so this file is the only field-failure
 * signal. The Areenax Profile screen offers "Share crash logs" when the file
 * exists (user consents by sending — nothing leaves the device uninvited).
 *
 * The file is excluded from cloud backup / device transfer
 * (res/xml/backup_rules.xml + data_extraction_rules.xml — A6-02).
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"
    private const val MAX_BYTES = 256 * 1024 // keep the artifact small

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun crashFile(context: Context): File = File(File(context.applicationContext.filesDir, DIR), FILE)

    fun hasCrashLog(context: Context): Boolean = crashFile(context).let { it.exists() && it.length() > 0 }

    /** Removes the stored crash report (after the user chose not to share it). */
    fun clear(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.applicationContext.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        var body = "AREENAX crash — $stamp (thread: ${thread.name})\n$stack\n"
        if (body.length > MAX_BYTES) body = body.take(MAX_BYTES)
        File(dir, FILE).writeText(body)
    }
}
