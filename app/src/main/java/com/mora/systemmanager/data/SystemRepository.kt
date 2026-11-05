package com.mora.systemmanager.data

import android.content.Context
import android.os.StatFs
import com.mora.systemmanager.core.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SystemSnapshot(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val storageTotalGb: Double,
    val storageFreeGb: Double,
    val uptime: String
)

class SystemRepository(private val context: Context) {
    fun snapshot(): SystemSnapshot {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo()
        activity.getMemoryInfo(memory)
        val stat = StatFs(context.filesDir.absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val uptimeSeconds = android.os.SystemClock.elapsedRealtime() / 1000
        val uptime = "${uptimeSeconds / 3600}h ${(uptimeSeconds % 3600) / 60}m"
        return SystemSnapshot(
            totalRamMb = memory.totalMem / 1024 / 1024,
            availableRamMb = memory.availMem / 1024 / 1024,
            storageTotalGb = total / 1_073_741_824.0,
            storageFreeGb = free / 1_073_741_824.0,
            uptime = uptime
        )
    }

    suspend fun packages(executor: CommandExecutor): List<String> = withContext(Dispatchers.IO) {
        val result = executor.execute(listOf("pm", "list", "packages", "-3"))
        if (!result.success) emptyList()
        else result.stdout.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .sorted()
            .toList()
    }
}
