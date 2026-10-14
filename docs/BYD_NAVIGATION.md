# BYD navigation displays

Phone navigation arrows, next-turn distance and street names can appear on supported BYD displays. Ordinary operation requires no ADB, root, laptop or helper process. The map app must provide structured navigation metadata; compatibility is not guaranteed for every map app or version.

## Validated windshield path

Live guidance and street names were physically confirmed in both DiAuto and DiPlay on DiLink5.1 / Android13, firmware `BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys`. The standalone output is restricted to that firmware and the verified stock receiver version10601004/signing certificate. Other firmware is not implicitly enabled by this result. Existing cluster/SOME-IP outputs remain available on supported factory services; the DiPlay contributor independently reported DiLink5.0 cluster/HUD operation.

The app sends navigation-only broadcasts to the stock ClusterDebug receiver as its normal Android UID. Vendor output runs outside phone control callbacks. Street text uses the installed HAL's UTF-16LE chunk protocol, capped at48 UTF-16 units without splitting a surrogate pair. Output logs exclude street text.

While guidance is shown the navigation state is written again at most every 3.5 s, as the stock map does with its own guidance. The stock map sets that state to "not navigating" when the cluster projection display appears after start-up, when the driver changes the cluster's navi mode and when the map app starts; without the refresh the windshield kept the arrows hidden until the route was stopped and started again.

Normal route end, disconnect, disabling navigation output and stale guidance trigger cleanup. Force-stop/process kill may leave the last instruction visible until the app opens again; a recovery journal handles that next launch. There is no guaranteed process-independent expiry. Run only one projection app at a time.

Enable BYD navigation in settings. In DiAuto it is opt-in under Navigation; in DiPlay it is enabled by default when available. Debug-only receivers/demos require Android's DUMP permission and are absent from release manifests. Development starter and vendor-access experiments are not part of the production navigation path.

## Android Auto map on the instrument cluster (experimental)

DiAuto can ask the phone for Android Auto's instrument-cluster map and show it in the BYD cluster's map window, as DiPlay does with CarPlay's cluster screen. The phone's navigation app draws this map itself; Google Maps and Waze do so for cars that declare a cluster display. DiAuto declares a second video display of type cluster (`MediaSinkService` `display_id` 1, `display_type` 1, the fields aa-proxy-rs uses to add cluster displays) on channel 14, decodes its H.264 stream with a separate decoder, and shows it as a `Presentation` on the cluster projection display. No ADB, root or helper is needed.

- Turn on **Android Auto map on the dashboard** under Navigation. The switch appears only when a BYD cluster projection display (`fission_bg_XDJAScreenProjection`, owned by `com.byd.containerservice`) is visible to the app. It takes effect from the next connection.
- In the cluster's own steering-wheel menu, choose "Full screen navi" or "Small screen navi"; the cluster shows the projection area only then.
- On the measured DiLink 5.1 firmware (Han L, and the Leopard 5 firmware DiPlay added) pick the cluster theme you use in the car under **Cluster theme in the car**: Map uses the full layer `shared_…_0` with a 1920×480 window at y=144; Scenario and Simple use the side layer `shared_…_1` with a 600×720 window at x=1320. These are DiPlay's measured windows. As DiPlay does with CarPlay, DiAuto asks for one plain stream and crops it at native scale into the window, around the centre. Other firmware gets the whole display.
- Android Auto, unlike CarPlay's `altScreenURLs`, does not say which cluster displays a phone accepts; a phone that refuses one closes the session right after service discovery. A first build that asked for a map shaped like the side window (1280×720 with a 680 px width margin) was refused by a OnePlus 15 at protocol 1.2 and 1.7 alike. DiAuto therefore tries descriptions phones are known to accept, one per connection, until one is accepted: aa-proxy-rs's cluster defaults (1280×720, no margins, viewing distance 300, real density 160), the same with its optional D-pad input source (channel 15), and the desktop head unit's 800×480 example. After the last one it stops offering the cluster until DiAuto restarts, so a refusal costs one connection attempt each.
- **Turn card on the dashboard map** (on by default) draws DiPlay's next-turn card over the map: the Material Symbols maneuver glyphs, distance, street or roundabout exit, and an arrival strip when the phone reports the route's remaining time and distance. It uses the same mapped guidance as the HUD output and does not need the HUD switch.

The cluster display is offered only on connections that run Android Auto protocol 1.6 or newer (DiAuto asks for 1.7 and falls back to 1.2); web-auto, whose cluster video works, declares its displays at 1.6. The diagnostic report's `Dashboard map:` line names the description the phone accepted and the ones it refused.

Limits: like DiPlay's, the cluster window belongs to the projection screen, so it closes while DiAuto is in the background. Whether a phone streams the map depends on its Android Auto and navigation app versions; without a stream the window shows a spinner. The cluster stream has not yet been confirmed in a car. The diagnostic report's `Dashboard map:` line shows the detected display, the stream that was offered and whether a picture arrived; log lines start with `ClusterMap:`.
