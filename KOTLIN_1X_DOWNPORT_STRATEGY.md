# GrowthBook Kotlin SDK - Kotlin 1.x Downport Strategy

## Executive Summary

This document outlines a comprehensive strategy to create and maintain a Kotlin 1.x compatible version of the GrowthBook SDK (currently on Kotlin 2.1.20) for enterprise customers who cannot upgrade their platforms.

## Current State Analysis

### Project Overview
- **Current Kotlin Version**: 2.1.20
- **Current SDK Version**: 6.1.1
- **Architecture**: Kotlin Multiplatform (Android, JVM, iOS, JS, WASM)
- **Key Dependencies**:
  - kotlinx-coroutines-core: 1.9.0
  - kotlinx-serialization-json: 1.7.3
  - Ktor: 3.1.2
  - cryptography-core: 0.4.0

### Kotlin 2.x Features Identified

1. **`data object` (Kotlin 2.0+)**
   - Location: `GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/utils/Constants.kt:319`
   - Usage: `data object NotPresent : OptionalProperty<Nothing>()`

2. **`@OptIn(ExperimentalEncodingApi::class)` for Base64**
   - Location: `GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/utils/Crypto.kt`
   - The stdlib Base64 API is experimental in Kotlin 1.x

3. **WASM Target (wasmJs)**
   - WASM support was significantly improved in Kotlin 2.0

## Recommended Strategy: Git Branch-Based Approach

### Option 1: Separate `kotlin-1x` Branch (RECOMMENDED)

This is the most maintainable approach for a one-time enterprise release.

#### Implementation Plan

```
main (Kotlin 2.x)
  │
  ├─── kotlin-1x (Kotlin 1.9.24) ← Downported version
```

#### Advantages
✅ Clean separation of codebases
✅ Minimal CI/CD changes
✅ Clear versioning strategy (e.g., 6.1.1-k1x)
✅ Can cherry-pick critical bug fixes
✅ No impact on main development
✅ Easy to sunset when customer upgrades

#### Implementation Steps

1. **Create the kotlin-1x branch**
   ```bash
   git checkout -b kotlin-1x
   ```

2. **Downgrade Kotlin version to 1.9.24** (latest stable 1.x)
   - Update `build.gradle.kts`
   - Update dependency versions to compatible ones

3. **Replace Kotlin 2.x features**:
   - Replace `data object` with `object`
   - Replace stdlib Base64 with okio or manual implementation
   - Remove WASM targets
   - Update AGP (Android Gradle Plugin) to compatible version

4. **Version Strategy**:
   - Main branch: `6.1.1`, `6.2.0`, etc.
   - kotlin-1x branch: `6.1.1-k1x`, `6.1.2-k1x`, etc.

5. **Publication Strategy**:
   - Publish with a different artifact suffix
   - Maven coordinates: `io.growthbook.sdk:GrowthBook-k1x:6.1.1-k1x`
   - Or use a different group ID: `io.growthbook.sdk.k1x:GrowthBook:6.1.1`

### Option 2: Gradle Source Sets with Version Catalogs

Create a multi-version build system using Gradle's capabilities.

#### Advantages
✅ Single codebase
✅ Shared common code
✅ Automated compatibility checks

#### Disadvantages
❌ Complex build configuration
❌ Harder to maintain
❌ Not ideal for "one-time" releases

### Option 3: Separate Repository

Create a new repository specifically for Kotlin 1.x support.

#### Advantages
✅ Complete independence
✅ No risk of accidental merges

#### Disadvantages
❌ Duplicate work for bug fixes
❌ Harder to keep in sync
❌ More maintenance overhead

## Detailed Migration Changes Required

### 1. Build Configuration Changes

#### Root `build.gradle.kts`
```kotlin
// Change from:
val kotlinPluginsVersion = "2.1.20"

// To:
val kotlinPluginsVersion = "1.9.24" // Latest stable 1.x
```

#### Dependency Version Compatibility Matrix

| Dependency | Kotlin 2.x Version | Kotlin 1.x Compatible Version |
|------------|-------------------|-------------------------------|
| kotlinx-coroutines | 1.9.0 | 1.7.3 |
| kotlinx-serialization | 1.7.3 | 1.6.3 |
| Ktor | 3.1.2 | 2.3.12 |
| Android Gradle Plugin | 8.0.2 | 7.4.2 |
| Dokka | 1.9.10 | 1.9.10 (compatible) |

### 2. Code Changes Required

#### A. Replace `data object` with `object`

**File**: `GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/utils/Constants.kt`

```kotlin
// Kotlin 2.x (current)
sealed class OptionalProperty<out T> {
    data object NotPresent : OptionalProperty<Nothing>()
    data class Present<T>(val value: T) : OptionalProperty<T>()
}

// Kotlin 1.x (compatible)
sealed class OptionalProperty<out T> {
    object NotPresent : OptionalProperty<Nothing>()
    data class Present<T>(val value: T) : OptionalProperty<T>()
}
```

**Impact**: The `data object` doesn't provide significant benefits over `object` for a singleton in a sealed hierarchy. The `equals()`, `hashCode()`, and `toString()` implementations are not materially different for this use case.

#### B. Replace Kotlin stdlib Base64 with Compatible Implementation

**File**: `GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/utils/Crypto.kt`

**Option 1: Use Okio** (Recommended)
```kotlin
// Add dependency
// implementation("com.squareup.okio:okio:3.5.0") // Compatible with Kotlin 1.x

// Replace:
@OptIn(ExperimentalEncodingApi::class)
fun decodeBase64(base64: String): ByteArray {
    return Base64.decode(base64)
}

// With:
import okio.ByteString.Companion.decodeBase64

fun decodeBase64(base64: String): ByteArray {
    return base64.decodeBase64()?.toByteArray() 
        ?: throw IllegalArgumentException("Invalid Base64")
}
```

**Option 2: Custom Implementation** (No new dependency)
```kotlin
// Create a simple Base64 decoder using standard charset encoding
// This is multiplatform compatible
expect object Base64Compat {
    fun decode(base64: String): ByteArray
    fun encode(bytes: ByteArray): String
}

// Then implement for each platform:
// - JVM: use java.util.Base64
// - Android: use android.util.Base64
// - iOS/Native: use NSData
// - JS: use btoa/atob
```

#### C. Remove/Disable WASM Targets

**Files**: All `build.gradle.kts` files

```kotlin
// Remove these lines:
@file:OptIn(ExperimentalWasmDsl::class)
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// In kotlin { } block, remove:
wasmJs {
    nodejs()
}

// Remove wasmJsMain source sets
```

### 3. Platform-Specific Considerations

#### Android
- Ensure AGP 7.4.2 compatibility
- Min SDK remains at 21 (compatible)
- Compile SDK can stay at 34

#### iOS
- Kotlin 1.9.24 supports iOS targets
- No changes needed

#### JVM
- Continue targeting JVM 1.8
- No changes needed

#### JS
- Kotlin 1.9.x has stable JS support
- No changes needed

### 4. Testing Strategy

#### Compatibility Test Matrix
```
┌─────────────────┬──────────────┬──────────────┐
│ Test Category   │ Kotlin 2.x   │ Kotlin 1.x   │
├─────────────────┼──────────────┼──────────────┤
│ Unit Tests      │ ✓ Run all    │ ✓ Run all    │
│ Integration     │ ✓ Run all    │ ✓ Run all    │
│ Platform Tests  │ All targets  │ No WASM      │
│ Serialization   │ ✓            │ ✓            │
│ Crypto          │ ✓            │ ✓ (validate) │
│ Networking      │ Ktor 3.x     │ Ktor 2.x     │
└─────────────────┴──────────────┴──────────────┘
```

#### Test Plan
1. Run existing test suite on kotlin-1x branch
2. Add version-specific tests if needed
3. Test with customer's actual Kotlin 1.x environment
4. Validate Maven publication works correctly

### 5. CI/CD Pipeline

#### GitHub Actions Workflow

```yaml
name: Kotlin 1.x Build

on:
  push:
    branches: [ kotlin-1x ]
  pull_request:
    branches: [ kotlin-1x ]

jobs:
  build:
    runs-on: macos-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Run tests
      run: ./gradlew test
    
    - name: Publish to Maven (on tag)
      if: startsWith(github.ref, 'refs/tags/v')
      run: ./gradlew publish
      env:
        GB_SONATYPE_USERNAME: ${{ secrets.GB_SONATYPE_USERNAME }}
        GB_SONATYPE_PASSWORD: ${{ secrets.GB_SONATYPE_PASSWORD }}
        GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
        GPG_PRIVATE_PASSWORD: ${{ secrets.GPG_PRIVATE_PASSWORD }}
```

### 6. Documentation Updates

#### README-K1X.md (for kotlin-1x branch)

```markdown
# GrowthBook Kotlin SDK - Kotlin 1.x Compatible Version

> ⚠️ **Enterprise Support Version**: This is a Kotlin 1.x compatible version 
> specifically maintained for enterprise customers on Kotlin 1.9.x platforms.
> 
> For new projects, please use the main version with Kotlin 2.x support.

## Installation

```kotlin
dependencies {
    implementation 'io.growthbook.sdk:GrowthBook-k1x:6.1.1-k1x'
    
    // Network Dispatcher (Ktor 2.x)
    implementation 'io.growthbook.sdk:NetworkDispatcherKtor-k1x:1.0.6-k1x'
}
```

## Differences from Main Version

- Built with Kotlin 1.9.24
- Uses Ktor 2.3.x instead of 3.x
- No WASM target support
- Compatible with older Android Gradle Plugin versions

## Migration Path

When your project upgrades to Kotlin 2.x:
1. Change dependency from `GrowthBook-k1x` to `GrowthBook`
2. Update version to latest (6.x.x)
3. No API changes required - fully compatible!
```

### 7. Maintenance Strategy

#### For Critical Bug Fixes
```bash
# Fix in main branch first
git checkout main
# ... make fix ...
git commit -m "Fix critical bug XYZ"

# Cherry-pick to kotlin-1x
git checkout kotlin-1x
git cherry-pick <commit-hash>
# Resolve any conflicts if necessary
git push origin kotlin-1x
```

#### For Feature Requests
- New features go to main branch only
- kotlin-1x branch is in maintenance mode
- Only critical bug fixes and security updates

#### Sunset Plan
- Communicate timeline with enterprise customer
- Provide 6-12 months notice before end-of-life
- Help customer plan migration to Kotlin 2.x
- Archive kotlin-1x branch (don't delete)

### 8. Publication Naming Conventions

#### Artifact Naming Strategy

**Option A: Suffix-based (RECOMMENDED)**
```kotlin
group = "io.growthbook.sdk"
artifact = "GrowthBook-k1x"
version = "6.1.1-k1x"
```

**Option B: Separate Group**
```kotlin
group = "io.growthbook.sdk.k1x"
artifact = "GrowthBook"
version = "6.1.1"
```

**Option C: Classifier-based**
```kotlin
group = "io.growthbook.sdk"
artifact = "GrowthBook"
version = "6.1.1"
classifier = "k1x"
```

### 9. Communication Plan

#### For Enterprise Customer
- Provide clear migration guide
- Offer dedicated support channel
- Set up regular sync meetings
- Share roadmap and timelines

#### For Community
- Update main README with note about k1x version
- Create clear documentation on GitHub
- Add to releases page with appropriate tags

## Implementation Timeline

### Phase 1: Setup (Week 1)
- [ ] Create kotlin-1x branch
- [ ] Update Kotlin version to 1.9.24
- [ ] Update all dependencies

### Phase 2: Code Changes (Week 1-2)
- [ ] Replace data object with object
- [ ] Implement Base64 compatibility layer
- [ ] Remove WASM targets
- [ ] Update build configurations

### Phase 3: Testing (Week 2)
- [ ] Run full test suite
- [ ] Test all platform targets
- [ ] Validate with customer's environment

### Phase 4: CI/CD & Documentation (Week 3)
- [ ] Set up CI/CD pipeline
- [ ] Update documentation
- [ ] Create README-K1X.md

### Phase 5: Publication (Week 3)
- [ ] Configure Maven publication
- [ ] Test publication locally
- [ ] Publish first release

### Phase 6: Customer Onboarding (Week 4)
- [ ] Provide integration guide
- [ ] Support customer migration
- [ ] Gather feedback

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Dependency incompatibilities | High | Thorough testing with version matrix |
| Breaking API changes | Medium | Maintain API compatibility |
| CI/CD complexity | Low | Separate workflows per branch |
| Maintenance burden | Medium | Clear sunset timeline |
| Version confusion | Low | Clear naming conventions |

## Cost-Benefit Analysis

### Costs
- **Initial Setup**: ~2-3 weeks of engineering time
- **Ongoing Maintenance**: ~2-4 hours per critical bug fix
- **CI/CD Resources**: Minimal additional cost
- **Documentation**: ~1 week initial effort

### Benefits
- **Customer Retention**: Keep enterprise customer happy
- **Market Position**: Show commitment to backward compatibility
- **Reference Case**: Use as example for other enterprise customers
- **Learning**: Experience with version management strategies

## Success Metrics

1. **Technical**
   - All tests passing on kotlin-1x branch
   - Successfully published to Maven Central
   - Zero API breaking changes

2. **Customer**
   - Successful integration by enterprise customer
   - No critical bugs in first 3 months
   - Customer satisfaction score > 8/10

3. **Maintenance**
   - Bug fix sync time < 2 days
   - Clear separation of concerns
   - Easy to understand for new team members

## Conclusion

The **Git Branch-Based Approach (Option 1)** is recommended for this one-time enterprise release because it:

1. ✅ Provides clean separation
2. ✅ Minimizes maintenance overhead
3. ✅ Allows for controlled sunset
4. ✅ Doesn't impact main development
5. ✅ Clear versioning and publication strategy

This approach balances customer needs with engineering efficiency while maintaining code quality and setting clear expectations for long-term support.

---

**Next Steps**: Review this strategy with the team and customer, then proceed with Phase 1 implementation.

