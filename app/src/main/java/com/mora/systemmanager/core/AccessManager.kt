package com.mora.systemmanager.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class AccessManager {
    fun hasShizukuPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun shizukuAvailable(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun requestShizukuPermission(requestCode: Int) {
        if (shizukuAvailable() && !hasShizukuPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun preferredExecutor(): CommandExecutor? = when {
        hasRootBinary() -> RootExecutor()
        hasShizukuPermission() -> ShizukuExecutor()
        else -> null
    }

    private fun hasRootBinary(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val completed = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Throwable) { false }
}
