# DiAuto 0.3 review

## Product changes

- New dark standby screen with large touch controls, connection status and Bluetooth
  pairing guidance. USB fallback and car-home navigation remain one tap away.
- New settings landing page for automation, display/performance, permissions and credits.
- Basic settings narrowed to everyday controls. Calibration, transport and diagnostics
  remain available under Advanced; search is retained.
- Removed self-mode, VPN permission requests, Nearby/helper discovery and manual IP
  selection code from the home screen. Legacy transport implementations remain internally
  available for existing integrations and troubleshooting.
- Removed custom loading media, donation/support, simulated speed, HUD mirroring,
  synthetic memory profiles and video fault controls from the main settings list.
- Setup reduced from 11 screens to welcome, safety, permissions and ready. Deferring
  setup no longer incorrectly marks it complete. Custom loading-media setup is omitted.
- Android Auto arrow launcher/adaptive icons, DiAuto names in every launcher locale,
  shorter startup delay, and accurate fork attribution.

## Reliability and performance

- Preserved the existing 1920×1080 offset fix.
- Fullscreen ownership is now per projection activity. Destruction unregisters the
  layout listener and removes only that activity's strip panel. No synchronous file trace.
- Strip touches use a copied MotionEvent and current screen coordinates, including
  after resize; the framework's original event is never mutated.
- Fixed MainActivity registering its recreation receiver each time loading-video
  cleanup ran. Registration is now paired once with activity teardown.
- Normal logging avoids stack trace collection for every audio/video message.
- Direct surface output is the fresh-install default only for the tested `DiLink5.1`
  model. Other models and explicit existing choices are preserved. Invalid imported
  renderer IDs fall back safely instead of triggering a null assertion.
- Video fault injection cannot activate in a release build, including from stale prefs.
- Added a release-compatible installer that selects the exact ADB serial, checks
  process death and leaves pairing/calibration intact.
- Removed the redundant debug workflow and unpinned third-party AI review workflow.
  CI now uses the pinned NDK and builds both debug and optimized release plus unit tests.

## Evidence

- Debug and optimized release assembly passed; release includes R8/resource shrinking.
- Final local APK: `DiAuto-v0.3-review.apk`, about 10 MB, non-debuggable, signed with
  the existing development key for upgrade compatibility. Signature verification passed.
  SHA-256: `3f436119c9a17236ca2f14ddcd261832603541a01e169017c383d536437a9512`.
  Installed successfully on both the car and emulator.
- 569 unit tests passed: 0 failures, 0 errors, 0 skipped.
- Live car: final optimized APK also connected and displayed video (sampled decoder
  throughput 41–47 FPS, zero dropped/skipped frames); native wireless connected; 1920×1080, scaleX/scaleY 1.0; hardware AVC decoder.
- Both TextureView and SurfaceView displayed the complete picture. Bottom-edge drawer
  tap at (50,1030) opened the Android Auto drawer with SurfaceView.
- Direct-surface samples: decoder 48–50 FPS with zero dropped/skipped frames in sampled
  windows. SurfaceFlinger scroll sample: 84 timestamps over about 2 seconds, 41.9 FPS,
  median interval 17.2 ms. These are different measurements, not interchangeable FPS.
- No controlled A/B uplift is claimed. Phone animation/idle rate and Wi-Fi contention
  affect the samples; a long drive and repeatable animation benchmark remain necessary.
- Emulator: fresh install opened setup, then the optimized APK completed runtime
  permission prompts and rendered the portrait home screen without crashing.
- Optimized APK settings landing page and display settings opened on the car; back
  navigation returned to home. No app crash was recorded in the checked crash buffer.
- Full legacy debug lint did not finish in a bounded run and was stopped after both
  APK builds completed. Release vital lint passed. A full lint-clean claim is not made.

## Before public release

- Owner review of the installed preview and interaction design.
- Cold boot, away/return reconnect, microphone/audio, physical USB and sustained driving.
- Decide on the permanent signing key and application ID before publishing.
- Review launcher branding for public distribution; this preview uses the requested mark.
- Finish the inherited full-lint audit. Existing Kotlin/Android deprecation warnings remain.
- Wi-Fi Direct MAC reboot stability remains unverified; Static BSSID is still needed
  on this firmware. The new installer documents manual entry for non-debuggable APKs.

No repository, tag or public release was created. Screenshots in `docs/review/` are
local review artifacts and intentionally ignored by Git because projection includes
private map and phone content.

## 0.3.1 follow-up: audio, settings, frame delivery

- Added **Music through car Bluetooth** (Basic > Audio). Defaults on for DiLink5.1,
  off on other models, with an explicit saved override. Reconnect to apply.
  The media sink is omitted from discovery while speech/system sinks remain. Dynamic,
  protocol and permanent audio-focus decisions all use NEVER on this route, regardless
  of proprietary Bluetooth profile detection. The existing focus preference is preserved
  for switching back. Backup/import/reset include the new preference.
- Audio evidence: the old process repeatedly requested USAGE_MEDIA audio focus while
  BYD Bluetooth was present. No focus acquisition from the new process appeared in the
  sampled session. User reported no audible cutouts during the initial listening test.
  Calls, assistant mixing and long drives still require verification.
- Settings option rows now use individual bordered cards, larger typography and Material
  switches. Automation, display, permissions, about, DPI, mic, keymap and appearance
  inherit the consistent settings palette. Quick Settings, pickers, search, segmented
  options, permission rows and toolbar actions are also restyled.
- SurfaceView output uses bounded timestamp scheduling rather than immediately releasing
  pairs of decoded frames. Queued presentation is capped at two display intervals; stale
  decoded frames may be skipped without dropping encoded reference data. Texture/GLES
  and pre-21 devices retain immediate release. Four pure pacing tests cover burst spacing,
  queue bounds/recovery, a slower source, and invalid refresh rates.
- Live DiLink reports only 1920x1080 at 57.999996 Hz. A physical steady 60 FPS is unavailable
  through this display mode; the motion target is approximately 58 distinct frames/sec.
- Limited same-device comparison: three scripted 7-second map drags per build, sampling
  the last ~127 SurfaceFlinger presentation timestamps during each gesture. Pacing off:
  30.2 / 34.0 / 35.0 FPS; pacing on: 34.5 / 39.0 / 34.5 FPS. P95 frame gaps remained
  approximately 138–155 ms. This is a small sequential experiment, not proof of a stable
  gain or resolution of stutter; map content, phone/network load and warm-up can vary.
  Earlier 42 FPS samples used a different session and are not directly comparable.
- Decoder input wait was low in a sampled drag session (17–21 ms accumulated over five
  seconds), while fed throughput was only 22–26 FPS in those mixed windows. This does
  not establish a unique bottleneck. The wired comparison below narrows the large stalls to wireless operation.
- Validation: 573 unit tests passed; optimized GitHub-flavor release and release vital
  lint passed. Portrait and landscape option layouts and save interaction checked on an
  emulator. Reconnection and projection checked on the car. Review APK remains signed
  with the development certificate, not a publication signing identity.

### Wired comparison (same review build)

Confirmed Android Open Accessory USB transport after granting access before and after
phone re-enumeration. Three scripted drags: **50.7 / 52.9 / 51.1 displayed FPS**;
p95 intervals **34.49 / 34.48 / 34.49 ms**, versus 138–155 ms on wireless. Each USB
sample had one interval above 35 ms (maximum ~52 ms). This strongly implicates the
wireless path in the large stalls, rather than the display alone. The car's simultaneous
Wi-Fi station connection reported 5200 MHz with RSSI -77 dBm; this is the station/AP
link, not a measurement of the phone's projection link. Channel contention is a hypothesis,
not yet isolated. Do not attribute the USB improvement to the new pacing code alone.

User confirmed the wired session feels noticeably smoother.


### Wireless channel alignment, verified on car

The active P2P group was 5745 MHz while the car's associated Wi-Fi station was
5200 MHz. `NativeGroupFrequencyPolicy` now prefers the station's non-DFS 5 GHz
primary channel on the initial native group attempt (Android 10+). Forced 2.4 GHz,
unassociated/unsupported channels, creation retries and join recovery retain the
existing band selection. The public Builder frequency API is used instead of the
mutually exclusive band setter; driver rejection falls through existing retries.
No network is disconnected and no router/car system settings are changed.

The car accepted 5200 MHz; dumpsys wifip2p confirmed an active group with one client.
Fresh pre-change wireless samples: 35.3/34.2 FPS, p95 137.95/155.19 ms; third sample
aborted on an ADB connection drop. Post-change samples: 54.9/46.8/57.5 FPS,
p95 34.48/34.53/17.26 ms, max 34.50/51.75/34.49 ms. With music playing:
51.7/40.3/56.2 FPS, p95 34.49/34.51/17.27 ms, max 34.49/103.46/34.52 ms.
This supports channel contention as a major contributor in this driveway setup;
it is not a guarantee of constant frame rate or a controlled general benchmark.
Map content, phone output and other radio traffic still vary. One 103 ms stall
remained. The panel remains ~58 Hz. Await owner subjective wireless feedback.

Final tests: 576 passed, zero failures/errors/skips; optimized release assembly and
release vital lint passed; git diff --check clean. Final installed car APK and local
`DiAuto-v0.3.1-review.apk` include channel alignment, audio routing, pacing and UI.
SHA-256: `1470fe63a3448a3919cbdebb46c627c3538e69bbf80d1b82a85cfcad183ec588`.
Development signed, signature verified. No GitHub publication.


## Public release 0.3.1

Owner accepted the result for publication. Public versionCode 101 uses a dedicated
release signing key (not the private review's development key). Package ID retained.
Source/support links now target shihabal3amri/DiAuto; unused registration asset removed.
576 unit tests and optimized release/vital lint passed again. Five static download
pages passed desktop/mobile layout checks, including Arabic RTL.
Public APK SHA-256: 04f88b8692bb4c2f9413896646691f8a5839f3aaee21aa558cdd0bed9615c9e1.
Private preview-to-public upgrades need settings export and reinstall because signing differs.
