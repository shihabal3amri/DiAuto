# Install DiAuto 0.3.1

Install the APK **on the car's Android head unit**, not on your phone.
Tested: BYD DiLink 5.1, Android 13. Other firmware/head units are not verified.
Your phone must support Android Auto; no separate DiAuto phone app or dongle is needed.

## Download

Use the [multilingual download page](https://shihabal3amri.github.io/DiAuto/) or the
[GitHub release](https://github.com/shihabal3amri/DiAuto/releases/tag/v0.3.1).
Download `DiAuto-v0.3.1.apk`. Checksums are provided alongside the APK.

## Install and connect

1. Park the car. Use the APK installation method permitted by your head unit's firmware.
   If using a browser/file manager, allow that application to install APKs when prompted.
   If the car does not offer APK installation, use ADB only if the firmware permits it.
2. Open DiAuto and complete setup. Grant permissions requested for Bluetooth,
   Wi-Fi/location, microphone, notifications and overlay features as applicable.
3. Pair your phone in the **car's Bluetooth settings**. Enable Bluetooth and Wi-Fi on
   your phone, then choose **Connect phone** in DiAuto.
4. Accept Android Auto's first-connection prompts on the phone.
5. For wired operation, use a USB **data** cable and choose **Connect with USB**.

DiLink 5.1 defaults to **Music through car Bluetooth**. Keep the phone paired to the car
for media audio. Navigation and assistant audio remain available through Android Auto.
Changes to this option require reconnecting.

## ADB installation

Install Android platform-tools on your computer, enable ADB using your head unit's
supported method, and connect the computer and car to the same trusted network.
Replace `CAR_IP` below with the car's current IP address; no fixed address is assumed.

```sh
adb connect CAR_IP:5555
adb -s CAR_IP:5555 install -r DiAuto-v0.3.1.apk
```

Open DiAuto on the car to finish setup. The optional repository helper also grants
supported runtime permissions, enables location, allows the overlay and exempts DiAuto
from idle/background restrictions:

```sh
./scripts/install.sh CAR_IP:5555 /absolute/path/to/DiAuto-v0.3.1.apk
```

## Wireless pairing / Static BSSID

Some DiLink firmware hides the Wi-Fi Direct MAC address from apps. If wireless pairing
fails, start **Connect phone** so the wireless group exists, then run:

```sh
adb -s CAR_IP:5555 shell cat /sys/class/net/p2p0/address
```

Enter the returned address in **Settings → Display and performance → Advanced → Static
BSSID** (or search for **Static BSSID**), save, and reconnect. The value belongs to your
car: do not copy another car's address. If that interface is absent or unreadable, include
the firmware/model details in an issue instead of inventing an address.

## Updates and private previews

The first public release uses a dedicated production signing key. Future public releases
will use that same key so they can update in place.

Private `0.3.x-preview` APKs were development-signed. Upstream Open Headunit builds may
also share the application ID but use a different key. Android rejects an in-place update
when signatures differ. Export settings from the existing app first, then uninstall that
app and install the public APK. Uninstalling removes local settings and may require pairing
again. Do not uninstall the official BYD phone app; it is unrelated to DiAuto.

## Troubleshooting

- If scrolling stutters, compare a USB connection. Wireless conditions and concurrent
  car Wi-Fi connections can affect performance. DiAuto now prefers a supported matching
  5 GHz channel but cannot guarantee that firmware or the phone will accept it.
- If Bluetooth music cuts out, verify **Music through car Bluetooth** is enabled and
  reconnect. Test assistant prompts and calls too; report the route and circumstances.
- If the picture is wrong, try the other **View Mode** in Display and performance.
- For reports, include head-unit model/firmware, phone model/Android, connection type,
  DiAuto version and reproducible steps. Remove locations, device identifiers and
  account details from public logs/screenshots.
