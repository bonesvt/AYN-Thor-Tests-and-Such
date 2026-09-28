# Thor Dual Screen

Dual-screen support for games that don't have it, on the **AYN Thor**. The first game
supported is **Jak X: Combat Racing** (PS2).

The app runs next to your emulator. It captures the top screen, lifts HUD pieces out of
the game image (track map, position/lap, weapon, health/turbo) and draws them enlarged on
the Thor's bottom screen. It works with any emulator (NetherSX2, AetherSX2, ARMSX2, ...)
because it doesn't patch the emulator or the game.

```
 ┌──────────────────────────┐
 │  top screen: the game     │──► screen capture (MediaProjection → GPU texture)
 └──────────────────────────┘                  │
 ┌──────────────────┐                          ▼  crops per game profile (JSON)
 │ bottom: HUD, big │◄──── GLES2 renderer on the second display
 └──────────────────┘
```

## Install and use

1. Grab `app-debug.apk` from the latest **Build APK** run in GitHub Actions (or build it
   yourself, see below) and sideload it onto the Thor.
2. Open **Thor Dual Screen** on the top screen, pick *Jak X: Combat Racing*, tap **Start**.
   - Allow *Display over other apps* the first time.
   - Allow screen capture. If Android asks, choose *Entire screen*.
3. Open your emulator and start Jak X. The HUD appears on the bottom screen.
4. On the bottom screen:
   - **double-tap**: next layout (*Race HUD*, *Map + status*, *Big map*, *Full game mirror*)
   - **long-press**: calibration editor (see below)

   The same actions are also in the notification.

Touching the bottom screen **never steals controller focus** from the game. The bottom
window is non-focusable, so Android keeps key and gamepad input on the top screen.

### Calibrating (do this once per game)

The built-in Jak X crop boxes are **starting estimates**. They haven't been checked
against the real game yet, so the app shows an "uncalibrated" banner. To fix them:

1. Get into a race and pause, so the HUD is on screen and not moving.
2. Long-press the bottom screen. It now shows the whole game image with coloured boxes.
3. **Editing: game crops**: drag each box over its HUD element, and drag the round corner
   handles to resize.
4. Tap the mode button to switch to **Editing: this screen**, then arrange where each
   piece goes on the bottom screen.
5. **Save**. The calibrated profile is written to
   `Android/data/com.bonesvt.thordual/files/profiles/jak-x-combat-racing.json`.

Please send the calibrated JSON back (open a PR or an issue) so the built-in profile can
become the real one.

## Adding more games

Every game is a JSON profile. Drop new ones into the profiles folder above, or add them to
`app/src/main/assets/profiles/` to ship them built in. See
[`docs/profile-format.md`](docs/profile-format.md) for the format, and
[`docs/jak-x.md`](docs/jak-x.md) for notes on the Jak X mod and where it could go next.

## Building

Requires JDK 17+ and the Android SDK (API 34).

```sh
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Code layout (`app/src/main/java/com/bonesvt/thordual/`):

| Path | What it does |
| --- | --- |
| `core/` | Pure Kotlin: profile model/JSON, viewport + crop geometry, scene building, editor drag math. Unit-tested on the JVM. |
| `render/` | EGL/GLES2. The capture lands in a `SurfaceTexture`, and each crop is drawn as a textured quad (optional border, ellipse mask). No CPU copies. |
| `screen/` | Overlay window on the second display, gestures, and the calibration editor. |
| `service/CaptureService.kt` | Foreground service that owns the MediaProjection and the virtual display, and follows top-screen rotation/resolution changes. |
| `MainActivity.kt` | Control panel on the top screen. |

## Limitations

- The HUD is **copied**, not moved: it still shows on the top screen too. Hiding it there
  needs a game patch; see the roadmap in [`docs/jak-x.md`](docs/jak-x.md).
- Screen capture sees what's on the top screen, so turn off the emulator's on-screen
  touch controls, or they can end up inside the crops.
- Android asks for capture permission each time you tap Start. That's a platform rule.
