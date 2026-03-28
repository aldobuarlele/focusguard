# FocusGuard

A high-performance digital wellbeing Android application engineered to enforce intelligent app-blocking through a pure event-driven architecture. The system combines React Native UI capabilities with deep native Android OS integration, processing all logic locally for maximum privacy and zero-latency enforcement.

## Architecture & Tech Stack

### Native SQLite Engine

Phase 2 introduced a robust **Native SQLite Engine** (`DatabaseHelper.kt`) that executes directly in the Kotlin layer. This architectural decision completely decouples the blocker from the React Native JavaScript thread:

- **O(1) Query Performance** — Database operations execute on the native thread, bypassing React Native's JavaScript bridge entirely
- **Background Thread Safety** — `AccessibilityService` queries blocking rules directly without marshalling data through the JS runtime
- **Zero-Latency Enforcement** — Blocking decisions execute in microseconds, ensuring the overlay appears before the target app renders
- **UI-Independent Operation** — The blocker remains functional even when the React Native UI process is terminated by Android


### Core OS APIs

| API | Implementation | Purpose |
|-----|----------------|---------|
| `AccessibilityService` | `AccessibilityDetectionService.kt` | Real-time foreground app detection via event-driven callbacks |
| `SYSTEM_ALERT_WINDOW` | `OverlayService.kt` + `OverlayView.kt` | Type-safe overlay rendering with correct WindowManager flags |
| `DevicePolicyManager` | Phase 4 | Uninstall protection via Device Admin |
| `WorkManager` | Phase 4 | Scheduled background tasks for weekly analytics |

### Hybrid Architecture with Heavy Native Modules

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI / Frontend** | React Native (Expo Bare Workflow) | Dashboard rendering, state management, user interactions |
| **Native Core** | Kotlin | OS-level services, event processing, SQLite operations |
| **Data Persistence** | Native SQLite Engine | O(1) ultra-fast background queries without JS thread dependency |

## Overview

FocusGuard intercepts target application access at the operating system level using a sophisticated hybrid architecture. The application leverages Android's `AccessibilityService` for real-time foreground detection and `SYSTEM_ALERT_WINDOW` for overlay rendering, implementing an advanced state machine that handles Android's complex activity lifecycle edge cases without resorting to battery-draining polling mechanisms.

## Current Status

> **✓ Phase 3 Complete** — Core Blocker Logic & Gamification Operational  
> **→ Preparing for Phase 4:** Ironclad Security & Anti-Tampering Implementation

### Phase 3: Core Blocker Logic & Gamification ✓

Implemented the complete Hybrid Bypass Engine with advanced gamification and zero-battery notification system:

#### Hybrid Bypass Engine Architecture
- **O(1) ConcurrentHashMap + SQLite Persistence** — In-memory cache with disk persistence that survives OS service kills
- **Service-Resilient Design** — Bypass state persists across AccessibilityService restarts and Android memory pressure events
- **Real-time Active Bypass Monitoring** — React Native bridge provides live updates of active bypass sessions

#### The Focus Paradox Solution: Custom NumPad
- **Programmatic Custom NumPad** — Engineered to bypass Android's `FLAG_NOT_FOCUSABLE` keyboard limitation for Level 2 Math Challenge
- **Pure Programmatic Input** — No soft keyboard dependency, enabling overlay-based numeric input without focus conflicts
- **Level 2 Gamification** — Cognitive math challenges with time-based difficulty scaling

#### Battery-Zero Notification System
- **Android 13+ Chronometer Notifications** — Leverages `setUsesChronometer(true)` and `setTimeoutAfter()` for self-destructing bypass notifications
- **Zero Background Polling** — No timers, no `Handler` loops, no battery drain
- **Auto-Cleanup** — Notifications automatically dismiss after bypass completion without manual user interaction

#### Dynamic Settings via RN Bridge
- **Real-time Duration Configuration** — React Native UI controls bypass duration settings with immediate native layer propagation
- **Bidirectional Communication** — Native → JS event emission for active bypass state monitoring
- **Type-Safe Bridge** — Kotlin/JavaScript interface with Promise-based async operations and error handling

#### Advanced Event-Driven State Machine Enhancements
- **Bypass State Machine** — Extended state tracking for multi-level blocking (Nudge → Gamification → Hard Block)
- **Concurrent User Session Management** — Support for multiple overlapping bypass attempts with thread-safe synchronization

### Phase 1: Native OS Foundation ✓

Established the foundational native infrastructure:

- `AccessibilityDetectionService` — Event-driven foreground monitoring via `TYPE_WINDOW_STATE_CHANGED`
- `OverlayService` — Blocking overlay renderer with `FLAG_NOT_FOCUSABLE` and click absorption
- Direct Intent communication between services (bypasses React Native bridge)
- Lifecycle reset mechanism via `ACTION_RESET_SERVICE` to prevent zombie instances

### Phase 2: Data Persistence & UI Setup ✓

Implemented the complete data layer and React Native dashboard bridge:

#### Native SQLite Engine
- `DatabaseHelper.kt` — Singleton database manager with thread-safe SQLite operations
- Schema: `blocked_apps` table with `package_name`, `block_level`, `max_daily_minutes`, `created_at`
- Direct native queries from `AccessibilityService` without JS thread involvement

#### Advanced Event-Driven State Machine

The `AccessibilityDetectionService` implements a sophisticated state machine that solves critical Android OS edge cases without polling:

| Challenge | Solution |
|-----------|----------|
| **Android 11+ Package Visibility** | `<queries>` manifest declarations with `intent.action.MAIN` + graceful fallback for restricted packages |
| **Full-Screen Rendering Paradox** | `rootInActiveWindow` verification before overlay dispatch — eliminates zombie overlays caused by background content changes |
| **Quick-Switch Bypass Attempts** | `TYPE_WINDOWS_CHANGED` event monitoring catches rapid app transitions via Recent Apps that `TYPE_WINDOW_STATE_CHANGED` misses |
| **Duplicate Event Suppression** | `lastDetectedPackage` state tracking prevents redundant overlay triggers |
| **No Polling Debouncers** | Pure state machine logic — no `Handler` delays or timers that drain battery |

#### React Native Bridge
- `FocusGuardModule.kt` — Native module exposing `getInstalledApps()`, `addBlockedApp()`, `removeBlockedApp()`, `getBlockedApps()`
- `FocusGuardNativeModule.js` — JavaScript interface with Promise-based async operations
- Bidirectional communication: UI controls → Native storage → Service queries

### Upcoming Phases

| Phase | Focus | Status |
|-------|-------|--------|
| Phase 3 | Core Blocker Logic & Gamification | **✓ Complete** |
| Phase 4 | Ironclad Security & Anti-Tampering | **Next** |
| Phase 5 | Edge Cases, Boot Receiver, Battery Optimization Onboarding | Pending |

## Blocking Levels

| Level | Name | Behavior |
|-------|------|----------|
| 1 | Awareness / Nudge | Transparent popup with "Continue" or "Close" options |
| 2 | Friction / Gamification | Cognitive challenge (math/history) required for bypass |
| 3 | Discipline / Hard Block | Full-screen overlay with no bypass option |

## Technical Highlights

### TYPE_WINDOWS_CHANGED for Quick-Switch Detection

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    when (event?.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChange(event)
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> handleWindowsChanged()
    }
}
```

Monitoring `TYPE_WINDOWS_CHANGED` catches rapid app switches (e.g., double-tap Recent Apps) that bypass `TYPE_WINDOW_STATE_CHANGED` events, closing a common user bypass vector without requiring any polling.

### rootInActiveWindow Verification

```kotlin
// Before dispatching overlay, verify the window is actually active
val rootNode = rootInActiveWindow
if (rootNode == null) {
    // Window not ready — skip overlay to prevent zombie renders
    return
}
val packageName = rootNode.packageName?.toString()
```

This verification prevents overlay dispatch when Android reports package changes but the window isn't fully rendered. It solves the "zombie overlay" problem where overlays would appear over the wrong application during background animations.

### Why Not TYPE_WINDOW_CONTENT_CHANGED?

Listening to `TYPE_WINDOW_CONTENT_CHANGED` is explicitly forbidden in this architecture. This event fires during background animations and content updates, causing overlays to appear on already-dismissed applications — the root cause of zombie overlays.

## Getting Started

### Prerequisites

- Node.js 18+ and npm
- Android Studio with SDK 33+
- Java 17 (for Gradle builds)
- Physical Android device or emulator with API 28+

### Installation

```bash
# Clone the repository
git clone https://github.com/aldobuarlele/focusguard.git
cd focusguard

# Install dependencies
npm install

# Build and run on Android
npx expo run:android
```

### Required Permissions

After installation, grant the following permissions manually:

1. **Accessibility Service** — Settings → Accessibility → FocusGuard
2. **Display Over Other Apps** — Settings → Apps → FocusGuard → Display over other apps
3. **Device Admin** (Phase 4) — Required for uninstall protection

## Project Structure

```
focusguard/
├── android/
│   └── app/src/main/java/com/anonymous/focusguard/
│       ├── AccessibilityDetectionService.kt  # Event-driven foreground monitor
│       ├── DatabaseHelper.kt                  # Native SQLite engine
│       ├── FocusGuardModule.kt               # React Native bridge
│       ├── FocusGuardPackage.kt              # Module registration
│       ├── OverlayService.kt                 # Overlay lifecycle manager
│       ├── OverlayView.kt                    # Blocking UI renderer
│       ├── MainActivity.kt                   # Entry point
│       └── MainApplication.kt                # Application class
├── src/
│   └── native/
│       ├── FocusGuardNativeModule.js         # JS bridge interface
│       └── index.js                          # Module exports
├── App.js                                    # React Native dashboard
├── FOCUSGUARD_CORE.md                        # Technical specification (v3.1)
└── README.md                                 # This document
```

## Development Guidelines

- All OS-level logic must be implemented in native Kotlin
- React Native layer handles UI rendering and state only
- Use relative paths from project root (never absolute paths)
- Follow event-driven architecture — avoid polling mechanisms and Handler debouncers
- Services must implement self-cleanup on UI restart
- Database queries from services must use native SQLite, not JS bridge
- Never listen to `TYPE_WINDOW_CONTENT_CHANGED` — it causes zombie overlays

## License

This project is proprietary and under active development.

---

*Engineered with React Native and Native Android for maximum OS integration and zero-latency enforcement.*
