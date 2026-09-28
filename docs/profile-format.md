# Profile format

A profile describes one game: where its HUD elements are in the game image (**regions**)
and one or more ways to arrange them on the second screen (**layouts**).

All rectangles are `[x, y, w, h]`, normalized 0–1 with the origin at the top-left.

- Region `src` rects are relative to the **game viewport**, the part of the top screen
  that actually shows the game after the emulator letterboxes it.
- Placement `dst` rects are relative to the **second screen**.

That keeps profiles independent of the screen resolution and the emulator's
scaling.

```jsonc
{
  "id": "jak-x-combat-racing",        // also the file name: <id>.json
  "name": "Jak X: Combat Racing",
  "platform": "PS2",
  "notes": "Shown in the app under the profile picker.",
  "calibrated": false,                 // set to true automatically when you save from the editor
  "viewport": {
    "aspect": "16:9",                  // "16:9", "4:3", "1.5", or "fill" (stretched to the whole screen)
    "rect": null                       // optional explicit [x,y,w,h] of the top screen, overrides aspect
  },
  "background": "#05070A",             // "#RRGGBB" or "#AARRGGBB"
  "regions": [
    { "id": "minimap", "label": "Track map", "src": [0.78, 0.04, 0.19, 0.34] }
  ],
  "layouts": [
    {
      "name": "Big map",
      "placements": [
        {
          "region": "minimap",         // a region id, or "@full" for the whole game image
          "dst": [0.02, 0.02, 0.96, 0.96],
          "fit": "contain",            // contain (letterbox) | cover (trim to fill) | stretch
          "shape": "rect",             // rect | ellipse (for round radars)
          "border": "#29D17D",         // optional
          "borderWidth": 4,            // bottom-screen pixels
          "opacity": 1.0
        }
      ]
    }
  ]
}
```

Tips:

- Always include a `"@full"` layout. It's a quick way to check that capture works at all.
- Keep regions a little bigger than the HUD element. Some HUDs animate or bounce.
- The app skips invalid profiles and lists the reason under the profile picker.
- `./gradlew testDebugUnitTest` validates every bundled profile.
