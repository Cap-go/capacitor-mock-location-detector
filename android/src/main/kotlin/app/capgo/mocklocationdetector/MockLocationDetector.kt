package app.capgo.mocklocationdetector

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class AnalyzeRequest(
    val checks: List<String>?,
    val requestLocationSample: Boolean,
    val locationTimeoutMs: Int,
    val minDetectedChecks: Int,
    val additionalMockAppPackages: List<String>,
)

class MockLocationDetector(private val context: Context) {

    private var lastLocation: Location? = null

    private val defaultChecks = listOf(
        "system_mock_flag",
        "developer_options",
        "developer_mode_indicators",
        "mock_location_app",
        "adb_enabled",
        "mock_provider_settings",
        "location_anomaly",
        "simulator",
    )

    private val knownMockPackages = listOf(
    private val knownMockPackages = listOf(
        "com.lexa.fakegps",
        "com.incorporateapps.fakegps.free",
        "com.incorporateapps.fakegps.fre",
        "com.blogspot.newapphorizons.fakegps",
        "com.ninja.toolkit.pulse.fake.gps.pro",
        "com.evezzon.mockgps",
        "com.mockgps.mocklocation",
        "com.imyfone.anytoandroid",
        "com.imyfone.anyto",
        "com.location.changer",
        "com.fakegps.mock",
        "com.incorporateapps.fakegps",
        "com.gsmartstudio.fakegps",
        "com.fake.gps.location.emulator",
        "com.fakegps.locationchanger",
        "com.locationfaker",
        "com.pokeys.pokeep",
        "com.pokeep.app",
        "com.anyto.android",
    )
    fun getCapabilities(): JSObject {
        return JSObject().apply {
            put("platform", "android")
            put("availableChecks", JSArray(defaultChecks))
            put("supportsMonitoring", true)
            put("canOpenDeveloperSettings", true)
        }
    }

    fun analyze(request: AnalyzeRequest): JSObject {
        val checks = resolveChecks(request.checks)
        val previousLocation = lastLocation
        var location = lastLocation
        if (request.requestLocationSample) {
            location = requestLocation(request.locationTimeoutMs) ?: lastLocation
        }

        val results = checks.map { runInternalCheck(it, location, request, previousLocation) }
        if (location != null) {
            lastLocation = location
        }
        return buildCompositeResult(results, location, request.minDetectedChecks.coerceAtLeast(1))
    }

    fun runCheck(id: String, request: AnalyzeRequest): JSObject {
        val previousLocation = lastLocation
        var location = lastLocation
        if (request.requestLocationSample) {
            location = requestLocation(request.locationTimeoutMs) ?: lastLocation
        }
        val result = runInternalCheck(id, location, request, previousLocation)
        if (location != null) {
            lastLocation = location
        }
        return result
    }

    fun openDeveloperSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
        val fallback = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }

    fun getPluginVersion(): String = "native-android"

    private fun resolveChecks(requested: List<String>?): List<String> {
        if (requested.isNullOrEmpty()) return defaultChecks
        return requested.filter { defaultChecks.contains(it) }
    }

    private fun runInternalCheck(
        id: String,
        location: Location?,
        request: AnalyzeRequest,
        previousLocation: Location?,
    ): JSObject {
        return when (id) {
            "system_mock_flag" -> checkSystemMockFlag(location)
            "developer_options" -> checkDeveloperOptions()
            "developer_mode_indicators" -> checkDeveloperModeIndicators()
            "mock_location_app" -> checkMockLocationApps(request.additionalMockAppPackages)
            "adb_enabled" -> checkAdbEnabled()
            "mock_provider_settings" -> checkMockProviderSettings()
            "location_anomaly" -> checkLocationAnomaly(location, previousLocation)
            "simulator" -> checkSimulator()
            else -> unavailableCheck(id, "Check is not supported on Android.")
        }
    }

    private fun checkSystemMockFlag(location: Location?): JSObject {
        if (location == null) {
            return checkResult(
                id = "system_mock_flag",
                name = "Android mock location flag",
                available = true,
                detected = false,
                message = "No GPS sample was available. Grant location permission and retry.",
            )
        }

        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

        return checkResult(
            id = "system_mock_flag",
            name = "Android mock location flag",
            available = true,
            detected = isMock,
            message = if (isMock) {
                "Location provider reported a mock/fixture location."
            } else {
                "Location provider did not report a mock location."
            },
            metadata = JSObject().apply { put("isMock", isMock) },
        )
    }

    private fun checkDeveloperOptions(): JSObject {
        val enabled = readGlobalSetting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1
        return checkResult(
            id = "developer_options",
            name = "Developer options enabled",
            available = true,
            detected = enabled,
            message = if (enabled) {
                "Developer options are enabled on this device."
            } else {
                "Developer options appear disabled."
            },
            metadata = JSObject().apply { put("developmentSettingsEnabled", enabled) },
        )
    }

    private fun checkDeveloperModeIndicators(): JSObject {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val testKeys = Build.TAGS?.contains("test-keys") == true
        val detected = debuggable || testKeys
        return checkResult(
            id = "developer_mode_indicators",
            name = "Developer build indicators",
            available = true,
            detected = detected,
            message = if (detected) {
                "Debuggable or test-key build indicators were detected."
            } else {
                "No debug build indicators were detected."
            },
            metadata = JSObject().apply {
                put("debuggable", debuggable)
                put("testKeys", testKeys)
            },
        )
    }

    private fun checkMockLocationApps(extraPackages: List<String>): JSObject {
        val packages = (knownMockPackages + extraPackages).distinct()
        val installed = packages.filter { isPackageInstalled(it) }
        return checkResult(
            id = "mock_location_app",
            name = "Known mock-location apps installed",
            available = true,
            detected = installed.isNotEmpty(),
            message = if (installed.isEmpty()) {
                "No known mock-location packages are installed."
            } else {
                "Installed mock-location packages: ${installed.joinToString()}"
            },
            metadata = JSObject().apply { put("installedPackages", JSArray(installed)) },
        )
    }

    private fun checkAdbEnabled(): JSObject {
        val adbEnabled = readGlobalSetting(Settings.Global.ADB_ENABLED) == 1
        return checkResult(
            id = "adb_enabled",
            name = "ADB debugging enabled",
            available = true,
            detected = adbEnabled,
            message = if (adbEnabled) {
                "USB debugging appears enabled."
            } else {
                "USB debugging appears disabled."
            },
            metadata = JSObject().apply { put("adbEnabled", adbEnabled) },
        )
    }

    private fun checkMockProviderSettings(): JSObject {
        val mockApps = findAppsWithMockLocationPermission()
        return checkResult(
            id = "mock_provider_settings",
            name = "Apps granted mock-location permission",
            available = true,
            detected = mockApps.isNotEmpty(),
            message = if (mockApps.isEmpty()) {
                "No apps with mock-location permission were found."
            } else {
                "Apps with mock-location permission: ${mockApps.joinToString()}"
            },
            metadata = JSObject().apply { put("mockApps", JSArray(mockApps)) },
        )
    }

    private fun checkLocationAnomaly(location: Location?, previousLocation: Location?): JSObject {
        val previous = previousLocation
        if (location == null || previous == null || previous.time == location.time) {
            return checkResult(
                id = "location_anomaly",
                name = "Impossible movement heuristic",
                available = true,
                detected = false,
                message = "Not enough location history to evaluate movement anomalies yet.",
            )
        }

        val elapsedSeconds = ((location.time - previous.time).coerceAtLeast(1L)).toDouble() / 1000.0
        val distanceMeters = previous.distanceTo(location).toDouble()
        val speedKmh = (distanceMeters / elapsedSeconds) * 3.6
        val detected = speedKmh > 300 || (distanceMeters > 1000 && elapsedSeconds < 5)

        return checkResult(
            id = "location_anomaly",
            name = "Impossible movement heuristic",
            available = true,
            detected = detected,
            message = if (detected) {
                "Movement implies ${"%.0f".format(speedKmh)} km/h between consecutive fixes."
            } else {
                "Movement speed looks plausible at ${"%.0f".format(speedKmh)} km/h."
            },
            metadata = JSObject().apply {
                put("distanceMeters", distanceMeters)
                put("elapsedSeconds", elapsedSeconds)
                put("impliedSpeedKmh", speedKmh)
            },
        )
    }

    private fun checkSimulator(): JSObject {
        val detected = isEmulator()
        return checkResult(
            id = "simulator",
            name = "Emulator environment",
            available = true,
            detected = detected,
            message = if (detected) "App is running on an Android emulator." else "App is running on physical hardware.",
        )
    }

    private fun unavailableCheck(id: String, message: String): JSObject {
        return checkResult(id = id, name = id, available = false, detected = false, message = message)
    }

    private fun checkResult(
        id: String,
        name: String,
        available: Boolean,
        detected: Boolean,
        message: String,
        metadata: JSObject = JSObject(),
    ): JSObject {
        return JSObject().apply {
            put("id", id)
            put("name", name)
            put("platform", "android")
            put("available", available)
            put("detected", detected)
            put("message", message)
            put("metadata", metadata)
        }
    }

    private fun buildCompositeResult(
        checks: List<JSObject>,
        location: Location?,
        minDetectedChecks: Int,
    ): JSObject {
        val triggered = checks.filter { it.getBoolean("detected", false) == true }
        val highConfidenceIds = setOf("system_mock_flag", "simulator")
        val highConfidence = triggered.any { highConfidenceIds.contains(it.getString("id")) }
        val isSimulated = highConfidence || triggered.size >= minDetectedChecks
        val riskScore = (triggered.size * 20 + if (highConfidence) 40 else 0).coerceAtMost(100)
        val confidence = when {
            riskScore == 0 -> "none"
            riskScore < 30 -> "low"
            riskScore < 60 -> "medium"
            else -> "high"
        }

        val developerChecks = checks.filter {
            val id = it.getString("id")
            id == "developer_options" || id == "developer_mode_indicators" || id == "adb_enabled" ||
                id == "mock_provider_settings" || id == "mock_location_app"
        }
        val developerDetected = developerChecks.any { it.getBoolean("detected", false) == true }

        return JSObject().apply {
            put("isSimulated", isSimulated)
            put("confidence", confidence)
            put("riskScore", riskScore)
            put("platform", "android")
            put("checks", JSArray(checks))
            put(
                "developerMode",
                JSObject().apply {
                    put("detected", developerDetected)
                    put("canDetectDeveloperMode", true)
                    put("checks", JSArray(developerChecks))
                },
            )
            put("locationSample", locationSample(location))
            if (isSimulated || developerDetected) {
                put(
                    "recommendation",
                    "Disable mock location apps, developer options, and USB debugging, then restart the device. Apps cannot change these settings automatically.",
                )
            }
        }
    }

    private fun locationSample(location: Location?): Any {
        if (location == null) return JSObject.NULL
        return JSObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy.toDouble())
            put("altitude", location.altitude)
            put("speed", location.speed.toDouble())
            put("timestamp", location.time)
        }
    }

    private fun requestLocation(timeoutMs: Int): Location? {
        val fineGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            return null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnown = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            return lastKnown
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            return null
        }

        val latch = CountDownLatch(1)
        var result: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                result = location
                latch.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }

        try {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            latch.await(timeoutMs.coerceAtLeast(1000).toLong(), TimeUnit.MILLISECONDS)
        } catch (_: SecurityException) {
            return null
        } finally {
            manager.removeUpdates(listener)
        }

        return result
    }

    private fun readGlobalSetting(name: String): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, name, 0)
        } catch (_: Exception) {
            0
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun findAppsWithMockLocationPermission(): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return emptyList()
        }

        val mockApps = mutableListOf<String>()
        val permission = "android.permission.ACCESS_MOCK_LOCATION"
        val packages = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            if (app.packageName == context.packageName) continue
            val result = context.packageManager.checkPermission(permission, app.packageName)
            if (result == PackageManager.PERMISSION_GRANTED) {
                mockApps.add(app.packageName)
            }
        }
        return mockApps
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("sdk_x86") ||
            Build.PRODUCT.contains("sdk_gphone64_arm64") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator")
    }
}
