package com.oxygen.ai.diagnostics

import com.oxygen.ai.core.device.DeviceProfiler
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.data.db.OxygenDatabase
import com.oxygen.ai.inference.LlamaCppRuntime
import com.oxygen.ai.models.ModelManager
import com.oxygen.ai.settings.SettingsRepository
import java.io.File

data class DiagnosticsSnapshot(
    val fields: Map<String, String>,
    val logs: List<OxygenLog.LogLine>,
)

class DiagnosticsCollector(
    private val profiler: DeviceProfiler,
    private val runtime: LlamaCppRuntime,
    private val models: ModelManager,
    private val settings: SettingsRepository,
    private val filesDir: File,
    private val db: OxygenDatabase,
) {
    fun capture(): DiagnosticsSnapshot {
        val device = profiler.snapshot()
        val status = runtime.status()
        val model = models.activeProfile()
        val dbFile = filesDir.parentFile?.let { File(it, "databases/oxygen.db") }
        val fields = linkedMapOf(
            "Device" to "${device.manufacturer} ${device.model}",
            "ABI" to device.abi,
            "SDK" to device.sdk.toString(),
            "CPU cores" to device.cpuCores.toString(),
            "RAM" to formatBytes(device.totalRamBytes) + " / avail " + formatBytes(device.availRamBytes),
            "Storage" to formatBytes(device.availStorageBytes) + " free",
            "Battery saver" to device.batterySaver.toString(),
            "Thermal" to device.thermalStatus.toString(),
            "Backend" to status.backend,
            "Native available" to status.available.toString(),
            "Model" to (model?.displayName ?: "none"),
            "Model size" to (model?.fileSize?.let { formatBytes(it) } ?: "-"),
            "Context" to status.contextSize.toString(),
            "Threads" to status.threads.toString(),
            "Reasoning" to settings.reasoningLevel().name,
            "Performance" to settings.performance().name,
            "Drive mode" to settings.driveMode().name,
            "Local only" to settings.localOnly().toString(),
            "Database" to (dbFile?.takeIf { it.exists() }?.length()?.let { formatBytes(it) } ?: "n/a"),
            "Models dir" to formatBytes(dirSize(models.modelsDir())),
        )
        return DiagnosticsSnapshot(fields, OxygenLog.snapshot())
    }

    private fun formatBytes(v: Long): String {
        if (v < 1024) return "$v B"
        val kb = v / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
