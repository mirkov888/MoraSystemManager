package com.mora.systemmanager.core

interface CommandExecutor {
    suspend fun execute(command: List<String>): CommandResult
    val label: String
}
