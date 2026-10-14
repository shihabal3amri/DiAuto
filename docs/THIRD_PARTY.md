# Third-party notices

DiAuto preserves the Open Headunit source and Michael Reid copyright notices under
AGPL-3.0. See the root LICENSE and COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt.

- Open Headunit: https://github.com/andreknieriem/open-headunit
- Original headunit work: https://github.com/mikereidis/headunit
- FFmpeg 7.1.1: the bundled ARM64 software HEVC libraries report LGPL-2.1-or-later.
  Headers, notices and configuration guidance are in `app/src/main/cpp/ffmpeg/`.
  Upstream source: https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz
- libusb: dynamically linked USB support, LGPL-2.1-or-later.
  Source and license: https://github.com/libusb/libusb
- Material Symbols (Apache-2.0): the dashboard turn card's maneuver glyphs
  (`app/src/main/res/drawable/ic_turn_card_*.xml`), as used by DiPlay.
  Source: https://github.com/google/material-design-icons
- Kotlin, AndroidX, Material Components, protobuf and other dependencies are declared
  in the Gradle build files. Their original notices and licenses apply.
- Android Auto name and mark belong to Google. BYD belongs to its respective owner.
  Neither company endorses this independent project.

## BYD HUD maneuver icons

Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate)

The maneuver PNGs under `app/src/main/assets/byd-hud-icons` were imported from BYDMate. Its PolyForm Noncommercial 1.0.0 terms and required notice are included alongside the assets. These files are separate from the project code license; upstream describes them as donor assets and their original provenance is not independently established. The validated DiLink5.1 windshield path uses factory turn codes rather than these images.
