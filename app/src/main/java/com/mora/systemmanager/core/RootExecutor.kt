package com.mora.systemmanager.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RootExecutor : CommandExecutor {
    override val label = "Root (su)"

    override suspend fun execute(command: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            // Commands are passed as a single quoted script to su; never concatenate UI text.
            val script = command.joinToString(" ") { shellQuote(it) }
            val process = ProcessBuilder("su", "-c", script).start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext CommandResult(124, stdout, "Command timed out")
            }
            CommandResult(process.exitValue(), stdout, stderr)
        }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
