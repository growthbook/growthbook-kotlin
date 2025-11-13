#!/bin/bash

# GrowthBook Kotlin 1.x Migration Script
# This script automates the creation of the kotlin-1x branch and applies necessary changes

set -e  # Exit on error

echo "🚀 Starting GrowthBook Kotlin 1.x migration..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Check if we're in the right directory
if [ ! -f "settings.gradle.kts" ]; then
    print_error "Error: Not in the root directory of GrowthBook Kotlin project"
    exit 1
fi

# Confirm with user
echo ""
echo "This script will:"
echo "  1. Create a new 'kotlin-1x' branch"
echo "  2. Update Kotlin version to 1.9.24"
echo "  3. Update dependencies to compatible versions"
echo "  4. Apply code changes for Kotlin 1.x compatibility"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Step 1: Create kotlin-1x branch
echo ""
echo "📝 Step 1: Creating kotlin-1x branch..."
git checkout -b kotlin-1x || {
    print_warning "Branch kotlin-1x already exists. Checking out..."
    git checkout kotlin-1x
}
print_status "Branch kotlin-1x ready"

# Step 2: Update root build.gradle.kts
echo ""
echo "📝 Step 2: Updating root build.gradle.kts..."
sed -i.backup 's/val kotlinPluginsVersion = "2.1.20"/val kotlinPluginsVersion = "1.9.24"/' build.gradle.kts
sed -i.backup 's/com.android.tools.build:gradle:8.0.2/com.android.tools.build:gradle:7.4.2/' build.gradle.kts
print_status "Root build.gradle.kts updated"

# Step 3: Update libs.versions.toml
echo ""
echo "📝 Step 3: Updating dependency versions..."
cat > gradle/libs.versions.toml << 'EOF'
[versions]
kotlinxCoroutinesCore = "1.7.3"
kotlinxSerializationJson = "1.6.3"
cryptography = "0.4.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutinesCore" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
cryptography-core = { module = "dev.whyoleg.cryptography:cryptography-core", version.ref = "cryptography" }
cryptography-provider-jdk = { module = "dev.whyoleg.cryptography:cryptography-provider-jdk", version.ref = "cryptography" }
cryptography-provider-webcrypto = { module = "dev.whyoleg.cryptography:cryptography-provider-webcrypto", version.ref = "cryptography" }
EOF
print_status "libs.versions.toml updated"

# Step 4: Update NetworkDispatcherKtor to use Ktor 2.x
echo ""
echo "📝 Step 4: Updating Ktor version..."
sed -i.backup 's/val ktorVersion = "3.1.2"/val ktorVersion = "2.3.12"/' NetworkDispatcherKtor/build.gradle.kts
print_status "Ktor version updated to 2.3.12"

# Step 5: Update version numbers with -k1x suffix
echo ""
echo "📝 Step 5: Updating version numbers..."
sed -i.backup 's/version = "6.1.1"/version = "6.1.1-k1x"/' GrowthBook/build.gradle.kts
sed -i.backup 's/version = "1.1.1"/version = "1.1.1-k1x"/' Core/build.gradle.kts
sed -i.backup 's/version = "1.0.6"/version = "1.0.6-k1x"/' NetworkDispatcherKtor/build.gradle.kts
print_status "Version numbers updated with -k1x suffix"

# Step 6: Remove WASM targets from all build files
echo ""
echo "📝 Step 6: Removing WASM targets..."
for file in GrowthBook/build.gradle.kts Core/build.gradle.kts NetworkDispatcherKtor/build.gradle.kts; do
    if [ -f "$file" ]; then
        # Remove @file:OptIn(ExperimentalWasmDsl::class)
        sed -i.backup '/^@file:OptIn(ExperimentalWasmDsl::class)/d' "$file"
        # Remove import statement
        sed -i.backup '/^import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl/d' "$file"
        # Remove wasmJs block (this is a simple approach, might need manual verification)
        sed -i.backup '/wasmJs {/,/}/d' "$file"
        # Remove wasmJsMain source set references
        sed -i.backup '/val wasmJsMain by getting/,/}/d' "$file"
    fi
done
print_status "WASM targets removed"

# Step 7: Update Constants.kt to replace data object with object
echo ""
echo "📝 Step 7: Fixing Kotlin 2.x specific syntax..."
CONSTANTS_FILE="GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/utils/Constants.kt"
if [ -f "$CONSTANTS_FILE" ]; then
    sed -i.backup 's/data object NotPresent : OptionalProperty<Nothing>()/object NotPresent : OptionalProperty<Nothing>()/' "$CONSTANTS_FILE"
    print_status "Constants.kt updated (data object -> object)"
else
    print_warning "Could not find Constants.kt"
fi

# Step 8: Clean up backup files
echo ""
echo "📝 Step 8: Cleaning up backup files..."
find . -name "*.backup" -delete
print_status "Backup files cleaned"

# Step 9: Create README for kotlin-1x branch
echo ""
echo "📝 Step 9: Creating README-K1X.md..."
cat > README-K1X.md << 'EOF'
# GrowthBook Kotlin SDK - Kotlin 1.x Compatible Version

> ⚠️ **Enterprise Support Version**: This is a Kotlin 1.x compatible version 
> specifically maintained for enterprise customers on Kotlin 1.9.x platforms.
> 
> For new projects, please use the main version with Kotlin 2.x support.

## Kotlin Version

This branch is built with **Kotlin 1.9.24** and compatible dependencies.

## Installation

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("io.growthbook.sdk:GrowthBook:6.1.1-k1x")
    
    // Network Dispatcher (choose one)
    implementation("io.growthbook.sdk:NetworkDispatcherKtor:1.0.6-k1x")  // Ktor 2.3.x
    implementation("io.growthbook.sdk:NetworkDispatcherOkHttp:1.0.2-k1x")
}
```

**Gradle (Groovy DSL):**
```groovy
dependencies {
    implementation 'io.growthbook.sdk:GrowthBook:6.1.1-k1x'
    
    // Network Dispatcher (choose one)
    implementation 'io.growthbook.sdk:NetworkDispatcherKtor:1.0.6-k1x'
    implementation 'io.growthbook.sdk:NetworkDispatcherOkHttp:1.0.2-k1x'
}
```

## Differences from Main Version

| Feature | Main (Kotlin 2.x) | This Branch (Kotlin 1.x) |
|---------|-------------------|--------------------------|
| Kotlin Version | 2.1.20 | 1.9.24 |
| Ktor Version | 3.1.2 | 2.3.12 |
| WASM Target | ✅ Supported | ❌ Not supported |
| API Compatibility | ✅ Identical | ✅ Identical |

## Supported Platforms

- ✅ Android (API 21+)
- ✅ JVM (Java 8+)
- ✅ iOS (iosX64, iosArm64, iosSimulatorArm64)
- ✅ JavaScript (Browser & Node.js)
- ❌ WASM (not supported)

## Usage

The API is **100% compatible** with the main version. See the [main README](README.md) for complete usage documentation.

```kotlin
val sdkInstance: GrowthBookSDK = GBSDKBuilder(
    apiKey = "<API_KEY>",
    hostURL = "<GrowthBook_URL>",
    attributes = mapOf("id" to "user-123"),
    trackingCallback = { experiment, result -> 
        // Track your experiments
    },
    networkDispatcher = GBNetworkDispatcherKtor()
).initialize()
```

## Migration to Kotlin 2.x

When your project upgrades to Kotlin 2.x:

1. Update your dependency from `6.1.1-k1x` to `6.1.1` (or latest)
2. Change artifact name from `GrowthBook-k1x` to `GrowthBook` (if using suffix naming)
3. No code changes required! The API is identical.

```kotlin
// Before (Kotlin 1.x)
implementation("io.growthbook.sdk:GrowthBook:6.1.1-k1x")

// After (Kotlin 2.x)
implementation("io.growthbook.sdk:GrowthBook:6.1.1")
```

## Support

This version receives:
- ✅ Critical bug fixes
- ✅ Security updates
- ❌ New features (main branch only)

For issues specific to this Kotlin 1.x version, please tag your issue with `kotlin-1x`.

## License

MIT License - Same as the main project.
EOF
print_status "README-K1X.md created"

# Step 10: Create migration guide
echo ""
echo "📝 Step 10: Creating MIGRATION_NOTES.md..."
cat > MIGRATION_NOTES.md << 'EOF'
# Migration Notes - Kotlin 1.x Branch

## Changes Applied

### Build Configuration
- Kotlin version: `2.1.20` → `1.9.24`
- Android Gradle Plugin: `8.0.2` → `7.4.2`
- Ktor: `3.1.2` → `2.3.12`
- kotlinx-coroutines: `1.9.0` → `1.7.3`
- kotlinx-serialization: `1.7.3` → `1.6.3`

### Code Changes
1. **Constants.kt**
   - Changed `data object NotPresent` to `object NotPresent`
   - Reason: `data object` is a Kotlin 2.0+ feature

2. **Removed WASM Targets**
   - Removed `wasmJs` target from all modules
   - Removed `@file:OptIn(ExperimentalWasmDsl::class)`
   - Removed `wasmJsMain` source sets

3. **Version Suffix**
   - All module versions now include `-k1x` suffix
   - e.g., `6.1.1-k1x`

## Manual Steps Required

### 1. Base64 Implementation
The current code uses `kotlin.io.encoding.Base64` which is experimental in Kotlin 1.x.

**Option A: Add Okio dependency (Recommended)**
Add to `GrowthBook/build.gradle.kts`:
```kotlin
commonMain {
    dependencies {
        implementation("com.squareup.okio:okio:3.5.0")
    }
}
```

Then update `Crypto.kt`:
```kotlin
import okio.ByteString.Companion.decodeBase64

fun decodeBase64(base64: String): ByteArray {
    return base64.decodeBase64()?.toByteArray() 
        ?: throw IllegalArgumentException("Invalid Base64")
}
```

**Option B: Use platform-specific implementations**
Create expect/actual for each platform using native Base64.

### 2. Test the Build
```bash
./gradlew clean build
./gradlew test
```

### 3. Verify Publications
```bash
./gradlew publishToMavenLocal
```

Then check in `~/.m2/repository/io/growthbook/sdk/GrowthBook/6.1.1-k1x/`

### 4. Update CI/CD
Create `.github/workflows/kotlin-1x.yml` based on your main workflow.

## Maintenance Workflow

### Cherry-picking Bug Fixes from Main
```bash
# On main branch
git checkout main
git log --oneline -n 10  # Find the commit hash

# On kotlin-1x branch
git checkout kotlin-1x
git cherry-pick <commit-hash>

# Resolve conflicts if any
git add .
git commit
git push origin kotlin-1x
```

### Releasing a New Version
```bash
# On kotlin-1x branch
# 1. Update version in build.gradle.kts files
# 2. Commit changes
git add .
git commit -m "Release 6.1.2-k1x"
git tag v6.1.2-k1x
git push origin kotlin-1x
git push origin v6.1.2-k1x

# 3. GitHub Actions should automatically publish
```

## Testing Checklist

- [ ] All unit tests pass
- [ ] Android integration test
- [ ] JVM integration test
- [ ] iOS integration test (if applicable)
- [ ] JS integration test (if applicable)
- [ ] Maven publication works
- [ ] Documentation is clear

## Rollback Plan

If issues arise:
```bash
git checkout main
git branch -D kotlin-1x
git push origin --delete kotlin-1x
```

The main branch remains unaffected.

## Questions?

Contact the GrowthBook team or open an issue tagged with `kotlin-1x`.
EOF
print_status "MIGRATION_NOTES.md created"

# Completion
echo ""
echo "═══════════════════════════════════════════════════════════"
echo -e "${GREEN}✓ Migration script completed successfully!${NC}"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "Next steps:"
echo ""
echo "1. Review the changes:"
echo "   git diff main"
echo ""
echo "2. Address Base64 implementation (see MIGRATION_NOTES.md)"
echo ""
echo "3. Test the build:"
echo "   ./gradlew clean build"
echo ""
echo "4. Run tests:"
echo "   ./gradlew test"
echo ""
echo "5. Review and commit:"
echo "   git add ."
echo "   git commit -m 'Migrate to Kotlin 1.9.24 for enterprise support'"
echo ""
echo "6. Push to remote:"
echo "   git push origin kotlin-1x"
echo ""
echo "For detailed information, see:"
echo "  - KOTLIN_1X_DOWNPORT_STRATEGY.md (strategy document)"
echo "  - MIGRATION_NOTES.md (technical changes)"
echo "  - README-K1X.md (user documentation)"
echo ""

