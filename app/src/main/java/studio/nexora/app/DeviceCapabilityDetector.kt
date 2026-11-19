package studio.nexora.app

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/**
 * Lightweight, dependency-free hardware inventory.
 * Reports only capabilities exposed by Android; it never guesses that an NPU is usable.
 */
data class DeviceCapabilities(
    val manufacturer: String,
    val model: String,
    val socVendor: String,
    val socName: String,
    val cpuAbi: String,
    val cpuCores: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val glesVersion: String,
    val vulkanAvailable: Boolean,
    val vulkanLevel: String,
    val neuralApiAvailable: Boolean,
    val thermalStatus: String,
    val freeStorageMb: Long
)

object DeviceCapabilityDetector {
    fun detect(context: Context): DeviceCapabilities {
        val pm = context.packageManager
        val features = pm.systemAvailableFeatures.orEmpty()
        val vulkan = features.any {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL ||
                it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
        }
        val vulkanLevel = features.firstOrNull {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
        }?.version?.toString() ?: "Not exposed"
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val stat = StatFs(context.cacheDir.absolutePath)
        val socModel = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
        val hardware = listOf(socModel, Build.HARDWARE, Build.BOARD)
            .filter { it.isNotBlank() && !it.equals("unknown", true) }
            .distinct()
        val socName = hardware.firstOrNull() ?: "Not reported by firmware"
        val socVendor = identifyVendor(
            Build.MANUFACTURER + " " + Build.HARDWARE + " " + Build.BOARD + " " + socModel
        )
        val glVersion = activityManager.deviceConfigurationInfo.reqGlEsVersion
        val gles = if (glVersion == 0) "Unknown" else
            "${glVersion shr 16}.${glVersion and 0xffff}"
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            when (power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                else -> "Unknown"
            }
        } else "Unavailable on this Android version"

        return DeviceCapabilities(
            manufacturer = Build.MANUFACTURER.ifBlank { "Unknown" },
            model = Build.MODEL.ifBlank { "Unknown" },
            socVendor = socVendor,
            socName = socName,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            totalRamMb = memory.totalMem / (1024L * 1024L),
            availableRamMb = memory.availMem / (1024L * 1024L),
            glesVersion = gles,
            vulkanAvailable = vulkan,
            vulkanLevel = vulkanLevel,
            neuralApiAvailable = Build.VERSION.SDK_INT >= 27 &&
                pm.hasSystemFeature("android.hardware.neuralnetworks"),
            thermalStatus = thermal,
            freeStorageMb = stat.availableBytes / (1024L * 1024L)
        )
    }

    private fun identifyVendor(value: String): String {
        val s = value.lowercase()
        return when {
            "qcom" in s || "qualcomm" in s || "snapdragon" in s -> "Qualcomm"
            Regex("\\bmt[0-9]+").containsMatchIn(s) || "mediatek" in s || "dimensity" in s -> "MediaTek"
            "exynos" in s || "samsung" in s -> "Samsung"
            "tensor" in s || "google" in s -> "Google"
            "kirin" in s || "hisilicon" in s -> "HiSilicon"
            "unisoc" in s || "spreadtrum" in s -> "UNISOC"
            "rockchip" in s || "rk3" in s -> "Rockchip"
            "allwinner" in s -> "Allwinner"
            "tegra" in s || "nvidia" in s -> "NVIDIA"
            else -> "Other / firmware-hidden"
        }
    }
}
