package com.mora.systemmanager.connector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.mora.connector.sdk.MoraConnectorContract as C
import com.mora.systemmanager.core.CommandExecutor
import com.mora.systemmanager.core.CommandResult

data class MoraPolicySnapshot(
    val enabled: Boolean = true,
    val monitoringEnabled: Boolean = true,
    val networkMode: String = "inherit",
    val backgroundMode: String = "normal",
    val updatedAtMs: Long = 0L
)

/** Authenticated Messenger bridge to Mora Shield. Last known policy is cached locally. */
class MoraPolicyBridge(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("mora_central_policy", Context.MODE_PRIVATE)
    val policy: MutableState<MoraPolicySnapshot> = mutableStateOf(readCache())
    val connection: MutableState<String> = mutableStateOf("Belum terhubung")
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var remote: Messenger? = null
    @Volatile private var bound = false
    @Volatile private var registered = false
    @Volatile private var sessionToken: String? = null

    private val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val senderPackages = try { app.packageManager.getPackagesForUid(message.sendingUid)?.toSet().orEmpty() }
                catch (_: Exception) { emptySet() }
            if (C.HOST_PACKAGE !in senderPackages) return
            val data = message.data ?: Bundle()
            when (message.what) {
                C.MSG_REGISTERED -> {
                    val token = data.getString(C.KEY_SESSION_TOKEN).orEmpty()
                    registered = data.getBoolean(C.KEY_OK, false) && token.length in 32..64
                    sessionToken = token.takeIf { registered }
                    connection.value = if (registered) "Terhubung ke Mora Shield" else
                        data.getString(C.KEY_SUMMARY) ?: "Registrasi ditolak"
                    if (registered) {
                        requestPolicy()
                        handler.removeCallbacks(heartbeatTask)
                        handler.removeCallbacks(policyTask)
                        handler.postDelayed(heartbeatTask, 30_000L)
                        handler.postDelayed(policyTask, 60_000L)
                    }
                }
                C.MSG_POLICY -> {
                    if (data.getBoolean(C.KEY_OK, false)) {
                        val next = MoraPolicySnapshot(
                            enabled = data.getBoolean(C.KEY_POLICY_ENABLED, true),
                            monitoringEnabled = data.getBoolean(C.KEY_MONITORING_ENABLED, true),
                            networkMode = data.getString(C.KEY_NETWORK_MODE) ?: "inherit",
                            backgroundMode = data.getString(C.KEY_BACKGROUND_MODE) ?: "normal",
                            updatedAtMs = data.getLong(C.KEY_POLICY_UPDATED_AT, System.currentTimeMillis())
                        )
                        policy.value = next
                        prefs.edit()
                            .putBoolean("enabled", next.enabled)
                            .putBoolean("monitoring_enabled", next.monitoringEnabled)
                            .putString("network_mode", next.networkMode)
                            .putString("background_mode", next.backgroundMode)
                            .putLong("updated_at_ms", next.updatedAtMs)
                            .apply()
                        connection.value = "Terhubung · kebijakan diperbarui"
                    } else {
                        connection.value = "Terhubung · kebijakan belum tersedia"
                    }
                }
                C.MSG_HEARTBEAT_ACK -> {
                    if (!data.getBoolean(C.KEY_OK, false)) disconnectState("Heartbeat ditolak")
                }
                C.MSG_ERROR, C.MSG_DISCONNECTED -> disconnectState(
                    data.getString(C.KEY_SUMMARY) ?: "Koneksi terputus"
                )
            }
        }
    })

    private val heartbeatTask = object : Runnable {
        override fun run() {
            if (!registered) return
            send(C.MSG_HEARTBEAT)
            handler.postDelayed(this, 30_000L)
        }
    }
    private val policyTask = object : Runnable {
        override fun run() {
            if (!registered) return
            requestPolicy()
            handler.postDelayed(this, 60_000L)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) {
                connection.value = "Binder Mora Shield kosong"
                return
            }
            remote = Messenger(service)
            val data = Bundle().apply {
                putInt(C.KEY_API_VERSION, C.API_VERSION)
                putString("connector_id", "system-manager")
                putStringArrayList(C.KEY_CAPABILITIES, arrayListOf("events.publish", "policy.read"))
            }
            val message = Message.obtain(null, C.MSG_REGISTER).apply {
                this.data = data
                replyTo = incoming
            }
            try {
                remote?.send(message)
                connection.value = "Mengautentikasi…"
            } catch (_: RemoteException) {
                connection.value = "Gagal mengirim registrasi"
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) = disconnectState("Mora Shield terputus")
        override fun onBindingDied(name: ComponentName?) {
            disconnectState("Binding Mora Shield berakhir")
            runCatching { app.unbindService(this) }
            bound = false
        }
        override fun onNullBinding(name: ComponentName?) = disconnectState("Mora Shield menolak binding")
    }

    fun connect() {
        if (bound) return
        val intent = Intent(C.ACTION_BIND).setComponent(
            ComponentName(C.HOST_PACKAGE, C.HOST_SERVICE_CLASS)
        )
        try {
            bound = app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) connection.value = "Mora Shield tidak ditemukan"
        } catch (_: SecurityException) {
            connection.value = "Izin Connector ditolak · periksa signing key"
        } catch (_: Exception) {
            connection.value = "Gagal menghubungkan ke Mora Shield"
        }
    }

    fun close() {
        if (registered) send(C.MSG_DISCONNECT)
        registered = false
        sessionToken = null
        handler.removeCallbacks(heartbeatTask)
        handler.removeCallbacks(policyTask)
        remote = null
        if (bound) runCatching { app.unbindService(serviceConnection) }
        bound = false
        connection.value = "Tidak terhubung · memakai cache"
    }

    fun guarded(delegate: CommandExecutor): CommandExecutor = object : CommandExecutor {
        override val label: String = delegate.label
        override suspend fun execute(command: List<String>): CommandResult {
            if (!policy.value.enabled) {
                return CommandResult(126, "", "Ditolak oleh kebijakan pusat Mora Shield: System Manager dinonaktifkan.")
            }
            val result = delegate.execute(command)
            if (policy.value.monitoringEnabled && registered) {
                val operation = command.firstOrNull()?.take(40) ?: "system"
                val affected = command.lastOrNull()?.takeIf {
                    it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
                }.orEmpty()
                publishEvent(
                    title = if (result.success) "Aksi sistem selesai" else "Aksi sistem gagal",
                    detail = "Operasi terprivilege: $operation",
                    affectedPackage = affected
                )
            }
            return result
        }
    }

    private fun requestPolicy() = send(C.MSG_GET_POLICY)

    private fun publishEvent(title: String, detail: String, affectedPackage: String) {
        val data = baseBundle().apply {
            putString(C.KEY_EVENT_ID, "system-manager-${System.currentTimeMillis()}")
            putString(C.KEY_EVENT_TYPE, "PRIVILEGED_ACTION")
            putString(C.KEY_TITLE, title)
            putString(C.KEY_AFFECTED_PACKAGE, affectedPackage)
            putString(C.KEY_DETAIL, detail)
            putString(C.KEY_SEVERITY, "info")
            putLong(C.KEY_OCCURRED_AT_MS, System.currentTimeMillis())
        }
        send(C.MSG_PUBLISH_EVENT, data)
    }

    private fun send(what: Int, data: Bundle = baseBundle()): Boolean {
        val target = remote ?: return false
        return try {
            target.send(Message.obtain(null, what).apply {
                this.data = data
                replyTo = incoming
            })
            true
        } catch (_: RemoteException) {
            disconnectState("Koneksi IPC gagal")
            false
        }
    }

    private fun baseBundle() = Bundle().apply {
        putInt(C.KEY_API_VERSION, C.API_VERSION)
        sessionToken?.let { putString(C.KEY_SESSION_TOKEN, it) }
    }

    private fun disconnectState(reason: String) {
        registered = false
        sessionToken = null
        handler.removeCallbacks(heartbeatTask)
        handler.removeCallbacks(policyTask)
        connection.value = "$reason · memakai cache"
    }

    private fun readCache() = MoraPolicySnapshot(
        enabled = prefs.getBoolean("enabled", true),
        monitoringEnabled = prefs.getBoolean("monitoring_enabled", true),
        networkMode = prefs.getString("network_mode", "inherit") ?: "inherit",
        backgroundMode = prefs.getString("background_mode", "normal") ?: "normal",
        updatedAtMs = prefs.getLong("updated_at_ms", 0L)
    )
}
