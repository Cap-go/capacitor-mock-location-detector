import Capacitor
import Foundation

@objc(MockLocationDetectorPlugin)
public class MockLocationDetectorPlugin: CAPPlugin, CAPBridgedPlugin, MockLocationDetectorEventSink {
    public let identifier = "MockLocationDetectorPlugin"
    public let jsName = "MockLocationDetector"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getCapabilities", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "analyze", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "runCheck", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openDeveloperSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startMonitoring", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopMonitoring", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise),
    ]

    private let implementation = MockLocationDetector()

    override public func load() {
        super.load()
        implementation.eventSink = self
    }

    @objc func getCapabilities(_ call: CAPPluginCall) {
        call.resolve(implementation.getCapabilities())
    }

    @objc func analyze(_ call: CAPPluginCall) {
        let request = parseAnalyzeRequest(call)
        Task {
            let result = await implementation.analyze(request)
            call.resolve(result)
        }
    }

    @objc func runCheck(_ call: CAPPluginCall) {
        guard let check = call.getString("check") else {
            call.reject("check is required")
            return
        }
        var request = parseAnalyzeRequest(call)
        request.checks = [check]
        Task {
            let result = await implementation.runCheck(id: check, request: request)
            call.resolve(result)
        }
    }

    @objc func openDeveloperSettings(_ call: CAPPluginCall) {
        implementation.openDeveloperSettings()
        call.resolve()
    }

    @objc func startMonitoring(_ call: CAPPluginCall) {
        let analyze = parseAnalyzeRequest(call)
        let intervalMs = call.getInt("intervalMs") ?? 30000
        implementation.startMonitoring(MonitoringRequest(intervalMs: intervalMs, analyze: analyze))
        call.resolve()
    }

    @objc func stopMonitoring(_ call: CAPPluginCall) {
        implementation.stopMonitoring()
        call.resolve()
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": implementation.getPluginVersion()])
    }

    public func emitLocationIntegrityChanged(_ payload: [String: Any]) {
        notifyListeners("locationIntegrityChanged", data: payload)
    }

    private func parseAnalyzeRequest(_ call: CAPPluginCall) -> AnalyzeRequest {
        AnalyzeRequest(
            checks: call.getArray("checks", String.self),
            requestLocationSample: call.getBool("requestLocationSample") ?? true,
            locationTimeoutMs: call.getInt("locationTimeoutMs") ?? 10000,
            minDetectedChecks: call.getInt("minDetectedChecks") ?? 1,
            additionalMockAppUrlSchemes: call.getArray("additionalMockAppUrlSchemes", String.self) ?? []
        )
    }
}
