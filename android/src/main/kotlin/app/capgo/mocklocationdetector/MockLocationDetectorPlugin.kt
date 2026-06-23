package app.capgo.mocklocationdetector

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "MockLocationDetector")
class MockLocationDetectorPlugin : Plugin() {

    private lateinit var implementation: MockLocationDetector
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var monitoringLocationListener: LocationListener? = null
    private var monitoringRequest: AnalyzeRequest? = null
    private var emitOnlyOnChange = true
    private var lastEmittedFingerprint: String? = null

    override fun load() {
        super.load()
        implementation = MockLocationDetector(context)
    }

    @PluginMethod
    fun getCapabilities(call: PluginCall) {
        call.resolve(implementation.getCapabilities())
    }

    @PluginMethod
    fun analyze(call: PluginCall) {
        val result = implementation.analyze(parseAnalyzeRequest(call))
        call.resolve(result)
    }

    @PluginMethod
    fun runCheck(call: PluginCall) {
        val check = call.getString("check")
        if (check.isNullOrBlank()) {
            call.reject("check is required")
            return
        }
        val result = implementation.runCheck(check, parseAnalyzeRequest(call))
        call.resolve(result)
    }

    @PluginMethod
    fun openDeveloperSettings(call: PluginCall) {
        implementation.openDeveloperSettings()
        call.resolve()
    }

    @PluginMethod
    fun startMonitoring(call: PluginCall) {
        stopMonitoringInternal()
        monitoringRequest = parseAnalyzeRequest(call)
        emitOnlyOnChange = call.getBoolean("emitOnlyOnChange", true) ?: true
        lastEmittedFingerprint = null
        val intervalMs = call.getInt("intervalMs", 30000)?.coerceAtLeast(5000) ?: 30000
        monitoringRunnable = object : Runnable {
            override fun run() {
                emitMonitoringEvent("interval")
                handler.postDelayed(this, intervalMs.toLong())
            }
        }
        handler.post(monitoringRunnable!!)
        startMonitoringLocationUpdates()
        emitMonitoringEvent("manual", force = true)
        call.resolve()
    }

    @PluginMethod
    fun stopMonitoring(call: PluginCall) {
        stopMonitoringInternal()
        call.resolve()
    }

    @PluginMethod
    fun getPluginVersion(call: PluginCall) {
        call.resolve(
            JSObject().apply {
                put("version", implementation.getPluginVersion())
            },
        )
    }

    private fun emitMonitoringEvent(reason: String, force: Boolean = false) {
        val request = monitoringRequest ?: return
        val payload = implementation.analyze(request)
        payload.put("reason", reason)
        if (!force && emitOnlyOnChange) {
            val fingerprint = integrityFingerprint(payload)
            if (fingerprint == lastEmittedFingerprint) {
                return
            }
            lastEmittedFingerprint = fingerprint
        } else if (force || !emitOnlyOnChange) {
            lastEmittedFingerprint = integrityFingerprint(payload)
        }
        notifyListeners("locationIntegrityChanged", payload)
    }

    private fun integrityFingerprint(payload: JSObject): String {
        val checks = payload.getJSONArray("checks") ?: return payload.toString()
        val triggered = buildList {
            for (index in 0 until checks.length()) {
                val check = checks.optJSONObject(index) ?: continue
                if (check.optBoolean("detected", false)) {
                    add(check.optString("id", ""))
                }
            }
        }.sorted().joinToString(",")
        return listOf(
            payload.optBoolean("isSimulated", false),
            payload.optString("confidence", ""),
            payload.optInt("riskScore", 0),
            triggered,
        ).joinToString("|")
    }

    private fun startMonitoringLocationUpdates() {
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            return
        }

        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                implementation.updateLastLocation(location)
                emitMonitoringEvent("location_update")
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        monitoringLocationListener = listener

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.requestLocationUpdates(provider, 5000L, 10f, listener, Looper.getMainLooper())
            } else {
                @Suppress("DEPRECATION")
                manager.requestLocationUpdates(provider, 5000L, 10f, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            monitoringLocationListener = null
        }
    }

    private fun stopMonitoringInternal() {
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable = null
        monitoringRequest = null
        lastEmittedFingerprint = null

        monitoringLocationListener?.let { listener ->
            try {
                val manager = context.getSystemService(LocationManager::class.java)
                manager?.removeUpdates(listener)
            } catch (_: SecurityException) {
            }
        }
        monitoringLocationListener = null
    }

    private fun parseAnalyzeRequest(call: PluginCall): AnalyzeRequest {
        val checksArray = call.getArray("checks")
        val checks = checksArray?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
        }

        val packagesArray = call.getArray("additionalMockAppPackages")
        val packages = packagesArray?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
        } ?: emptyList()

        return AnalyzeRequest(
            checks = checks,
            requestLocationSample = call.getBoolean("requestLocationSample", true) ?: true,
            locationTimeoutMs = call.getInt("locationTimeoutMs", 10000) ?: 10000,
            minDetectedChecks = call.getInt("minDetectedChecks", 1) ?: 1,
            additionalMockAppPackages = packages,
        )
    }
}
