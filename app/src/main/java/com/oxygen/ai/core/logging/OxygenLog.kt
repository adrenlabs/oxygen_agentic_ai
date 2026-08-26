package com.oxygen.ai.core.logging

import android.util.Log
import com.oxygen.ai.security.SecretRedactor
import java.util.ArrayDeque

/**
 * Structured logger that never writes secrets. A bounded in-memory ring is
 * exposed on the diagnostics screen.
 */
object OxygenLog {
    private const val TAG = "OXYGEN"
    private const val CAPACITY = 400
    private val lock = Any()
    private val ring = ArrayDeque<LogLine>(CAPACITY)

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class LogLine(
        val timestampMs: Long,
        val level: Level,
        val topic: String,
        val message: String,
    )

    fun d(topic: String, message: String) = write(Level.DEBUG, topic, message)
    fun i(topic: String, message: String) = write(Level.INFO, topic, message)
    fun w(topic: String, message: String, error: Throwable? = null) =
        write(Level.WARN, topic, message + (error?.let { " :: ${it.javaClass.simpleName}" } ?: ""))

    fun e(topic: String, message: String, error: Throwable? = null) {
        val extra = error?.let { " :: ${it.javaClass.simpleName}: ${SecretRedactor.redact(it.message ?: "")}" } ?: ""
        write(Level.ERROR, topic, message + extra)
    }

    fun snapshot(): List<LogLine> = synchronized(lock) { ring.toList() }

    fun clear() = synchronized(lock) { ring.clear() }

    private fun write(level: Level, topic: String, message: String) {
        val safe = SecretRedactor.redact(message)
        val line = LogLine(System.currentTimeMillis(), level, topic, safe)
        synchronized(lock) {
            if (ring.size >= CAPACITY) ring.removeFirst()
            ring.addLast(line)
        }
        val text = "$topic | $safe"
        when (level) {
            Level.DEBUG -> Log.d(TAG, text)
            Level.INFO -> Log.i(TAG, text)
            Level.WARN -> Log.w(TAG, text)
            Level.ERROR -> Log.e(TAG, text)
        }
    }
}
