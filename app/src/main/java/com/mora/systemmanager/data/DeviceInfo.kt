package com.mora.systemmanager.data

import android.os.Build

data class DeviceInfo(
    val model: String = Build.MODEL ?: "Unknown",
    val manufacturer: String = Build.MANUFACTURER ?: "Unknown",
    val android: String = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
    val device: String = Build.DEVICE ?: "Unknown",
    val fingerprint: String = Build.FINGERPRINT ?: "Unknown"
)
