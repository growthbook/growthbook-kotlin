Pod::Spec.new do |spec|
    spec.name                     = 'GrowthBook'
    spec.version       = "1.0.7"
    spec.homepage                 = 'https://github.com/growthbook/growthbook-kotlin'
    spec.source       = { :http => "https://github.com/growthbook/growthbook-kotlin/releases/download/1.0.7/GrowthBook.xcframework.zip"}
    spec.authors                  = 'nicholaspearson918@gmail.com'
    spec.license                  = 'https://opensource.org/licenses/MIT'
    spec.summary                  = 'Powerful A/B testing SDK for Swift - iOS'

    spec.vendored_frameworks      = "GrowthBook.xcframework"

    spec.ios.deployment_target = '12.0'
    spec.watchos.deployment_target = '7.0'
    spec.tvos.deployment_target = '13.0'


    spec.user_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=*simulator*]' => 'arm64' }
    spec.pod_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=*simulator*]' => 'arm64' }

end
