# InputForge TV

**InputForge TV** is a TV-first Android input engine: it turns ordinary remote/gamepad navigation into a configurable virtual pointer and global TV actions.

## Why it exists

Smart-TV interfaces often assume every app supports the same focus model. Many do not. InputForge adds an input layer above the UI so a D-pad can behave like a pointer instead of being trapped by focus navigation.

## v0.1 capabilities

- Accessibility-based input engine for Android TV.
- Virtual pointer controlled by D-pad.
- A / D-pad Center = click at pointer position.
- B = Back.
- X = Home.
- Y = Recent Apps.
- Adjustable pointer speed.
- TV-first launcher UI.
- No network bridge, cloud dependency, root or companion phone required.

## Architecture

```text
InputForge TV
├── MainActivity                  # TV control panel
├── InputForgeAccessibilityService # global input interception
│   ├── Cursor engine             # virtual pointer position
│   ├── Gesture engine            # tap / movement injection
│   └── Action mapper             # Back / Home / Recents
└── Android Accessibility API     # privileged user-approved input layer
```

## Important platform boundary

InputForge intentionally uses public Android APIs. Accessibility can receive filtered key events and dispatch gestures, but it cannot magically inject arbitrary hardware-level gamepad events into every application. Analog-stick capture, raw HID rewriting and some game-specific mappings require a different system-level mechanism and are therefore separate future work.

## Build

```bash
gradle :mobile:assembleDebug --no-daemon
```

The GitHub Actions workflow builds the debug APK on every push to `main`.

## First launch

1. Install `InputForge TV` on the Android TV.
2. Open it.
3. Choose **Ativar InputForge**.
4. Enable the InputForge accessibility service.
5. Return to the app.
6. Leave **Modo ponteiro** enabled.

## Project direction

Future modules are planned around profiles, macro composition, analog-to-pointer strategies, per-app rules, diagnostics and a polished TV-native UI. The core rule is simple: every feature must be backed by a real Android capability rather than a fake tester/demo.
