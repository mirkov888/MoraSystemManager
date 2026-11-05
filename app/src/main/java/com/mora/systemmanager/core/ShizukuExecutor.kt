package com.mora.systemmanager.core

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShizukuExecutor : CommandExecutor {
    override val label = "Shizuku"

    override suspend fun execute(command: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val script = command.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            val process = Shizuku.newProcess(arrayOf("sh", "-c", script), null, null)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext CommandResult(124, stdout, "Command timed out")
            }
            CommandResult(process.exitValue(), stdout, stderr)
        }
}
