# Jak X: Combat Racing dual-screen mod

## Status

| Piece | State |
| --- | --- |
| Capture and render pipeline | Done; builds and unit-tested, not yet tried on a Thor |
| Jak X profile: 4 regions, 4 layouts | Region positions are **estimates**, needs on-device calibration |
| On-device calibration editor | Done |

Regions in the profile:

| id | What it's meant to capture | Initial guess (game viewport) |
| --- | --- | --- |
| `race_status` | Race position and lap counter | top-left |
| `minimap` | Track map / racer positions | top-right |
| `weapon` | Current weapon and ammo | bottom-right |
| `vehicle` | Vehicle health and turbo | bottom-left |

If Jak X's HUD turns out to have elements that aren't listed here, or some of these don't
exist, edit `app/src/main/assets/profiles/jak-x-combat-racing.json`. Region ids are free-form.

## Recommended emulator setup

- Jak X options: **Widescreen 16:9** (matches the profile's `"aspect": "16:9"`).
- Emulator aspect ratio: Auto/16:9. If you play 4:3 with black bars at the sides, set
  `"aspect": "4:3"` in the profile, or everything will be offset.
- Turn off on-screen touch controls; they're captured along with the game.
- Upscaling is worth it: the bottom screen enlarges HUD pieces, so 2x–3x internal
  resolution makes them noticeably sharper.

## Roadmap: from "copied HUD" to a real dual-screen mod

The capture approach works with any emulator, but it can only copy pixels. Going further
requires work inside the game or emulator:

1. **Hide the HUD on the top screen.** Find the HUD draw toggles in EE memory with the
   PCSX2 debugger (Jak X is built on the same engine as Jak 3), then ship them as a
   `.pnach` patch. Most Android PS2 emulators load pnach cheats. Capture would then have
   nothing left to crop, so this step depends on step 2.
2. **Draw the HUD from game state.** Read race state from EE RAM (positions, laps,
   weapon, ammo, health, racer coordinates for a real map) and render a custom
   bottom-screen HUD. With an open-source emulator (ARMSX2 is a PCSX2 port) this can live
   inside the emulator, drawing on the second display with Android's `Presentation` API.
   `core/` and `render/` in this repo are designed to be reused for that.
3. **Extras**: a rear-view camera would need a second render pass inside the game. It
   might be possible with engine hooks, but it's a much bigger project.

Memory addresses and patch codes belong under `docs/research/` once someone finds them.
Please don't commit guesses.
