# FocusGuard

A productivity and digital wellbeing Android application designed to limit social media usage through an intelligent leveling blocker system and gamification mechanics. All processing is handled locally on-device for maximum privacy.

## Overview

FocusGuard enforces app-blocking at the OS level using a hybrid architecture that combines the rapid development capabilities of React Native with the deep system access provided by native Android APIs. The application intercepts access to target applications and presents escalating barriers based on user-configured restriction levels.

## Architecture & Tech Stack

### Hybrid Architecture with Heavy Native Modules

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI / Frontend** | React Native (Expo Bare Workflow) | Rendering, state management, user interactions |
| **Native Core** | Kotlin / Java | OS-level logic, service management, system API access |
| **Data Persistence** | SQLite (Local) | On-device storage for rules, usage logs, and challenges |

### Core OS APIs

The application relies on the following Android system APIs:

- **AccessibilityService** — Real-time detection of foreground applications
- **SYSTEM_ALERT_WINDOW** — Drawing overlay UI above target applications  
- **DevicePolicyManager** — Preventing unauthorized uninstallation via Device Admin
- **WorkManager** — Scheduled background tasks for weekly reports and log aggregation

## Current Status

> **Active Development** — Phase 1 Complete

### Phase 1: Native OS Foundation & Core Blocking Services ✓

The foundational native infrastructure is fully implemented:

- `AccessibilityDetectionService` — Monitors foreground app changes via `TYPE_WINDOW_STATE_CHANGED` events
- `OverlayService` — Renders blocking overlays with correct `WindowManager` flags (`FLAG_NOT_FOCUSABLE`, `isClickable=true`)
- Direct Intent communication between services (bypasses React Native bridge for performance)
- Lifecycle state reset mechanism to prevent zombie service instances

### Upcoming Phases

- **Phase 2:** Data persistence layer (SQLite schema) and React Native dashboard UI
- **Phase 3:** Leveling logic implementation (Nudge → Gamification → Hard Block)
- **Phase 4:** Anti-tampering security and scheduled background jobs
- **Phase 5:** Edge case handling, boot receiver, and battery optimization onboarding

## Blocking Levels

| Level | Name | Behavior |
|-------|------|----------|
| 1 | Awareness / Nudge | Transparent popup with "Continue" or "Close" options |
| 2 | Friction / Gamification | Cognitive challenge (math/history) required for bypass |
| 3 | Discipline / Hard Block | Full-screen overlay with no bypass option |

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

After installation, the following permissions must be granted manually:

1. **Accessibility Service** — Settings → Accessibility → FocusGuard
2. **Display Over Other Apps** — Settings → Apps → FocusGuard → Display over other apps
3. **Device Admin** (Phase 4) — Required for uninstall protection

## Project Structure

```
focusguard/
├── android/                    # Native Android code (Kotlin/Java)
│   ├── app/src/main/java/     # Services and native modules
│   └── app/src/main/res/      # Android resources
├── src/
│   └── native/                # React Native bridge modules
├── assets/                    # App icons and images
├── App.js                     # Root React Native component
└── FOCUSGUARD_CORE.md        # Technical specification document
```

## Development Guidelines

- All OS-level logic must be implemented in native Kotlin/Java
- React Native layer handles UI rendering and state only
- Use relative paths from project root (never absolute paths)
- Follow event-driven architecture — avoid polling mechanisms
- Services must implement self-cleanup on UI restart

## License

This project is proprietary and under active development.

---

*Built with React Native and Native Android for maximum OS integration.*
