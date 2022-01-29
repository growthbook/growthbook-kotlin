// swift-tools-version:5.3
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "GrowthBook",
    platforms: [
        .iOS(.v12),
    ],
    products: [
        .library(
            name: "GrowthBook",
            targets: ["GrowthBook"]),
    ],
    dependencies: [
        // no dependencies
    ],
    targets: [
        .binaryTarget(
            name: "GrowthBook",
            path: "XCFramework/GrowthBook.xcframework"
        ),
    ]
)
