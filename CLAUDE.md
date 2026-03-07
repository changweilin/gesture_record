# CLAUDE.md — GestureRecord Codebase Guide

This file provides AI assistants with a comprehensive overview of the GestureRecord Android project: its architecture, development workflows, conventions, and key implementation details.

---

## Project Overview

**GestureRecord** is an Android application that records and replays touch gestures and smart clicks using the Android Accessibility Service API. Users can:
- Record gesture paths (finger swipes, taps) as reusable sequences
- Record "smart clicks" that target specific UI elements by accessibility node
- Organize gestures into named combinations
- Play back gestures on demand via a floating overlay UI

**Tech Stack**:
- Language: Kotlin
- Platform: Android (minSdk 24, targetSdk 34)
- Architecture: MVVM + Room + Coroutines
- Build: Gradle 8.4 (Kotlin DSL)

---

## Repository Structure

```
gesture_record/
├── app/
│   ├── build.gradle.kts                     # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml              # Permissions, services, activities
│       ├── java/com/example/gesturerecord/
│       │   ├── GestureApp.kt                # Application class; Room DB singleton
│       │   ├── MainActivity.kt              # Main UI: list of gesture combinations
│       │   ├── MainViewModel.kt             # ViewModel; CRUD via StateFlow
│       │   ├── GestureCombinationAdapter.kt # RecyclerView ListAdapter
│       │   ├── data/
│       │   │   ├── Entities.kt              # Room entities: GestureCombination, GestureItem
│       │   │   ├── GestureDatabase.kt       # Room DB definition and singleton
│       │   │   └── GestureDao.kt            # DAO: queries for combinations and items
│       │   └── service/
│       │       ├── GestureAccessibilityService.kt  # Gesture dispatch + smart click recording
│       │       ├── OverlayService.kt               # Floating overlay UI and gesture logic
│       │       └── TransparentCaptureView.kt       # Custom view for touch path recording
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── layout_overlay.xml
│           │   └── item_gesture_combination.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/
│               └── accessibility_service_config.xml
├── build.gradle.kts                         # Root build config
├── settings.gradle.kts                      # Single-module project settings
├── gradle.properties                        # JVM args, AndroidX, Kotlin code style
└── gradle/wrapper/
    └── gradle-wrapper.properties            # Gradle 8.4 distribution URL
```

---

## Architecture

### MVVM Pattern

```
View (Activity/Overlay) → ViewModel → Repository (DAO) → Room DB
```

- **`MainActivity`**: Observes `StateFlow<List<GestureCombination>>` from `MainViewModel` and renders the RecyclerView. Handles user actions (add, edit, delete, duplicate) through ViewModel calls.
- **`MainViewModel`**: Holds all UI state via `StateFlow`. Executes all database operations in `viewModelScope` using coroutines.
- **`GestureDao`**: Provides `Flow<List<GestureCombination>>` for reactive updates to the combination list.
- **`GestureApp`**: Application class provides a lazy-initialized `GestureDatabase` instance accessible globally via `(application as GestureApp).database`.

### Services

The app uses two Android services that work together:

| Service | Type | Purpose |
|---|---|---|
| `OverlayService` | Foreground Service | Manages floating overlay UI, records gesture paths, stores temporary gesture slots |
| `GestureAccessibilityService` | AccessibilityService | Dispatches gestures on screen, records smart clicks via accessibility events |

### Data Model

**`GestureCombination`** — top-level record:
```kotlin
@Entity(tableName = "gesture_combinations")
data class GestureCombination(
    val id: Int = 0,        // Primary key (autoGenerate)
    val name: String,
    val orderIndex: Int
)
```

**`GestureItem`** — individual action within a combination:
```kotlin
@Entity(
    tableName = "gesture_items",
    foreignKeys = [ForeignKey(
        entity = GestureCombination::class,
        parentColumns = ["id"],
        childColumns = ["combinationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class GestureItem(
    val id: Int = 0,
    val combinationId: Int,     // FK to GestureCombination
    val orderIndex: Int,
    val actionType: Int,        // 0 = path gesture, 1 = smart click
    val gestureData: String     // JSON-serialized gesture path or click target
)
```

`actionType` constants:
- `0` — Path gesture: `gestureData` contains serialized touch path coordinates and duration
- `1` — Smart click: `gestureData` contains the accessibility node descriptor for a UI element

---

## Development Workflows

### Building

Use the Gradle wrapper — never install Gradle globally:

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK
./gradlew build                # Full build (including tests)
./gradlew clean                # Clean build artifacts
```

Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Running Tests

```bash
./gradlew test                     # Unit tests (JVM)
./gradlew connectedAndroidTest     # Instrumented tests (requires connected device/emulator)
```

Test frameworks configured:
- **JUnit 4** (4.13.2) for unit tests
- **Espresso** (3.5.1) for UI/instrumented tests
- Test runner: `AndroidJUnitRunner`

### Linting

```bash
./gradlew lint                 # Run Android Lint
./gradlew lintDebug            # Lint debug variant only
```

No custom ktlint or detekt configuration is present. Follow standard Android Lint rules and Kotlin official code style.

---

## Code Conventions

### Kotlin Style

- **Kotlin code style**: `official` (set in `gradle.properties`)
- Class names: `PascalCase` (e.g., `GestureAccessibilityService`)
- Functions and variables: `camelCase` (e.g., `dispatchGestureItem()`)
- Constants: `SCREAMING_SNAKE_CASE` inside companion objects
- Use Kotlin idioms: `?.let {}`, `apply {}`, `also {}`, data classes, sealed classes

### Coroutines

- All database operations run on `Dispatchers.IO` within `viewModelScope`
- UI state updates are done via `StateFlow` and `MutableStateFlow`
- Never perform database operations on the main thread

```kotlin
// Correct pattern:
viewModelScope.launch(Dispatchers.IO) {
    database.gestureDao().insertCombination(combination)
}
```

### Room Database

- Database version: **2** with `fallbackToDestructiveMigration()` enabled
- Always provide proper migrations or use `fallbackToDestructiveMigration()` for dev builds
- Access database only through DAO interfaces
- Use `Flow<List<T>>` for reactive queries; use `suspend` functions for writes

### Services

- `OverlayService` communicates with `GestureAccessibilityService` via static singleton pattern (`GestureAccessibilityService.instance`)
- Always null-check `GestureAccessibilityService.instance` before calling methods on it
- Overlay windows use `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` (requires `SYSTEM_ALERT_WINDOW` permission)

### Android Manifest

When adding new features requiring permissions, add them to `AndroidManifest.xml` and handle runtime permission requests in `MainActivity` (see existing pattern for `SYSTEM_ALERT_WINDOW` and Accessibility Service checks).

---

## Key Implementation Details

### Gesture Recording Flow

1. User taps "Record" in the overlay → `OverlayService` shows `TransparentCaptureView`
2. `TransparentCaptureView.onTouchEvent()` collects path points and total duration
3. On lift, the recorded gesture is stored in one of 9 temporary slot arrays in `OverlayService`
4. User presses "Save" → gesture slots are serialized and inserted into Room as `GestureItem` records

### Gesture Playback Flow

1. User taps a slot in the overlay → `OverlayService` calls `GestureAccessibilityService.instance?.dispatchGestureItem(item)`
2. For path gestures (`actionType == 0`): constructs a `GestureDescription` with `StrokeDescription` and calls `dispatchGesture()`
3. For smart clicks (`actionType == 1`): finds the target accessibility node by descriptor and calls `performAction(ACTION_CLICK)`

### Smart Click Recording

1. User activates "Smart Click" record mode → `GestureAccessibilityService` begins monitoring `AccessibilityEvent.TYPE_VIEW_CLICKED`
2. On next click event, it captures the node info (package, class, resource ID, text) and stores it
3. This descriptor is serialized into `gestureData` of a `GestureItem` with `actionType == 1`

### Overlay UI Positioning

The overlay header is draggable. `OverlayService` uses `WindowManager.updateViewLayout()` to reposition the overlay as the user drags it. The overlay layout is defined in `res/layout/layout_overlay.xml`.

---

## Required System Permissions

The app requires two special user-granted permissions that cannot be granted at install time:

| Permission | How to Grant | Check in Code |
|---|---|---|
| Overlay (`SYSTEM_ALERT_WINDOW`) | Settings → Apps → Special App Access → Display over other apps | `Settings.canDrawOverlays(context)` |
| Accessibility Service | Settings → Accessibility → GestureRecord | `AccessibilityManager.isEnabled` + service check |

`MainActivity` checks both on `onResume()` and prompts the user to enable them if missing.

---

## Database Schema

**Table: `gesture_combinations`**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER | PK, autoincrement |
| name | TEXT | User-defined name |
| orderIndex | INTEGER | Display order |

**Table: `gesture_items`**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER | PK, autoincrement |
| combinationId | INTEGER | FK → gesture_combinations(id) CASCADE DELETE |
| orderIndex | INTEGER | Playback order within combination |
| actionType | INTEGER | 0=path gesture, 1=smart click |
| gestureData | TEXT | Serialized gesture payload |

---

## Adding New Features — Checklist

When implementing new features:

1. **Data changes**: Update `Entities.kt`, increment `GestureDatabase.DATABASE_VERSION`, add a `Migration` object (or accept destructive migration for dev)
2. **New queries**: Add methods to `GestureDao.kt`
3. **Business logic**: Add to `MainViewModel.kt` using coroutines; expose state via `StateFlow`
4. **UI updates**: Update `MainActivity.kt` or add new Activities/Fragments; observe new StateFlow fields
5. **Services**: If the feature involves recording/playback, modify `OverlayService` or `GestureAccessibilityService`
6. **Permissions**: Add to `AndroidManifest.xml` and handle in `MainActivity.onResume()`
7. **Layout**: Add or update XML layouts in `res/layout/`
8. **Strings**: Add all user-facing text to `res/values/strings.xml` — never hardcode strings

---

## Common Pitfalls

- **Do not call `GestureAccessibilityService.instance` without null check** — the service may not be running if the user hasn't enabled it in Settings
- **Do not perform DB operations on the main thread** — Room will throw an exception; always use `Dispatchers.IO`
- **Database version must be incremented** when changing schema — forgetting causes crashes on upgrade unless `fallbackToDestructiveMigration()` is used
- **Overlay permission is not a standard runtime permission** — use `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` intent to direct the user to the settings page
- **`TYPE_APPLICATION_OVERLAY` window type requires API 26+** — the minSdk is 24, so use appropriate API level guards if adding new window features
- **Accessibility service config** (`res/xml/accessibility_service_config.xml`) must be kept in sync with the service capabilities declared in `AndroidManifest.xml`

---

## No CI/CD Currently Configured

There are no GitHub Actions, GitLab CI, or other pipeline configurations. All builds and tests must be run locally via the Gradle wrapper commands listed above.
