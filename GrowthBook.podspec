Pod::Spec.new do |spec|
    spec.name                     = 'GrowthBook'
    spec.version       = "1.0.6"
    spec.homepage                 = 'https://github.com/growthbook/growthbook-kotlin'
    spec.source                   = { :git => "https://github.com/growthbook/growthbook-kotlin.git"}
    spec.authors                  = 'nicholaspearson918@gmail.com'
    spec.license                  = 'https://opensource.org/licenses/MIT'
    spec.summary                  = 'Powerful A/B testing SDK for Swift - iOS'

    spec.vendored_frameworks      = "XCFramework/GrowthBook.xcframework"

    spec.ios.deployment_target = '12.0'

    spec.user_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64' }
    spec.pod_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64' }


end
