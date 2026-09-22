# Automatic Wi-Fi Direct BSSID recovery

Included in v0.3.2. Initially tested on 22 September 2026; the release also keeps
credential refresh from replacing the group while Bluetooth waits for its address.

## Problem

On the tested BYD DiLink 5.1 / Android 13, clearing Static BSSID reproduces a
wireless startup failure. Bluetooth reaches the phone and the 5 GHz group is
created, but ordinary app MAC lookups return null or masked addresses. Location
and nearby-device permissions are already enabled. The native handshake aborts
before sending Wi-Fi credentials.

Previously this unit required manually provisioning its group address using ADB.
That cannot serve users whose cars do not expose ADB.

## Change

The active group interface on this firmware has a modified EUI-64 IPv6 link-local
address derived from its current MAC. `P2pInterfaceBssid` reverses that encoding,
using `NetworkInterface.inetAddresses` within DiAuto's own process. The encoding
is described in [RFC 4291, Appendix A](https://www.rfc-editor.org/rfc/rfc4291#appendix-A).

- Runs when automatic MAC lookup is masked and the head unit owns the group.
- Reads only the named group interface; it does not select a station interface,
  nearby phone, router address, or a hard-coded car address.
- Requires `fe80::/64` and the modified EUI-64 `ff:fe` marker; rejects opaque
  identifiers, multicast/zero/masked results, and conflicting candidates.
- Retains explicit Static BSSID overrides and the existing MAC lookup paths.
- Allows IPv6 to arrive within the existing 15-second address-wait budget, with
  the existing generation check preventing delivery after group replacement.
- Does not save the recovered address in settings. Subsequent groups are read
  again, so the mechanism does not depend on a MAC remaining stable.
- Adds no permissions, shell commands, root dependency, phone app or ADB grants.

## Verification

- 581 JVM unit tests pass, including five new tests covering local/universal MAC
  decoding, malformed/opaque/non-link-local input, placeholders, ambiguous
  candidates, and absent interfaces.
- Debug and optimized release builds pass, including release vital lint.
- Live car: Static BSSID cleared to Auto, process restarted, automatic resolution
  logged from `p2p0`, Wi-Fi session established, SSL completed and video rendered.
- Optimized non-debuggable build installed with the existing private-preview
  certificate to preserve the owner's settings. Projection started with the ADB
  client disconnected. `run-as` was denied, confirming this was not a debug build.
- After the head-unit restart command, the Android framework/processes restarted
  and a newly named P2P group was created. Opening DiAuto again recovered its
  address, completed the handshake and rendered video. Kernel uptime did not
  reset, so this is a framework/head-unit restart test, not a cold power-cycle test.

The original on-car test used a non-debuggable development artifact with the
private-preview certificate. The public v0.3.2 release uses the production
certificate and versionCode 102. The existing v0.3.1 release does not contain this fix.

The isolated v0.3.2 release was retested on 23 September: automatic interface
recovery, a 5200 MHz group, successful TLS and first rendered frame at 01:01:41
with the saved 1080p preference. This on-car release test again used the existing
private-preview signing key; the public artifact certificate was separately verified.

## Limits

This is a targeted recovery method for interfaces using MAC-derived IPv6
identifiers. Firmware using opaque IPv6 identifiers or no IPv6 may still need
another solution. Tested with the owner's OnePlus 15, not the issue reporter's
Samsung S21 Ultra. Do not claim universal wireless compatibility from this test.
ADB was used to install and inspect the test build; the resolver itself uses only
ordinary app APIs. Disconnecting the ADB client is not the same test as disabling
the ADB service in system settings.
