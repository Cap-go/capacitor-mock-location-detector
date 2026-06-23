// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorMockLocationDetector",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapgoCapacitorMockLocationDetector",
            targets: ["MockLocationDetectorPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "MockLocationDetectorPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/MockLocationDetectorPlugin"),
        .testTarget(
            name: "MockLocationDetectorPluginTests",
            dependencies: ["MockLocationDetectorPlugin"],
            path: "ios/Tests/MockLocationDetectorPluginTests")
    ]
)
