package com.mora.connector.sdk

/** Wire contract shared by Mora Shield and every companion APK. Keep field names stable within API v1. */
object MoraConnectorContract {
    const val API_VERSION = 1
    const val HOST_PACKAGE = "com.mora.shield"
    const val HOST_SERVICE_CLASS = "com.mora.shield.connector.MoraConnectorService"
    const val ACTION_BIND = "com.mora.shield.action.CONNECTOR"
    const val CONNECTOR_PERMISSION = "com.mora.shield.permission.CONNECTOR"

    const val MSG_REGISTER = 1
    const val MSG_REGISTERED = 2
    const val MSG_HEARTBEAT = 3
    const val MSG_HEARTBEAT_ACK = 4
    const val MSG_PUBLISH_EVENT = 5
    const val MSG_EVENT_ACK = 6
    const val MSG_GET_POLICY = 7
    const val MSG_POLICY = 8
    const val MSG_COMMAND = 9
    const val MSG_COMMAND_RESULT = 10
    const val MSG_COMMAND_RESULT_ACK = 11
    const val MSG_DISCONNECT = 12
    const val MSG_DISCONNECTED = 13
    const val MSG_ERROR = 99

    const val KEY_API_VERSION = "api_version"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_OK = "ok"
    const val KEY_SUMMARY = "summary"
    const val KEY_HOST_PACKAGE = "host_package"
    const val KEY_SESSION_TOKEN = "session_token"
    const val KEY_SERVER_TIME_MS = "server_time_ms"

    const val KEY_EVENT_ID = "event_id"
    const val KEY_EVENT_TYPE = "event_type"
    const val KEY_TITLE = "title"
    const val KEY_AFFECTED_PACKAGE = "affected_package"
    const val KEY_DETAIL = "detail"
    const val KEY_SEVERITY = "severity"
    const val KEY_OCCURRED_AT_MS = "occurred_at_ms"

    const val KEY_POLICY_ENABLED = "policy_enabled"
    const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    const val KEY_NETWORK_MODE = "network_mode"
    const val KEY_BACKGROUND_MODE = "background_mode"
    const val KEY_POLICY_UPDATED_AT = "policy_updated_at"

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_COMMAND_ID = "command_id"
    const val KEY_PAYLOAD = "payload"
    const val KEY_SUCCESS = "success"
}
