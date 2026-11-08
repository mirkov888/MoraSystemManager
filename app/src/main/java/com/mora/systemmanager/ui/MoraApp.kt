package com.mora.systemmanager.ui

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mora.systemmanager.connector.MoraPolicyBridge
import com.mora.systemmanager.core.AccessManager
import com.mora.systemmanager.core.CommandExecutor
import com.mora.systemmanager.data.AppEntry
import com.mora.systemmanager.data.DeviceInfo
import com.mora.systemmanager.data.PackageRepository
import com.mora.systemmanager.data.SystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page(val title: String) {
    HOME("Overview"), APPS("Packages"), CONTROL("Control"), DIAG("Diagnostics"), SETTINGS("Settings")
}
private enum class AppFilter(val title: String) {
    ALL("All"), USER("User"), SYSTEM("System"), ENABLED("Enabled"), DISABLED("Disabled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoraApp(bridge: MoraPolicyBridge) {
    val context = LocalContext.current
    val accessManager = remember { AccessManager() }
    val systemRepo = remember { SystemRepository(context) }
    val packageRepo = remember { PackageRepository(context) }
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(Page.HOME) }
    var executor by remember { mutableStateOf<CommandExecutor?>(null) }
    var accessText by remember { mutableStateOf("Checking access…") }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var appsLoading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var operationBusy by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf("No diagnostic run yet.") }
    var selectedIcon by remember { mutableIntStateOf(2) }
    var iconMessage by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val snapshot = remember(refresh) { systemRepo.snapshot() }
    val device = remember { DeviceInfo() }
    val centralPolicy = bridge.policy.value
    val connectorState = bridge.connection.value
    val activeExecutor = remember(executor, bridge) { executor?.let { bridge.guarded(it) } }

    suspend fun refreshAccess() {
        executor = withContext(Dispatchers.IO) { accessManager.preferredExecutor() }
        accessText = when {
            executor != null -> "Connected via ${executor!!.label}"
            accessManager.shizukuAvailable() -> "Shizuku available — permission required"
            else -> "No privileged access"
        }
    }

    fun loadApps() {
        scope.launch {
            appsLoading = true
            apps = withContext(Dispatchers.IO) { packageRepo.installedApps() }
            appsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshAccess()
        loadApps()
        selectedIcon = detectActiveIcon(context.packageManager, context.packageName)
    }

    val filteredApps = remember(apps, search, filter) {
        apps.filter { app ->
            val matchesSearch = search.isBlank() ||
                app.label.contains(search, ignoreCase = true) ||
                app.packageName.contains(search, ignoreCase = true)
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
                AppFilter.ENABLED -> app.enabled
                AppFilter.DISABLED -> !app.enabled
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mora System Manager", style = MaterialTheme.typography.titleLarge)
                        Text("SYSTEM CONTROL CENTER · v2.0.0", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refresh++
                        loadApps()
                        scope.launch { refreshAccess() }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Page.values().forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = {
                            Icon(when (item) {
                                Page.HOME -> Icons.Default.Dashboard
                                Page.APPS -> Icons.Default.Apps
                                Page.CONTROL -> Icons.Default.Tune
                                Page.DIAG -> Icons.Default.Terminal
                                Page.SETTINGS -> Icons.Default.Settings
                            }, contentDescription = item.title)
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        when (page) {
            Page.HOME -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Device", style = MaterialTheme.typography.titleMedium)
                            Text("${device.manufacturer} ${device.model}", style = MaterialTheme.typography.headlineSmall)
                            Text("Android ${device.android} · ${device.device}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AssistChip(onClick = { scope.launch { refreshAccess() } },
                                label = { Text(accessText) },
                                leadingIcon = { Icon(Icons.Default.Security, null) })
                            if (executor == null && accessManager.shizukuAvailable()) {
                                Button(onClick = {
                                    try {
                                        accessManager.requestShizukuPermission(4102)
                                    } catch (t: Throwable) {
                                        accessText = "Shizuku permission request failed: ${t.message}"
                                    }
                                }) { Text("Grant Shizuku permission") }
                            }
                            OutlinedButton(onClick = { scope.launch { refreshAccess() } }) {
                                Text("Recheck access")
                            }
                        }
                    }
                }
                item {
                    Text("System resources", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("RAM", "${snapshot.availableRamMb} MB",
                            "of ${snapshot.totalRamMb} MB", Modifier.weight(1f))
                        MetricCard("Storage", "%.1f GB".format(snapshot.storageFreeGb),
                            "free of %.1f GB".format(snapshot.storageTotalGb), Modifier.weight(1f))
                    }
                }
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Mora Shield · Central Policy", style = MaterialTheme.typography.titleMedium)
                            Text(connectorState, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Master: ${if (centralPolicy.enabled) "Aktif" else "Nonaktif"} · Monitoring: ${if (centralPolicy.monitoringEnabled) "Aktif" else "Nonaktif"}")
                            Text("Network: ${centralPolicy.networkMode} · Background: ${centralPolicy.backgroundMode}")
                            Text(if (centralPolicy.updatedAtMs > 0L) "Cache diperbarui: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(centralPolicy.updatedAtMs))}" else "Belum menerima kebijakan dari Shield; menggunakan cache lokal.")
                            if (!centralPolicy.enabled) {
                                Text("Aksi Root/Shizuku diblokir oleh master policy.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Installed applications", style = MaterialTheme.typography.titleMedium)
                            Text("${apps.size} packages detected")
                            Text("Package inventory is available without root. System changes require Root or Shizuku.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Page.APPS -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Package Manager", style = MaterialTheme.typography.titleLarge)
                    Text("${filteredApps.size} / ${apps.size}", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search app or package ID") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppFilter.values().forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.title) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { loadApps() }, enabled = !appsLoading) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (appsLoading) "Loading…" else "Refresh")
                    }
                    OutlinedButton(onClick = {
                        val report = buildString {
                            appendLine("Mora System Manager — Package Inventory")
                            appendLine("Device: ${device.manufacturer} ${device.model}")
                            appendLine("Android: ${device.android}")
                            appendLine("Total packages: ${apps.size}")
                            appendLine()
                            apps.forEach { app ->
                                appendLine("${app.label}\t${app.packageName}\t${if (app.isSystem) "SYSTEM" else "USER"}\t${if (app.enabled) "ENABLED" else "DISABLED"}\t${app.versionName}")
                            }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Mora Package Inventory")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Export package inventory"))
                    }) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Export")
                    }
                }
                if (appsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (apps.isEmpty() && !appsLoading) {
                    Text("No package inventory loaded.", Modifier.padding(16.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label, maxLines = 1) },
                            supportingContent = {
                                Text("${app.packageName}\n${if (app.isSystem) "System" else "User"} · ${if (app.enabled) "Enabled" else "Disabled"}",
                                    maxLines = 2)
                            },
                            leadingContent = {
                                Icon(if (app.isSystem) Icons.Default.SettingsSystemDaydream else Icons.Default.Android, null)
                            },
                            trailingContent = {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Details")
                            },
                            modifier = Modifier.fillMaxWidth().clickable { selectedApp = app }
                        )
                        HorizontalDivider()
                    }
                }
            }

            Page.CONTROL -> ControlCenterScreen(executor = activeExecutor)

            Page.DIAG -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
                Text("Collect basic device and privilege information.")
                Button(onClick = {
                    scope.launch {
                        val active = activeExecutor
                        diagnostic = if (active == null) "No Root/Shizuku access." else {
                            val id = active.execute(listOf("id"))
                            val kernel = active.execute(listOf("uname", "-a"))
                            "Access: ${active.label}\n${id.stdout.trim()}\n\nKernel:\n${kernel.stdout.trim()}\n\n${kernel.stderr}"
                        }
                    }
                }, enabled = activeExecutor != null) { Text("Run diagnostics") }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(diagnostic, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Page.SETTINGS -> SettingsScreen(
                selectedIcon = selectedIcon,
                message = iconMessage,
                onSelectIcon = { choice ->
                    val aliases = listOf(
                        ".LauncherIconBlue",
                        ".LauncherIconPurple",
                        ".LauncherIconGear",
                        ".LauncherIconCyan"
                    )
                    try {
                        val pm = context.packageManager
                        aliases.forEachIndexed { index, alias ->
                            pm.setComponentEnabledSetting(
                                ComponentName(context.packageName, context.packageName + alias),
                                if (index == choice - 1) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )
                        }
                        selectedIcon = choice
                        iconMessage = "Icon updated. If the launcher still shows the old icon, wait a moment or refresh the home screen."
                    } catch (t: Throwable) {
                        iconMessage = "Could not change icon: ${t.message ?: "unknown error"}"
                    }
                }
            )
        }
    }

    selectedApp?.let { app ->
        AlertDialog(
            onDismissRequest = { if (!operationBusy) selectedApp = null },
            title = { Text(app.label) },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(app.packageName, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    DetailLine("Type", if (app.isSystem) "System app" else "User app")
                    DetailLine("Status", if (app.enabled) "Enabled" else "Disabled")
                    DetailLine("Version", app.versionName)
                    DetailLine("Version code", app.versionCode.toString())
                    DetailLine("UID", app.uid.toString())
                    DetailLine("APK path", app.apkPath)
                    if (app.isSystem) {
                        Text("System package: uninstall is blocked in Mora V2. Disable only if you understand the consequences.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    operationMessage?.let { Text(it, color = if (it.contains("failed", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        enabled = executor != null && !operationBusy,
                        onClick = { pendingAction = if (app.enabled) "disable" else "enable" }
                    ) { Text(if (app.enabled) "Disable" else "Enable") }
                    if (!app.isSystem) {
                        TextButton(
                            enabled = executor != null && !operationBusy,
                            onClick = { pendingAction = "uninstall" }
                        ) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedApp = null; operationMessage = null }) { Text("Close") }
            }
        )
    }

    pendingAction?.let { action ->
        val app = selectedApp
        if (app != null) {
            val actionTitle = when (action) {
                "disable" -> "Disable ${app.label}?"
                "enable" -> "Enable ${app.label}?"
                else -> "Uninstall ${app.label} for user 0?"
            }
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                title = { Text(actionTitle) },
                text = {
                    Text(when (action) {
                        "disable" -> "The app will be disabled for Android user 0. Some system features may stop working."
                        "enable" -> "The app will be enabled for Android user 0."
                        else -> "The app will be uninstalled for user 0. Its APK may remain on the system partition; user data may be removed."
                    })
                },
                confirmButton = {
                    TextButton(
                        enabled = !operationBusy,
                        onClick = {
                            pendingAction = null
                            scope.launch {
                                val active = executor
                                if (active == null) {
                                    operationMessage = "Operation failed: no privileged access."
                                } else {
                                    operationBusy = true
                                    val result = when (action) {
                                        "disable" -> packageRepo.setEnabled(active, app, false)
                                        "enable" -> packageRepo.setEnabled(active, app, true)
                                        else -> packageRepo.uninstallUserApp(active, app)
                                    }
                                    operationBusy = false
                                    operationMessage = (if (result.success) "Success: " else "Failed: ") + result.message
                                    loadApps()
                                }
                            }
                        }
                    ) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAction = null }) { Text("Cancel") }
                }
            )
        }
    }
}


@Composable
private fun SettingsScreen(
    selectedIcon: Int,
    message: String?,
    onSelectIcon: (Int) -> Unit
) {
    val names = listOf("Blue Shield", "Privacy Cat", "Gear Shield", "Cyan Shield")
    val descriptions = listOf(
        "Blue layered shield",
        "Privacy Cat mascot — default",
        "Blue-violet gear and wrench",
        "Cyan glass shield"
    )
    val resources = listOf(
        com.mora.systemmanager.R.drawable.ic_launcher_variant_1,
        com.mora.systemmanager.R.drawable.ic_launcher_variant_2,
        com.mora.systemmanager.R.drawable.ic_launcher_variant_3,
        com.mora.systemmanager.R.drawable.ic_launcher_variant_4
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Text("Launcher appearance", style = MaterialTheme.typography.titleMedium)
            Text("Privacy Cat is the default launcher icon. Adaptive icons work on Android 8+ and themed monochrome icons on Android 13+ launchers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items((1..4).toList()) { number ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { onSelectIcon(number) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (selectedIcon == number)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(resources[number - 1]),
                        contentDescription = names[number - 1],
                        modifier = Modifier.size(76.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(names[number - 1], style = MaterialTheme.typography.titleMedium)
                        Text(descriptions[number - 1], style = MaterialTheme.typography.bodySmall)
                        if (number == 2) {
                            Text("DEFAULT", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    RadioButton(
                        selected = selectedIcon == number,
                        onClick = { onSelectIcon(number) }
                    )
                }
            }
        }
        if (message != null) {
            item { Text(message, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall) }
        }
        item {
            HorizontalDivider()
            Text("Future update suggestions", style = MaterialTheme.typography.titleMedium)
            SuggestionItem("Privacy Cat launcher icon", "Current default launcher artwork.")
            SuggestionItem("Adaptive & themed icons", "Adaptive foreground/background and monochrome themed icon resources are included.")
            SuggestionItem("Appearance presets", "Accent color, compact layout, and light/dark theme controls.")
            SuggestionItem("Battery & thermal monitor", "Battery percentage and Android thermal status snapshot.")
            SuggestionItem("Advanced Debloater & Kernel Manager", "Protected-package checks, kernel diagnostics, and guarded configuration controls.")
        }
    }
}

@Composable
private fun SuggestionItem(title: String, detail: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(Icons.Default.Lightbulb, contentDescription = null) }
    )
}

private fun detectActiveIcon(pm: PackageManager, packageName: String): Int {
    val aliases = listOf(
        ".LauncherIconBlue",
        ".LauncherIconPurple",
        ".LauncherIconGear",
        ".LauncherIconCyan"
    )
    val activeIndex = aliases.indexOfFirst { alias ->
        try {
            pm.getComponentEnabledSetting(ComponentName(packageName, packageName + alias)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (_: Throwable) { false }
    }
    return if (activeIndex < 0) 2 else activeIndex + 1
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(detail, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
