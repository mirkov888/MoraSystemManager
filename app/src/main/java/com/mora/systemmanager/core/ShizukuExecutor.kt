package com.mora.systemmanager.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Compatibility executor for Shizuku API 13.1.x.
 *
 * Shizuku 13.1.5 keeps the legacy newProcess implementation but marks it
 * private while preparing to remove it. Prefer a UserService-based executor
 * for a future release; this bridge avoids a compile-time call to that method.
 */
class ShizukuExecutor : CommandExecutor {
    override val label = "Shizuku"

    override suspend fun execute(command: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            if (!Shizuku.pingBinder()) {
                return@withContext CommandResult(1, "", "Shizuku service is not running")
            }
            val script = command.joinToString(" ") { shellQuote(it) }
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(
                    null,
                    arrayOf("sh", "-c", script),
                    null,
                    null
                ) as Process

                val finished = process.waitFor(20, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    return@withContext CommandResult(124, "", "Command timed out")
                }
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                CommandResult(process.exitValue(), stdout, stderr)
            } catch (e: Exception) {
                CommandResult(
                    126,
                    "",
                    "Shizuku legacy process bridge unavailable. Use Root access or migrate to UserService. ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                )
            }
        }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
