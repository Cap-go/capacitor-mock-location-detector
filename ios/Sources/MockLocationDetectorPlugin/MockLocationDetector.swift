import CoreLocation
import CoreMotion
import Darwin
import Foundation
import UIKit
struct AnalyzeRequest {
    var checks: [String]?
    var requestLocationSample: Bool
    var locationTimeoutMs: Int
    var minDetectedChecks: Int
    var additionalMockAppUrlSchemes: [String]
}

struct MonitoringRequest {
    var intervalMs: Int
    var analyze: AnalyzeRequest
    var emitOnlyOnChange: Bool
}

final class MockLocationDetector: NSObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    private let motionManager = CMMotionManager()
    private var locationContinuation: CheckedContinuation<CLLocation?, Never>?
    private var locationTimeoutWorkItem: DispatchWorkItem?
    private var lastLocation: CLLocation?
    private var lastMotionMagnitude: Double = 0
    private var monitoringTimer: Timer?
    private var monitoringRequest: MonitoringRequest?
    private var lastEmittedFingerprint: String?
    weak var eventSink: MockLocationDetectorEventSink?

    private let defaultChecks = [
        "system_mock_flag",
        "developer_mode_indicators",
        "mock_location_app",
        "location_anomaly",
        "motion_correlation",
        "simulator",
    ]
    private let knownMockUrlSchemes = [
        "anyto",
        "imyfone-anyto",
        "igo",
        "igomarkto",
        "fakegps",
        "mockgps",
        "pokeep",
        "locationfaker",
    ]
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func getCapabilities() -> [String: Any] {
        return [
            "platform": "ios",
            "availableChecks": defaultChecks,
            "supportsMonitoring": true,
            "canOpenDeveloperSettings": true,
        ]
    }

    func analyze(_ request: AnalyzeRequest) async -> [String: Any] {
        let checks = resolvedChecks(from: request.checks)
        var locationSample: CLLocation? = lastLocation

        if request.requestLocationSample {
            locationSample = await requestLocation(timeoutMs: request.locationTimeoutMs)
            if let locationSample {
                lastLocation = locationSample
            }
        }

        var results: [[String: Any]] = []
        for check in checks {
            results.append(runCheck(id: check, location: locationSample, request: request))
        }

        return buildCompositeResult(
            checks: results,
            location: locationSample,
            minDetectedChecks: max(1, request.minDetectedChecks)
        )
    }

    func runCheck(id: String, request: AnalyzeRequest) async -> [String: Any] {
        var locationSample: CLLocation? = lastLocation
        if request.requestLocationSample {
            locationSample = await requestLocation(timeoutMs: request.locationTimeoutMs)
            if let locationSample {
                lastLocation = locationSample
            }
        }
        return runCheck(id: id, location: locationSample, request: request)
    }

    func startMonitoring(_ request: MonitoringRequest) {
        monitoringRequest = request
        lastEmittedFingerprint = nil
        monitoringTimer?.invalidate()
        let interval = max(5.0, Double(request.intervalMs) / 1000.0)
        monitoringTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { await self.emitMonitoringEvent(reason: "interval") }
        }
        locationManager.startUpdatingLocation()
        Task { await emitMonitoringEvent(reason: "manual", force: true) }
    }

    func stopMonitoring() {
        monitoringTimer?.invalidate()
        monitoringTimer = nil
        monitoringRequest = nil
        lastEmittedFingerprint = nil
        locationManager.stopUpdatingLocation()
    }

    func openDeveloperSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            DispatchQueue.main.async {
                UIApplication.shared.open(url)
            }
        }
    }

    func getPluginVersion() -> String {
        return "native-ios"
    }

    private func resolvedChecks(from requested: [String]?) -> [String] {
        guard let requested, !requested.isEmpty else { return defaultChecks }
        return requested.filter { defaultChecks.contains($0) }
    }

    private func runCheck(id: String, location: CLLocation?, request: AnalyzeRequest) -> [String: Any] {
        switch id {
        case "system_mock_flag":
            return checkSystemMockFlag(location: location)
        case "developer_mode_indicators":
            return checkDeveloperModeIndicators()
        case "mock_location_app":
            return checkMockLocationApps(extraSchemes: request.additionalMockAppUrlSchemes)
        case "location_anomaly":
            return checkLocationAnomaly(location: location)
        case "motion_correlation":
            return checkMotionCorrelation(location: location)
        case "simulator":
            return checkSimulator()
        default:
            return unavailableCheck(id: id, message: "Check is not supported on iOS.")
        }
    }

    private func checkSystemMockFlag(location: CLLocation?) -> [String: Any] {
        guard let location else {
            return checkResult(
                id: "system_mock_flag",
                name: "Core Location software simulation flag",
                available: true,
                detected: false,
                message: "No GPS sample was available. Grant location permission and retry.",
                metadata: [:]
            )
        }

        if #available(iOS 15.0, *) {
            let source = location.sourceInformation
            let simulated = source?.isSimulatedBySoftware ?? false
            let accessory = source?.isProducedByAccessory ?? false
            let detected = simulated || accessory
            var message = "Core Location did not report software simulation."
            if simulated {
                message = "Core Location reports isSimulatedBySoftware=true."
            } else if accessory {
                message = "Core Location reports isProducedByAccessory=true."
            }

            return checkResult(
                id: "system_mock_flag",
                name: "Core Location software simulation flag",
                available: true,
                detected: detected,
                message: message,
                metadata: [
                    "isSimulatedBySoftware": simulated,
                    "isProducedByAccessory": accessory,
                ]
            )
        }

        return checkResult(
            id: "system_mock_flag",
            name: "Core Location software simulation flag",
            available: false,
            detected: false,
            message: "Requires iOS 15 or newer.",
            metadata: [:]
        )
    }

    private func checkDeveloperModeIndicators() -> [String: Any] {
        var indicators: [String: Bool] = [:]
        indicators["debuggerAttached"] = isDebuggerAttached()
        indicators["developmentProvisioning"] = hasDevelopmentProvisioningProfile()
        indicators["dyldInsertLibraries"] = ProcessInfo.processInfo.environment["DYLD_INSERT_LIBRARIES"] != nil

        let detected = indicators.values.contains(true)
        return checkResult(
            id: "developer_mode_indicators",
            name: "Developer tooling heuristics",
            available: true,
            detected: detected,
            message: detected
                ? "Indirect developer tooling signals were detected. iOS does not expose Developer Mode directly."
                : "No developer tooling heuristics were detected.",
            metadata: indicators
        )
    }

    private func checkMockLocationApps(extraSchemes: [String]) -> [String: Any] {
        let schemes = Array(Set(knownMockUrlSchemes + extraSchemes))
        var matched: [String] = []
        for scheme in schemes {
            guard let url = URL(string: "\(scheme)://") else { continue }
            if UIApplication.shared.canOpenURL(url) {
                matched.append(scheme)
            }
        }

        return checkResult(
            id: "mock_location_app",
            name: "Known mock-location companion apps",
            available: true,
            detected: !matched.isEmpty,
            message: matched.isEmpty
                ? "No configured URL schemes responded. Add LSApplicationQueriesSchemes for apps you want to detect."
                : "Matched URL schemes: \(matched.joined(separator: ", "))",
            metadata: ["matchedSchemes": matched]
        )
    }

    private func checkLocationAnomaly(location: CLLocation?) -> [String: Any] {
        guard let location, let previous = lastLocation, previous !== location else {
            return checkResult(
                id: "location_anomaly",
                name: "Impossible movement heuristic",
                available: true,
                detected: false,
                message: "Not enough location history to evaluate movement anomalies yet.",
                metadata: [:]
            )
        }

        let elapsed = max(location.timestamp.timeIntervalSince(previous.timestamp), 0.001)
        let distance = location.distance(from: previous)
        let speedMps = distance / elapsed
        let speedKmh = speedMps * 3.6
        let detected = speedKmh > 300 || (distance > 1000 && elapsed < 5)

        return checkResult(
            id: "location_anomaly",
            name: "Impossible movement heuristic",
            available: true,
            detected: detected,
            message: detected
                ? String(format: "Movement implies %.0f km/h between consecutive fixes.", speedKmh)
                : String(format: "Movement speed looks plausible at %.0f km/h.", speedKmh),
            metadata: [
                "distanceMeters": distance,
                "elapsedSeconds": elapsed,
                "impliedSpeedKmh": speedKmh,
            ]
        )
    }

    private func checkMotionCorrelation(location: CLLocation?) -> [String: Any] {
        guard let location, let previous = lastLocation, previous !== location else {
            return checkResult(
                id: "motion_correlation",
                name: "Motion vs GPS correlation",
                available: motionManager.isAccelerometerAvailable,
                detected: false,
                message: "Not enough data to compare device motion with GPS movement.",
                metadata: [:]
            )
        }

        let distance = location.distance(from: previous)
        let motionDelta = abs(lastMotionMagnitude)
        let detected = distance > 75 && motionDelta < 0.15

        return checkResult(
            id: "motion_correlation",
            name: "Motion vs GPS correlation",
            available: motionManager.isAccelerometerAvailable,
            detected: detected,
            message: detected
                ? "GPS moved significantly while accelerometer activity stayed flat."
                : "GPS movement correlates with device motion samples.",
            metadata: [
                "distanceMeters": distance,
                "motionDelta": motionDelta,
            ]
        )
    }

    private func checkSimulator() -> [String: Any] {
        #if targetEnvironment(simulator)
        let detected = true
        #else
        let detected = false
        #endif

        return checkResult(
            id: "simulator",
            name: "Simulator environment",
            available: true,
            detected: detected,
            message: detected ? "App is running in the iOS Simulator." : "App is running on physical hardware.",
            metadata: [:]
        )
    }

    private func unavailableCheck(id: String, message: String) -> [String: Any] {
        return checkResult(id: id, name: id, available: false, detected: false, message: message, metadata: [:])
    }

    private func checkResult(
        id: String,
        name: String,
        available: Bool,
        detected: Bool,
        message: String,
        metadata: [String: Any]
    ) -> [String: Any] {
        return [
            "id": id,
            "name": name,
            "platform": "ios",
            "available": available,
            "detected": detected,
            "message": message,
            "metadata": metadata,
        ]
    }

    private func buildCompositeResult(
        checks: [[String: Any]],
        location: CLLocation?,
        minDetectedChecks: Int
    ) -> [String: Any] {
        let triggered = checks.filter { ($0["detected"] as? Bool) == true }
        let highConfidenceIds: Set<String> = ["system_mock_flag", "simulator"]
        let highConfidence = triggered.contains { highConfidenceIds.contains(($0["id"] as? String) ?? "") }
        let isSimulated = highConfidence || triggered.count >= minDetectedChecks
        let riskScore = min(100, triggered.count * 20 + (highConfidence ? 40 : 0))
        let confidence: String
        switch riskScore {
        case 0: confidence = "none"
        case 1...29: confidence = "low"
        case 30...59: confidence = "medium"
        default: confidence = "high"
        }

        let developerChecks = checks.filter { check in
            let id = (check["id"] as? String) ?? ""
            return id == "developer_mode_indicators" || id == "mock_location_app"
        }
        let developerDetected = developerChecks.contains { ($0["detected"] as? Bool) == true }

        var recommendation: String? = nil
        if isSimulated || developerDetected {
            recommendation = "Disable mock-location tooling and Developer Mode, then restart the device. Apps cannot change these settings automatically."
        }

        return [
            "isSimulated": isSimulated,
            "confidence": confidence,
            "riskScore": riskScore,
            "platform": "ios",
            "checks": checks,
            "developerMode": [
                "detected": developerDetected,
                "canDetectDeveloperMode": false,
                "checks": developerChecks,
            ],
            "locationSample": locationSampleDict(from: location) as Any,
            "recommendation": recommendation as Any,
        ]
    }

    private func locationSampleDict(from location: CLLocation?) -> [String: Any]? {
        guard let location else { return nil }
        return [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "accuracy": location.horizontalAccuracy,
            "altitude": location.altitude,
            "speed": location.speed,
            "timestamp": Int(location.timestamp.timeIntervalSince1970 * 1000),
        ]
    }

    private func requestLocation(timeoutMs: Int) async -> CLLocation? {
        let status = locationManager.authorizationStatus
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        }

        return await withCheckedContinuation { continuation in
            locationContinuation?.resume(returning: lastLocation)
            locationContinuation = continuation
            locationManager.requestLocation()

            locationTimeoutWorkItem?.cancel()
            let workItem = DispatchWorkItem { [weak self] in
                guard let self else { return }
                self.locationContinuation?.resume(returning: self.lastLocation)
                self.locationContinuation = nil
            }
            locationTimeoutWorkItem = workItem
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(max(1000, timeoutMs)), execute: workItem)
        }
    }

    private func emitMonitoringEvent(reason: String, force: Bool = false) async {
        guard let monitoringRequest else { return }
        let payload = await analyze(monitoringRequest.analyze)
        var event = payload
        event["reason"] = reason

        if !force && monitoringRequest.emitOnlyOnChange {
            let fingerprint = integrityFingerprint(from: payload)
            if fingerprint == lastEmittedFingerprint {
                return
            }
            lastEmittedFingerprint = fingerprint
        } else {
            lastEmittedFingerprint = integrityFingerprint(from: payload)
        }

        eventSink?.emitLocationIntegrityChanged(event)
    }

    private func integrityFingerprint(from payload: [String: Any]) -> String {
        let checks = (payload["checks"] as? [[String: Any]]) ?? []
        let triggered = checks
            .filter { ($0["detected"] as? Bool) == true }
            .compactMap { $0["id"] as? String }
            .sorted()
            .joined(separator: ",")
        let isSimulated = (payload["isSimulated"] as? Bool) == true
        let confidence = (payload["confidence"] as? String) ?? ""
        let riskScore = (payload["riskScore"] as? Int) ?? 0
        return "\(isSimulated)|\(confidence)|\(riskScore)|\(triggered)"
    }

    private func isDebuggerAttached() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        let result = mib.withUnsafeMutableBufferPointer { pointer -> Int32 in
            sysctl(pointer.baseAddress, 4, &info, &size, nil, 0)
        }
        if result != 0 { return false }
        return (info.kp_proc.p_flag & P_TRACED) != 0
    }

    private func hasDevelopmentProvisioningProfile() -> Bool {
        guard let path = Bundle.main.path(forResource: "embedded", ofType: "mobileprovision") else {
            return false
        }
        return FileManager.default.fileExists(atPath: path)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let latest = locations.last else { return }
        lastLocation = latest
        sampleMotion()
        locationTimeoutWorkItem?.cancel()
        locationContinuation?.resume(returning: latest)
        locationContinuation = nil

        if monitoringRequest != nil {
            Task { await emitMonitoringEvent(reason: "location_update") }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        locationTimeoutWorkItem?.cancel()
        locationContinuation?.resume(returning: lastLocation)
        locationContinuation = nil
    }

    private func sampleMotion() {
        guard motionManager.isAccelerometerAvailable else { return }
        motionManager.accelerometerUpdateInterval = 0.2
        motionManager.startAccelerometerUpdates()
        if let data = motionManager.accelerometerData {
            let magnitude = sqrt(pow(data.acceleration.x, 2) + pow(data.acceleration.y, 2) + pow(data.acceleration.z, 2))
            lastMotionMagnitude = magnitude
        }
        motionManager.stopAccelerometerUpdates()
    }
}

protocol MockLocationDetectorEventSink: AnyObject {
    func emitLocationIntegrityChanged(_ payload: [String: Any])
}
