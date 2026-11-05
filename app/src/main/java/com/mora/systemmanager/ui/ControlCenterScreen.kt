package com.mora.systemmanager.ui

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.systemmanager.core.CommandExecutor
import com.mora.systemmanager.core.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val protectedPackages = setOf(
    "android", "com.android.systemui", "com.android.settings",
    "com.android.phone", "com.android.server.telecom",
    "com.android.providers.settings", "com.android.providers.media",
    "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.android.permissioncontroller", "com.android.shell",
    "com.android.bluetooth", "com.android.nfc", "com.android.providers.telephony",
    "com.android.providers.contacts", "com.android.providers.downloads",
    "com.android.networkstack", "com.android.networkstack.tethering"
)

@Composable
fun ControlCenterScreen(executor: CommandExecutor?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf("Pilih fitur untuk mulai.") }
    var busy by remember { mutableStateOf(false) }
    var propertyName by remember { mutableStateOf("debug.example.value") }
    var propertyValue by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var showDebloatDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val battery = remember { batterySnapshot(context) }
    val thermal = remember { thermalSnapshot(context) }
    fun runCommand(vararg command: String, onResult: (CommandResult) -> Unit = {}) {
        val active = executor
        if (active == null) {
            output = "Aksi ini membutuhkan Root atau Shizuku."
            return
        }
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { active.execute(command.toList()) }
            output = buildString {
                appendLine(if (result.success) "SUKSES" else "GAGAL (exit ${result.exitCode})")
                appendLine(result.stdout.trim())
                if (result.stderr.isNotBlank()) appendLine(result.stderr.trim())
            }.trim()
            busy = false
            onResult(result)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("System Control Center", style = MaterialTheme.typography.headlineSmall)
            Text("Fitur lanjutan memerlukan Root/ Shizuku. Backup sebelum mengubah sistem.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ControlCard("Battery & Thermal Monitor", "Battery ${battery.first} · ${battery.second}\n${thermal}",
                Icons.Default.BatteryFull) {
                val latestBattery = batterySnapshot(context)
                val latestThermal = thermalSnapshot(context)
                output = "Battery & thermal snapshot\nBattery: ${latestBattery.first}\nStatus: ${latestBattery.second}\n$latestThermal\n\nData berasal dari Android framework; sensor yang tidak diekspos perangkat tidak akan ditampilkan."
            }
        }
        item {
            ControlCard("Kernel Manager", "Kernel release, SELinux, CPU governor and maximum frequency (read-only diagnostics).",
                Icons.Default.Memory) {
                runCommand("sh", "-c", "echo 'KERNEL'; uname -a; cat /proc/version 2>/dev/null; echo; echo 'SELINUX'; getenforce 2>/dev/null || cat /sys/fs/selinux/enforce 2>/dev/null; echo; echo 'CPU GOVERNOR'; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo unavailable; echo 'CPU MAX FREQ'; cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null || echo unavailable")
            }
        }
        item {
            ControlCard("Build.prop / Property Editor", "Baca properti dan ubah properti runtime menggunakan resetprop jika tersedia.",
                Icons.Default.Tune) { showPropertyDialog = true }
        }
        item {
            ControlCard("Service Manager", "Lihat status service init dan jalankan / hentikan service non-kritis.",
                Icons.Default.SettingsSuggest) { showServiceDialog = true }
        }
        item {
            ControlCard("Advanced Debloater", "Daftar paket dengan blokir komponen Android yang berisiko tinggi.",
                Icons.Default.DeleteSweep) { showDebloatDialog = true }
        }
        item {
            ControlCard("Root Configuration Backup", "Backup konfigurasi KernelSU / module manager ke penyimpanan bersama.",
                Icons.Default.Backup) {
                runCommand("sh", "-c", "mkdir -p /sdcard/MoraBackup || exit 1; stamp=$(date +%Y%m%d-%H%M%S); set --; [ -d /data/adb/ksu ] && set -- \"$@\" /data/adb/ksu; [ -d /data/adb/modules ] && set -- \"$@\" /data/adb/modules; [ \"$#\" -gt 0 ] || exit 4; archive=/sdcard/MoraBackup/mora-config-\$stamp.tar.gz; tar -czf \"\$archive\" \"$@\" || exit 5; ls -lh \"\$archive\"")
            }
        }
        item {
            ControlCard("Restore Configuration", "Ekstrak arsip backup Mora dari /sdcard/MoraBackup. Gunakan hanya arsip yang dibuat dari perangkat ini.",
                Icons.Default.Restore) { showRestoreDialog = true }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Operation output", style = MaterialTheme.typography.titleMedium)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(output, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showPropertyDialog) AlertDialog(
        onDismissRequest = { showPropertyDialog = false },
        title = { Text("Runtime property editor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(propertyName, { propertyName = it }, label = { Text("Property name") }, singleLine = true)
                OutlinedTextField(propertyValue, { propertyValue = it }, label = { Text("New value") }, singleLine = true)
                Text("Akan membuat snapshot getprop terlebih dahulu. Perubahan memakai resetprop -p jika tersedia; ini bukan penulisan langsung ke partisi /system.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showPropertyDialog = false
                val key = propertyName.trim()
                val value = propertyValue
                if (!key.matches(Regex("[a-zA-Z0-9._-]{1,128}")) ||
                    !(key.startsWith("persist.") || key.startsWith("debug.")) ||
                    key in setOf("persist.sys.usb.config", "persist.sys.locale", "persist.sys.timezone")) {
                    output = "Ditolak: nama properti tidak valid atau termasuk properti boot/security yang dilindungi."
                } else {
                    runCommand("sh", "-c", "mkdir -p /sdcard/MoraBackup && getprop > /sdcard/MoraBackup/getprop-$(date +%Y%m%d-%H%M%S).txt && command -v resetprop >/dev/null 2>&1 && resetprop -p ${shellQuote(key)} ${shellQuote(value)}")
                }
            }) { Text("Backup & Apply") }
        },
        dismissButton = { TextButton(onClick = { showPropertyDialog = false }) { Text("Cancel") } }
    )

    if (showServiceDialog) AlertDialog(
        onDismissRequest = { showServiceDialog = false },
        title = { Text("Service Manager") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(serviceName, { serviceName = it }, label = { Text("init service name") }, singleLine = true)
                Text("Nama service hanya boleh berisi huruf, angka, titik, garis bawah, dan minus. Service inti diblokir.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showServiceDialog = false
                val name = serviceName.trim()
                val protected = name.startsWith("zygote") || name in setOf("surfaceflinger", "servicemanager", "hwservicemanager", "vold", "installd", "system_server", "lmkd", "netd")
                if (!name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || protected) {
                    output = "Ditolak: nama service tidak valid atau service inti dilindungi."
                } else runCommand("sh", "-c", "echo 'STATUS:'; getprop init.svc.$name; echo 'ACTION:'; (setprop ctl.start ${shellQuote(name)} 2>/dev/null && echo start-requested) || true")
            }) { Text("Inspect / Start") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showServiceDialog = false }) { Text("Close") }
                TextButton(onClick = {
                    showServiceDialog = false
                    val name = serviceName.trim()
                    if (name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) &&
                        name !in setOf("zygote", "zygote64", "surfaceflinger", "servicemanager", "hwservicemanager", "vold", "installd", "system_server", "lmkd", "netd")) {
                        runCommand("sh", "-c", "setprop ctl.stop ${shellQuote(name)} 2>/dev/null && echo stop-requested")
                    } else output = "Stop ditolak untuk service inti atau nama tidak valid."
                }) { Text("Stop service", color = MaterialTheme.colorScheme.error) }
            }
        }
    )

    if (showDebloatDialog) AlertDialog(
        onDismissRequest = { showDebloatDialog = false },
        title = { Text("Advanced Debloater") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(packageName, { packageName = it }, label = { Text("Package ID") }, singleLine = true)
                Text("Protected: Android framework, System UI, Settings, telephony, permissions, package installer, network stack, Bluetooth/NFC core. Hanya disable untuk user 0; tidak menghapus APK sistem.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Daftar protected: ${protectedPackages.size} komponen inti.", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showDebloatDialog = false
                val pkg = packageName.trim()
                if (!pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) || pkg in protectedPackages) {
                    output = "Ditolak: package ID tidak valid atau termasuk daftar protected."
                } else {
                    runCommand("sh", "-c", "pm list packages -3 | grep -Fx ${shellQuote("package:$pkg")} >/dev/null || exit 7; pm disable-user --user 0 ${shellQuote(pkg)}")
                }
            }) { Text("Disable for user 0") }
        },
        dismissButton = { TextButton(onClick = { showDebloatDialog = false }) { Text("Cancel") } }
    )

    if (showRestoreDialog) AlertDialog(
        onDismissRequest = { showRestoreDialog = false },
        title = { Text("Restore configuration?") },
        text = { Text("Restore arsip terbaru dari /sdcard/MoraBackup. Operasi akan menimpa folder /data/adb/ksu dan /data/adb/modules. Restore modul yang tidak kompatibel dapat menyebabkan bootloop. Pastikan punya recovery/akses fastboot.") },
        confirmButton = {
            TextButton(onClick = {
                showRestoreDialog = false
                runCommand("sh", "-c", "file=$(ls -1t /sdcard/MoraBackup/mora-config-*.tar.gz 2>/dev/null | head -n 1); [ -n \"\$file\" ] || exit 2; tar -tzf \"\$file\" >/dev/null || exit 3; tar -xzf \"\$file\" -C /")
            }) { Text("Restore latest backup", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun ControlCard(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(detail) },
            leadingContent = { Icon(icon, null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, null) }
        )
    }
}

private fun batterySnapshot(context: Context): Pair<String, String> {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val status = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val plugged = status?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    return "${if (level >= 0) "$level%" else "N/A"}" to if (plugged == 0) "Discharging / unplugged" else "Charging"
}

private fun thermalSnapshot(context: Context): String {
    if (Build.VERSION.SDK_INT < 29) return "Thermal status API requires Android 10+."
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val status = power.currentThermalStatus
    val label = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "Unknown"
    }
    return "Thermal status: $label"
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
