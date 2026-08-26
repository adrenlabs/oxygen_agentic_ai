package com.oxygen.ai.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import kotlin.math.max

enum class PerformanceProfile { ECO, BALANCED, PERFORMANCE, CUSTOM }

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val abi: String,
    val sdk: Int,
    val cpuCores: Int,
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val totalStorageBytes: Long,
    val availStorageBytes: Long,
    val batterySaver: Boolean,
    val thermalStatus: Int,
)

data class RuntimeTuning(
    val profile: PerformanceProfile,
    val threads: Int,
    val contextSize: Int,
    val batchSize: Int,
    val mmap: Boolean,
    val gpuLayers: Int,
    val generationBudget: Int,
)

class DeviceProfiler(private val context: Context) {
    fun snapshot(): DeviceSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val data = StatFs(context.filesDir.absolutePath)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else -1
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            sdk = Build.VERSION.SDK_INT,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamBytes = mem.totalMem,
            availRamBytes = mem.availMem,
            totalStorageBytes = data.totalBytes,
            availStorageBytes = data.availableBytes,
            batterySaver = pm.isPowerSaveMode,
            thermalStatus = thermal,
        )
    }

    fun recommend(preferred: PerformanceProfile = PerformanceProfile.BALANCED): RuntimeTuning {
        val snap = snapshot()
        val ramGb = snap.totalRamBytes / (1024.0 * 1024.0 * 1024.0)
        val profile = when {
            preferred == PerformanceProfile.CUSTOM -> preferred
            snap.batterySaver || ramGb < 4.0 -> PerformanceProfile.ECO
            ramGb >= 10.0 && preferred == PerformanceProfile.PERFORMANCE -> PerformanceProfile.PERFORMANCE
            else -> preferred
        }
        val cores = max(1, snap.cpuCores)
        return when (profile) {
            PerformanceProfile.ECO -> RuntimeTuning(
                profile, threads = max(1, cores / 2), contextSize = 4096,
                batchSize = 128, mmap = true, gpuLayers = 0, generationBudget = 256,
            )
            PerformanceProfile.BALANCED -> RuntimeTuning(
                profile, threads = max(2, cores - 1), contextSize = if (ramGb >= 8) 8192 else 4096,
                batchSize = 256, mmap = true, gpuLayers = 0, generationBudget = 512,
            )
            PerformanceProfile.PERFORMANCE -> RuntimeTuning(
                profile, threads = cores, contextSize = if (ramGb >= 12) 16384 else 8192,
                batchSize = 512, mmap = true, gpuLayers = 0, generationBudget = 1024,
            )
            PerformanceProfile.CUSTOM -> RuntimeTuning(
                profile, threads = max(2, cores - 1), contextSize = 8192,
                batchSize = 256, mmap = true, gpuLayers = 0, generationBudget = 512,
            )
        }
    }
}
