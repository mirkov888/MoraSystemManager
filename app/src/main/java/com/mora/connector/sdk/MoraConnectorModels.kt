package com.mora.connector.sdk

import java.util.UUID

data class MoraConnectorEvent(
    val eventType: String,
    val title: String,
    val affectedPackage: String = "",
    val detail: String = "",
    val severity: String = "info",
    val occurredAtMs: Long = System.currentTimeMillis(),
    val eventId: String = UUID.randomUUID().toString()
)

data class MoraConnectorPolicy(
    val enabled: Boolean,
    val monitoringEnabled: Boolean,
    val networkMode: String,
    val backgroundMode: String,
    val updatedAtMs: Long
)

data class MoraConnectorCommand(
    val requestId: String,
    val commandId: String,
    val payload: String
)

data class MoraConnectorCommandResult(
    val requestId: String,
    val commandId: String,
    val success: Boolean,
    val summary: String
)

data class MoraConnectorResponse(
    val ok: Boolean,
    val summary: String
)

fun interface MoraConnectorCommandHandler {
    fun execute(command: MoraConnectorCommand): MoraConnectorCommandResult
}

interface MoraConnectorListener {
    fun onConnected() {}
    fun onDisconnected() {}
    fun onConnectionError(message: String) {}
    fun onEventAccepted(eventId: String, accepted: Boolean, summary: String) {}
    fun onPolicyReceived(policy: MoraConnectorPolicy?) {}
    fun onCommandSent(result: MoraConnectorResponse) {}
}
