package com.mora.systemmanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mora.systemmanager.core.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val uid: Int,
    val apkPath: String,
    val isSystem: Boolean,
    val enabled: Boolean
)

data class OperationResult(val success: Boolean, val message: String)

class PackageRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    @Suppress("DEPRECATION")
    fun installedApps(): List<AppEntry> {
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        return apps.mapNotNull { info ->
            try {
                val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(info.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(info.packageName, 0)
                }
                val label = pm.getApplicationLabel(info).toString().ifBlank { info.packageName }
                AppEntry(
                    label = label,
                    packageName = info.packageName,
                    versionName = pkgInfo.versionName ?: "Unknown",
                    versionCode = if (Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode else legacyVersionCode(pkgInfo),
                    uid = info.uid,
                    apkPath = info.sourceDir ?: "Unknown",
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    enabled = info.enabled
                )
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    suspend fun setEnabled(executor: CommandExecutor, app: AppEntry, enabled: Boolean): OperationResult {
        if (!validPackage(app.packageName)) return OperationResult(false, "Invalid package identifier.")
        val command = if (enabled) {
            listOf("pm", "enable", "--user", "0", app.packageName)
        } else {
            listOf("pm", "disable-user", "--user", "0", app.packageName)
        }
        return run(executor, command, if (enabled) "Enable" else "Disable")
    }

    suspend fun uninstallUserApp(executor: CommandExecutor, app: AppEntry): OperationResult {
        if (app.isSystem) return OperationResult(false, "System apps are protected from uninstall in Mora V2.")
        if (!validPackage(app.packageName)) return OperationResult(false, "Invalid package identifier.")
        return run(executor, listOf("pm", "uninstall", "--user", "0", app.packageName), "Uninstall")
    }

    private suspend fun run(
        executor: CommandExecutor,
        command: List<String>,
        operation: String
    ): OperationResult = withContext(Dispatchers.IO) {
        try {
            val result = executor.execute(command)
            val output = (result.stdout + "\n" + result.stderr).trim()
            OperationResult(result.success, if (output.isBlank()) {
                if (result.success) "$operation completed." else "$operation failed (exit ${result.exitCode})."
            } else output)
        } catch (t: Throwable) {
            OperationResult(false, "$operation failed: ${t.message ?: "unknown error"}")
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyVersionCode(info: android.content.pm.PackageInfo): Long = info.versionCode.toLong()

    private fun validPackage(value: String): Boolean =
        value.length in 3..255 &&
            value.split('.').size >= 2 &&
            value.split('.').all { part ->
                part.isNotEmpty() && part.first().isLetter() &&
                    part.all { it.isLetterOrDigit() || it == '_' }
            }
}
