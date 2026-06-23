import XCTest
@testable import MockLocationDetectorPlugin

class MockLocationDetectorTests: XCTestCase {
    func testGetCapabilities() {
        let implementation = MockLocationDetector()
        let capabilities = implementation.getCapabilities()
        XCTAssertEqual(capabilities["platform"] as? String, "ios")
    }

    func testGetPluginVersion() {
        let implementation = MockLocationDetector()
        let result = implementation.getPluginVersion()
        XCTAssertEqual("native-ios", result)
    }
}
