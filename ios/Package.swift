// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "TranscribeCare",
    platforms: [
        .iOS(.v17)
    ],
    dependencies: [
        .package(url: "https://github.com/typelift/SwiftCheck.git", from: "0.12.0")
    ],
    targets: [
        .target(
            name: "TranscribeCare",
            path: "TranscribeCare"
        ),
        .testTarget(
            name: "TranscribeCareTests",
            dependencies: [
                "TranscribeCare",
                "SwiftCheck"
            ],
            path: "TranscribeCareTests"
        )
    ]
)
