# ControlBridge 3.0

ControlBridge is a local controller bridge designed around one goal: **physical gamepad → Android phone → TV**. The phone is the bridge, not a gamepad tester.

> 3.0 note: this branch is a deep architectural rewrite; validate the generated APKs on the target phone/TV before treating the bridge as production-ready.

## Architecture

```text
Bluetooth gamepad
      │
      ▼
 Android phone / ControlBridge
   ├─ Input capture
   ├─ Bluetooth HID ───────────────► Android TV / compatible HID host
   └─ WebSocket over LAN ─────────► ControlBridge TV receiver
                                      │
                                      └─ receiver UI / remote events
```

The Bluetooth HID path is the path intended for games and cloud-gaming apps that accept a normal Bluetooth gamepad. Android's public `BluetoothHidDevice` API supports registering an HID application, connecting to a paired host and sending HID reports. The registration must remain foreground, so the project now keeps the bridge in a `connectedDevice` foreground service.

The WebSocket path is a low-latency LAN control channel for the companion TV receiver. A normal Android app cannot inject arbitrary input into unrelated third-party TV applications just because it receives WebSocket packets; therefore the TV receiver intentionally exposes and visualizes the remote stream instead of pretending it can control every external app.

## Project structure

```text
core/
  protocol/              # InputPacket + WebSocket framing
mobile/
  bridge/                # Bluetooth HID output
  input/                 # Physical controller capture
  network/               # LAN WebSocket server + NSD advertisement
  service/               # Foreground bridge service
  audio/                 # UI sound manager + fallback tones
  ui/                    # Main dashboard / HUD / settings
  src/main/res/          # Theme, strings and optional downloaded SFX
tv/
  network/               # NSD discovery + WebSocket receiver
  ui/                    # TV receiver interface
scripts/
  fetch_ui_audio.sh      # Downloads the optional CC0 UI sound pack
.github/workflows/       # Android CI / APK artifact
```

## Run

1. Open the project in Android Studio with JDK 17.
2. Sync Gradle.
3. Build `:mobile:assembleDebug` for the phone APK.
4. Build `:tv:assembleDebug` for the Android TV receiver.
5. Install both apps on devices connected to the same Wi-Fi network.
6. Pair the phone with the TV through Bluetooth if you want the HID path.
7. Open ControlBridge on the phone and keep the bridge active. The service notification confirms the bridge is running.
8. Open ControlBridge TV. It advertises/discovers the phone automatically through Android NSD and opens the WebSocket channel.

## Themes and UI

The mobile UI is deliberately a dashboard rather than a permanent controller tester. `DashboardView` contains three screens: Home, Bridge HUD and Settings. Glass panels, subtle borders, animated ambient shapes and press/focus states are drawn with one lightweight custom view to minimize view hierarchy overhead.

For a future theme system, keep palette and dimensions centralized in `mobile/src/main/java/com/controlbridge/mobile/ui/`. Do not put networking or controller logic inside the drawing code.

## Audio

The runtime has a centralized `UiSoundManager` with four semantic events: focus, click, open and close. It falls back to Android `ToneGenerator`, so the APK remains functional without shipping external audio files.

Optional CC0 WAV assets can be installed automatically:

```bash
bash scripts/fetch_ui_audio.sh
```

The script uses the CC0 SFXMint UI set. If assets are present, the audio layer can be extended to prefer local files while retaining the tone fallback.

## Performance rules

- Controller events are emitted only when an input changes meaningfully.
- HID reports are compact and sent without JSON serialization.
- LAN JSON packets are small and only emitted for key/motion changes.
- UI animation uses a single custom Canvas instead of a large nested layout.
- Network work runs off the main thread.
- The HID/network bridge runs in a foreground `connectedDevice` service so the user can switch to another app without intentionally stopping the bridge.

## Important platform limitation

The network receiver and the Bluetooth HID path solve different problems. WebSocket packets can drive the **ControlBridge TV app itself**, but an ordinary Android application cannot universally inject those packets as physical gamepad events into arbitrary third-party games. For external games/cloud clients, use the Bluetooth HID connection so the TV sees the phone as a real HID gamepad.

## Status

This is a structural 3.0 rewrite. The next validation step is physical: install both APKs, pair the phone and Android TV, verify that the TV sees **ControlBridge Gamepad**, then test a real game/cloud client. Build success alone is not considered feature completion.
