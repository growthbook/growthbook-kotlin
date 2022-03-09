// swift-tools-version:5.5
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "GrowthBook",
    platforms: [
        .iOS(.v12), .tvOS(.v13), .watchOS(.v7)
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
    url: "https://github.com/growthbook/growthbook-kotlin/releases/download/1.0.7/GrowthBook.xcframework.zip",
    checksum: "89b4735fd947dd1c5c5e4dc65c540af2d2895856916fd440b7bfecb988533b5a"
        ),
    ]
)
