# External navigation app

Patch **External navigation app** (default off) makes the Navigation tab show an installed map app
(Google Maps, Waze, VietMap, ...) instead of Kim Map.

## Use

1. Finish Kim's ADB setup first (Settings > system permissions, "Grant all"). The patch launches the
   map app and sends taps through Kim's own local ADB connection.
2. Open **Kim navigation app** from the car's app list (Kim's own app drawer hides Kim's entries).
3. Pick the app, or **Kim Map (default)** to go back. Kim restarts to apply.

Kim's Settings tab is Dart (AOT) code, which Morphe cannot patch, so the choice lives in this
separate screen instead.

## How it works

The Navigation tab's map is a native view from the factory registered as `byd/native_kim_map`
(`KimLauncherActivity`, factory `K1/I9` in 1.0.2781, `K1/Z9` in 1.0.2785). The patch:

- tags that factory instance and, in its `create()`, returns `ExternalMapView` when an app is chosen
  (the Kim Map view is never built, so no Mapbox load);
- `ExternalMapView` is a `TextureView` drawing a virtual display (flags PUBLIC | OWN_CONTENT_ONLY |
  DESTROY_CONTENT_ON_REMOVAL). The app is started on it with `am force-stop` then
  `am start --display <id>`, the same way Kim's removed embedded navigation (`byd/navigation_vd`) did;
- touches go to the display with `InputManager.injectInputEvent` if allowed, otherwise as one
  `input -d <id> tap|swipe` per gesture when the finger lifts;
- `ExternalNav.shell` is bound at patch time to Kim's ADB client (`K1/Y8.m` / `K1/p9.m`), found
  through its caller in the old navigation launcher (string `NavigationVD shell launch ok=`).

The display lives for the whole Kim process, so the map keeps navigating when you leave the tab.

## Limits (not tested on a car yet)

- Without inject permission, touch is tap/swipe only: no pinch zoom, no live dragging, about half a
  second of delay. Use the app's own zoom buttons.
- Kim Map's Dart controls (buttons, search bar) are still drawn on top of the external app.
- The map app is force-stopped once each time Kim starts, which ends a navigation running elsewhere.
- Text input uses the main screen's keyboard; whether it reaches the app on the virtual display
  depends on the firmware.
