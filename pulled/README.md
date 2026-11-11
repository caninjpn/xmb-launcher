# Pulled from the device

Everything here came off the Fire TV (AFTCU864A2, Fire OS 8.1.8.5 / RS8185/3885) over
ADB, with no root. Reference material for debugging and for understanding how Amazon's
launcher works.

**The APKs are Amazon's proprietary software.** They are here because they were already
on a device you own, for inspection on that basis. Don't redistribute them.
`pulled/apk/` is gitignored for that reason, and because 41 MB of binaries doesn't
belong in a repo.

**Only this file is in the repository.** `pulled/config/` and `pulled/keylayout/` are
gitignored too: `getprop.txt` carries the device serial number and `dumpsys-input.txt`
carries the remote's Bluetooth MAC, neither of which belongs in a public repo. The file
paths quoted below therefore describe a local working copy, not anything you can open
from a clone. The findings themselves are reproduced in full in the text here.

---

## What the launcher's manifest settles

The question of whether Ember XMB could reproduce Amazon's Continue Watching row has a
definitive answer, and it's in `config/amazon-launcher-manifest.txt`. The launcher
holds permissions like:

```
amazon.media.recommendations.CONSUME_RECOMMENDATIONS
amazon.media.recommendations.MANAGE_RECOMMENDATIONS
com.amazon.privilege.consume.recommendations
com.amazon.avod.BUELLER_CONTENT_PROVIDER_ACCESS
com.amazon.CONTENT_PROVIDER_ACCESS
com.amazon.mas.client.CONTENT_PROVIDER_READ
```

These are privileged/signature permissions. A sideloaded app cannot hold them at any
price — same wall as `priority=950` on the HOME filter. Its content providers
(`com.amazon.avod.prime`, `com.amazon.tv.launcher.comrades`,
`com.amazon.tv.launcher.livetv`) are reachable only by callers holding those
permissions.

So the row Amazon shows is not something we failed to find. It is gated by the platform
signature, and `UsageStatsManager` recency is the best available substitute — which is
what Ember XMB uses.

---

## Contents

### `apk/` — 41 MB, gitignored

| File | Why |
|---|---|
| `amazon-tv-launcher.apk` | 28 MB. The home screen itself — rows, Continue Watching, recommendations. |
| `amazon-tv-settings-v2.apk` | 14.7 MB. Includes the `CTSDummySettingActivity` stub that fakes the Usage Access screen. |
| `amazon-firehomestarter.apk` | 26 KB. The priority-1 HOME handler sitting between Amazon's launcher and ours. |
| `amazon-firetv-notification-service.apk` | 301 KB. Package `com.amazon.media.recommendations` — the recommendations feed. |
| `amazon-providers-tv.apk` | 36 KB. Amazon's TV content provider. |

### `config/` — text, safe to commit

| File | Why |
|---|---|
| `packages-enabled.txt` / `packages-disabled.txt` | State after the debloat. The disabled list is the audit trail for `debloat/re-enable-all.sh`. |
| `packages-all-with-paths.txt` | Every package with its APK path, for pulling anything else. |
| `getprop.txt` | Full build properties — Fire OS version, device model, display config. |
| `dumpsys-input.txt` | Every input device with sources, keyboard type and vendor/product ids. This is where `amzkeyboard` and `kcmouse` show their `0x0000` vendor ids. |
| `home-handlers.txt` | The HOME priority ladder: Amazon 950, firehomestarter 1, us 0. |
| `dumpsys-amazon-launcher.txt` | Full package record for the launcher. |
| `amazon-launcher-manifest.txt` | Decoded manifest — the permissions quoted above. |

### `keylayout/` — 232 files

`system/` and `vendor/` key layouts. `Vendor_0171_Product_0424.kl` is your remote
(BLE "Willow"), and it maps `DPAD_UP` (103) and `DPAD_DOWN` (108) correctly — which is
how the remote was ruled out as the cause of the input bug. The real cause was Compose
focus; see the main README.

---

## Not pullable: a firmware image

Confirmed on the device, not assumed:

- `adb root` is refused — production build.
- `/dev/block/by-name/system` **does not exist**. This is an A/B device using a dynamic
  `super` partition, so the classic system image isn't a block device at all.
- Reading the underlying partitions needs root, which the locked bootloader prevents.

What *is* readable as files: `/system` (671 MB), `/system_ext` (0.9 GB), `/vendor`
(95 MB). If you want more of the firmware, name a path and it can be pulled.

For an actual signed image, Amazon publishes full firmware update files for Fire TV
devices on their own support pages. That's a better artifact than a dump from a running,
modified device.
