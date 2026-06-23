package app.capgo.mocklocationdetector

import android.os.Handler
import android.os.Looper
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "MockLocationDetector")
class MockLocationDetectorPlugin : Plugin() {

    private lateinit var implementation: MockLocationDetector
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null

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
        val request = parseAnalyzeRequest(call)
        val intervalMs = call.getInt("intervalMs", 30000)?.coerceAtLeast(5000) ?: 30000
        monitoringRunnable = object : Runnable {
            override fun run() {
                val payload = implementation.analyze(request)
                payload.put("reason", "interval")
                notifyListeners("locationIntegrityChanged", payload)
                handler.postDelayed(this, intervalMs.toLong())
            }
        }
        handler.post(monitoringRunnable!!)
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
            com.getcapacitor.JSObject().apply {
                put("version", implementation.getPluginVersion())
            },
        )
    }

    private fun stopMonitoringInternal() {
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable = null
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
