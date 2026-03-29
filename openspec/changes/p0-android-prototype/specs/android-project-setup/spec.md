# Capability: android-project-setup

Set up the Android project with Kotlin, Jetpack Compose, Material 3, and the required dependency stack.

## ADDED Requirements

### Requirement: Project Structure and Build Configuration

The Android project SHALL use Kotlin as the primary language with Jetpack Compose for UI. The project SHALL target the latest stable Android SDK (SDK 35) with a minimum SDK of 26 (Android 8.0). The build system SHALL be Gradle with Kotlin DSL (`.kts`). A `buildConfigField` SHALL be defined for the default relay URL.

#### Scenario: Project builds successfully

WHEN the project is opened in Android Studio and a Gradle sync is performed
THEN the sync completes without errors
AND `./gradlew assembleDebug` produces a valid APK

#### Scenario: APK installs on API 26+ device

WHEN the debug APK is installed on a device or emulator running Android API 26
THEN the installation succeeds
AND the app launches without crash

WHEN the debug APK is attempted on a device running API 25
THEN the installation is rejected (minSdk constraint)

#### Scenario: Compose preview renders

WHEN a `@Preview` annotated composable is opened in Android Studio
THEN the preview renders without errors

### Requirement: Dependency Stack

The project SHALL include the following dependencies:
- **OkHttp** (4.x): HTTP client and WebSocket support.
- **Coroutines** (kotlinx-coroutines-android): async operations and Flow.
- **Hilt** (dagger-hilt-android): dependency injection.
- **Material 3** (androidx.compose.material3): UI components.
- **Compose BOM**: version-aligned Compose dependencies.

Room is included as a dependency but NOT used in the prototype phase.

#### Scenario: OkHttp available for WebSocket use

WHEN code imports `okhttp3.OkHttpClient` and `okhttp3.WebSocket`
THEN compilation succeeds

#### Scenario: Hilt injection works

WHEN a `@HiltViewModel` annotated ViewModel is created
AND the Application class is annotated with `@HiltAndroidApp`
THEN Hilt generates the required components at compile time
AND the ViewModel is injectable via `hiltViewModel()`

### Requirement: Build Config Field for Relay URL

The `build.gradle.kts` SHALL define a `buildConfigField` named `DEFAULT_RELAY_URL` with a configurable default value. This allows the relay URL to be baked into the build while remaining overridable at runtime via settings UI.

#### Scenario: BuildConfig field accessible at runtime

WHEN code references `BuildConfig.DEFAULT_RELAY_URL`
THEN the value matches what was set in `build.gradle.kts`
