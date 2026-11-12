# Ember XMB

A PS3 XrossMediaBar launcher for Android TV, in Kotlin and Compose. Fire TV is one
Android TV among several, and the most restrictive of them; where it differs, this
README says so.

The design was locked in an interactive prototype first; this is the native port of it.

**Artwork, trademarks and licensing: see [NOTICE](NOTICE).** The PS3 icons under
`app/src/main/res/drawable-nodpi/` are Sony's, not mine, and no licence to them is
granted here. The source itself is all rights reserved.

---

## Download

[**xmb-launcher.apk**](https://github.com/caninjpn/xmb-launcher/releases/latest/download/xmb-launcher.apk)
— 1.0 MB, Android 7.1+ (`minSdk 25`), GLES 2.0. That link always points at the
[latest release](https://github.com/caninjpn/xmb-launcher/releases/latest).

A signed release build: R8-minified, and `android:debuggable` is false, so a debugger
cannot attach to it. The signature is a self-generated key (`CN=Ember XMB`), which
proves successive builds came from the same keystore and nothing more — it is not an
identity any store has vetted.

**Coming from v1.0 needs an uninstall first.** That release was the debug APK, signed
with the shared `CN=Android Debug` key, and Android refuses to replace an app with one
signed differently:

```bash
adb uninstall com.ember.xmb
```

That clears the app's preferences too — favourites, manual category assignments, the
row order and the theme all return to defaults.

Then follow [Install](#install), substituting the downloaded file for the built one.

---

## What it does

- **The XMB cross.** A horizontal category row crossed by a vertical item column. The
  column runs *through* the row: items before the selection ride above it compressed,
  the selection sits in the gap below it behind a highlight pill, and the rest stride
  down at full spacing.
- **Categories**, left to right: Users, Continue Watching, Games, Video, Music, Photo,
  **Apps**, Settings. Apps lists everything and sits beside Settings; the typed
  categories above it are assignments, not a partition, so an app in Video is still in
  Apps.
- **Auto-assignment, with manual override.** `ApplicationInfo.category` first, then a
  package-name table for the big streaming apps that declare nothing. Long-press →
  **Move to category…** overrides it permanently.
- **Continue Watching** from real system-wide recency (see below), as 16:9 cards.
- **Long-press context menu** — Open, Move, Move to category, Add/Remove favourite,
  Info, Uninstall, Rearrange categories.
- **Rearrangeable category row.** Long-press → **Rearrange categories**, then Left/Right
  walks the current category along the row and Select drops it. The cursor travels with
  the category, so holding a direction keeps pushing the same one. This is the same
  gesture as **Move** on the other axis of the cross: items move up the column,
  categories move along the row. Long-press works on every category for this reason —
  it used to do nothing outside app columns, which left Continue Watching and Users
  unreachable.
- **Seasonal + time-of-day theming**, with twelve manual themes in Settings → Theme.
- **GPU background** — the twisting ribbon runs at full native resolution, split across
  a gradient pass and one vertex-shaped strip per band.
- **Key legend** that only appears when a keyboard or mouse is actually attached.

Continue Watching needs one `appops` grant to see system-wide recency rather than only
what you launched from here. It is step 4 of Install.

---

## Build

### Toolchain

Verified building against:

| | Version |
|---|---|
| Android Studio | `AI-262.9437.185` |
| JDK | **25** (Studio's bundled JBR — no separate JDK needed) |
| Gradle | 9.8.2 (via the wrapper) |
| AGP | 9.4.1 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 25 |

Two AGP 9 details that will bite if you change the plugin block:

- **Do not apply `org.jetbrains.kotlin.android`.** AGP 9 compiles Kotlin itself and
  rejects the plugin outright.
- **Do still apply `org.jetbrains.kotlin.plugin.compose`.** The Compose compiler is
  not built in, and `buildFeatures.compose = true` fails without it.

There is no `jvmToolchain()` call on purpose. Only JDK 25 is installed here, so asking
for a 17 toolchain would send Gradle looking for a JDK that does not exist; it compiles
on 25 targeting bytecode 17 instead.

### Command line

```bash
./gradlew assembleDebug
```

On Windows, point `JAVA_HOME` at Studio's JBR first if it is not already set:

```bash
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk` (~11.9 MB).

### Android Studio

`File → Open`, pick this folder, let it sync, then
`Build → Build Bundle(s)/APK(s) → Build APK(s)`.

`local.properties` already points at `C:\Users\wongc\AppData\Local\Android\Sdk`. It is
gitignored — on another machine, let Studio regenerate it.

### Release signing

`assembleRelease` signs with the key described by `keystore.properties` at the project
root. That file is gitignored and so is absent from a clone: copy
`keystore.properties.example`, then create the keystore with the `keytool` line in its
comments. Without the file the release build still succeeds — it just produces an
unsigned APK, since being unable to build at all is the worse failure.

The keystore is the app's identity, not a convenience. Lose it and no future build can
ever update an install signed with it; leak it and someone else can publish updates in
your name. Keep a backup outside the project folder.

---

## Install

Anything running Android 7.1 or later with a D-pad remote will take it: `minSdk` is 25,
and the background needs only GLES 2.0.

The steps below are the manual route, and worth reading once so you know what is being done
to your TV. If you would rather not type them, [`tools/`](#tools) does the same thing in one
command and can undo it in one more.

### 1. Unlock developer options

The build number is the switch on every Android TV. Only the menu holding it is
renamed per vendor:

| Platform | Path | Click 7 times on |
|---|---|---|
| Stock Android TV | **Settings → Device Preferences → About** | **Build** |
| Google TV | **Settings → System → About** | **Android TV OS build** |
| Fire TV | **Settings → My Fire TV → About** | the **device name** |

### 2. Turn on ADB

**Developer options** appears as its own entry once unlocked — under *Device
Preferences* on stock Android TV, under *My Fire TV* on Fire OS. Enable **USB
debugging** there. Boxes that accept ADB over the network expose that in the same
place, named variously *ADB debugging*, *Network debugging* or *Wireless debugging*.

### 3. Install

Over USB, nothing else is needed:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you downloaded the APK rather than building it, use its filename instead:

```bash
adb install -r ember-xmb-debug.apk
```

Over the network, connect first — the IP is under **Settings → Network**:

```bash
adb connect <tv-ip>:5555
```

Accept the authorisation prompt that appears on the TV, then run the same `install`
line. If `connect` times out, the box either has network ADB switched off or has gone
to sleep; both look identical from the computer.

### 4. Grant usage access, for Continue Watching

```bash
adb shell appops set com.ember.xmb GET_USAGE_STATS allow
```

Without it, Continue Watching falls back to apps opened from this launcher only. Some
boxes expose the same grant as **Settings → Apps → Special app access → Usage access**,
but many TV builds omit that screen altogether and Fire OS ships a stub in its place
(measured — see Known limits), so the ADB line is the one route that works everywhere.

### Making it the home screen

**This is the step that varies most between boxes**, so ask yours rather than assuming.
After installing:

```bash
adb shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN
```

Whatever that names is what the Home button will open. Three outcomes:

- **It names `com.ember.xmb/.MainActivity`** — done, nothing further to do.
- **Pressing Home offers a chooser** — pick Ember XMB. This is the normal case on AOSP
  boxes, where the bundled launcher is an ordinary package with no priority on its HOME
  filter.
- **It keeps naming the stock launcher and no chooser ever appears** — that launcher is
  a privileged system app holding a priority a sideloaded package cannot outbid. Fire TV
  is the measured example; see *Why the Home button cannot be claimed*. The practical
  setup then is to launch Ember XMB from the apps row and remap a spare remote button to
  it (Button Mapper, via Accessibility), treating that button as your real Home.

### Uninstall

```bash
adb uninstall com.ember.xmb
```

---

## Tools

`tools/` is the whole install reduced to a handful of scripts. They work in Git Bash on
Windows, and on macOS and Linux. Run `tools/connect.sh` once and the rest remember the TV.

```bash
tools/connect.sh                  # USB or Wi-Fi? asks, tests, remembers in tools/tv.conf
tools/backup.sh                   # snapshot the TV BEFORE changing anything
tools/build-install.sh            # build, install, grant usage access, take HOME, verify
tools/shot.sh home                # screenshot, optionally after some D-pad keys
tools/restore.sh                  # put everything back
```

| Script | What it does |
|---|---|
| `connect.sh` | Picks USB or Wi-Fi, proves the connection works, writes `tools/tv.conf`. Says so if the box is a Fire TV. |
| `backup.sh` | Package lists, HOME apps, `getprop` and all three settings namespaces into `state/backup-<date>-<time>/`. `restore.sh` reads settings from the **oldest** one, so take it first. |
| `build-install.sh` | `--debug`, `--build-only`, `--reinstall`. Explains install failures instead of printing a raw `pm` error. |
| `shot.sh` | `shot.sh <name> [KEYCODE ...]` → `state/shots/<name>.png`. |
| `disable.sh` | `disable.sh <batch> <pkg>...` or `--list <file>`. One `pm` call per package, each success logged to `state/disabled.txt`. |
| `reapply.sh` | After a firmware update: re-disable what came back, retake HOME. Safe to run repeatedly. |
| `restore.sh` | Re-enable everything, hand HOME back to the stock launcher, restore settings. `--fire-tv` also re-enables [`tools/fire-tv-packages.txt`](tools/fire-tv-packages.txt); `--uninstall-launcher` removes Ember XMB too. |

Nothing is ever uninstalled: `pm disable-user` hides a package from the current user, so
everything `disable.sh` touches can be put back exactly as it was. `tools/tv.conf` and `state/`
are gitignored — the snapshots contain the device serial and MAC addresses.

[`PROMPT.md`](PROMPT.md) is the same workflow written for an AI coding assistant to carry out,
if you would rather delegate than type.

### What these scripts will not do

They will not take the Home button on a Fire TV, because nothing can — see
[below](#why-the-home-button-cannot-be-claimed-on-fire-tv). `build-install.sh` checks what
actually has focus afterwards rather than trusting `set-home-activity`, which returns Success
on Fire TV and changes nothing.

They also run no helper daemon on the TV. A launcher that wants to kill other apps or free
memory needs a process with more privilege than an app has, and the usual trick — leaving a
listener open on localhost that runs shell commands as uid 2000 — means any app on the device
can drive it. Ember XMB has no such feature and opens no such door.

---

## How it is put together

```
app/src/main/java/com/ember/xmb/
  MainActivity.kt          GLSurfaceView under a ComposeView; remote key handling
  XmbViewModel.kt          all navigation state, menu, move, theme
  model/Models.kt          Category, LauncherItem, Mode
  data/
    AppRepository.kt       PackageManager queries; launch / info / uninstall intents
    ContinueWatchingRepository.kt
    Prefs.kt               favourites, item order, category row order, theme choice
    Images.kt              drawable rasterising + poster loading
  system/SystemState.kt    network transport, keyboard/mouse detection
  gl/
    Shaders.kt             the whole background, as GLSL
    WaveView.kt            GLSurfaceView + renderer, paced to 30fps
  ui/
    XmbPalette.kt          analogous palette from month + time of day
    XmbScreen.kt           the cross
    XmbParts.kt            status cluster, legend, context menu, theme picker, toast
    XmbIcons.kt            icons built from SVG path data
```

### Two things worth knowing before you change anything

**The window background must stay transparent.** A `SurfaceView`'s surface is
composited *below* the app window, not inside it. If you give the theme an opaque
`windowBackground`, or paint an opaque background anywhere in the Compose tree, the GL
layer vanishes and you get a flat colour. See `res/values/themes.xml`.

**The background belongs on the GPU.** The prototype drew the ribbon with ~800 filled
quads per frame on the CPU. That is fine in a desktop browser and wrong for a TV SoC.
`Shaders.kt` is the same picture as a few sine evaluations per pixel.

---

## Known limits

These are platform facts, not bugs to fix. Split by whether they hold for Android TV
generally or only for Fire OS, because the two get conflated constantly.

### Any Android TV

| Area | Reality |
|---|---|
| **Home button** | Claimable only where the stock launcher is an ordinary package. Android forces activity intent-filter priority to 0 for every **non-privileged** package, so a launcher shipped in `priv-app` carrying a priority on its HOME filter always wins and no chooser is ever offered. Ask your box with the `resolve-activity` command under Install. |
| **Uninstall** | `ACTION_DELETE` is refused for system packages — on Fire TV that means every Amazon preinstall, elsewhere whatever the vendor bundled. Those rows are greyed out. Removing them needs `adb shell pm disable-user --user 0 <pkg>`. |
| **Continue Watching** | Measured on both test devices: the *Watch Next*, preview-program and channel providers all exist and are all **completely empty**. No public provider on either box carries playback position or episode titles, and a stock launcher's own row is fed by private signed providers a third-party launcher cannot read. So the row shows genuine **recently-watched apps** from `UsageStatsManager`, not invented episode progress. Full evidence below, including how to make real poster art appear. |
| **Users** | No public API lets a third-party launcher enumerate or switch TV profiles. The category shows the current profile name and a route into system settings. Nothing more is possible. |
| **Move / favourites / category order** | Not system concepts anywhere. Every TV launcher keeps its own; ours live in `SharedPreferences`. The category row order is stored as keys, and keys it does not mention keep their default order and land at the end — so a category added in a later version appears for people who have already rearranged the row, rather than silently vanishing. |

### Fire TV specifically

Measured on an **AFTCU864A2** (Fire OS 8.1.8.5, Android 11). These are the points where
Fire OS departs from stock, and each one cost real debugging time to find.

| Area | Reality |
|---|---|
| **Home button** | Not merely a chooser you lose — a resolution you never enter. Amazon's launcher holds priority 950 as a privileged system app. Full measurement immediately below. |
| **Usage access** | `android.settings.USAGE_ACCESS_SETTINGS` resolves to `com.amazon.tv.settings.v2.compliance.CTSDummySettingActivity` — a stub Amazon ships to pass compliance tests. There is no working UI to grant it, which is why the ADB line under Install is not optional here. |
| **`queryUsageStats` is near-useless here** | It returned 222 buckets of which only 5 carried a usable `lastTimeUsed`. The **event stream** (`queryEvents`, `MOVE_TO_FOREGROUND`) is where the history actually lives, so that is the primary source with the buckets as fallback. That ordering is a safe default on any box. |
| **Phantom keyboard and mouse** | Fire OS ships two synthetic input devices that are *always* connected: `amzkeyboard` (reports `KEYBOARD_TYPE_ALPHABETIC`) and `kcmouse` (reports `SOURCE_MOUSE`). Any "is a keyboard attached?" check based on sources or keyboard type alone is true on every Fire TV. Both report vendor/product `0x0000`, while real HID peripherals do not — that is what `rememberHasKeyboardOrMouse()` filters on. |

### Why the Home button cannot be claimed on Fire TV

Not folklore — this is what the device reports. Every activity registered for
`CATEGORY_HOME`, with the priority the system actually recorded:

| Priority | Activity |
|---:|---|
| **950** | `com.amazon.tv.launcher/.ui.HomeActivity_vNext` |
| **1** | `com.amazon.firehomestarter/.HomeStarterActivity` |
| **0** | `com.ember.xmb/.MainActivity` ← us |
| **−999** | `com.amazon.servicemenu/.MainActivity` |
| **−1000** | `com.amazon.tv.settings.v2/.system.FallbackHome` |

The resolver takes the highest priority, so Amazon's launcher wins before any chooser
is ever offered. Two routes were tried and both failed:

- **`adb shell cmd package set-home-activity com.ember.xmb/.MainActivity`** — prints
  `Success`, then changes nothing. HOME still resolves to priority 950 and the Home
  button still opens Amazon's launcher. A silent no-op.
- **`android:priority` on our HOME intent-filter** — zeroed by the platform. Tested at
  **960**: legal (the documented range is greater than −1000 and less than 1000), one
  above Amazon's 950, and confirmed present in the compiled binary manifest with
  `aapt2 dump xmltree` → `android:priority(0x0101001c)=960`. After install the system
  still recorded our filter at **0**.

  The clamp is not about the value. Android forces activity intent-filter priority to
  0 for any **non-privileged** package. Amazon keeps 950 only because its launcher
  ships as a privileged system app in `/system_ext/priv-app`. No manifest edit, and no
  value in range, can change this from a sideloaded APK.

Note the ladder also means disabling only `com.amazon.tv.launcher` would not promote
us — `firehomestarter` at priority 1 is still ahead. Both would have to go.

**Practical setup:** launch from the Apps row, and remap a spare remote button (Button
Mapper, via Accessibility) to treat as your real Home.

### The input bug: Compose focus will silently eat your D-pad

Symptom on the device: after using the launcher for a while, the remote stopped
working. Up and Down did nothing, apps would not open, and the cursor appeared to jump
to the top. The app was still focused, still repainting, still animating the
background. Restarting it fixed it until it happened again.

What was actually happening: `Activity.onKeyDown` only runs if no focused View consumed
the key first. `Modifier.clickable` and `Modifier.combinedClickable` make their node
**focusable**. Those were added for mouse support. Once Compose focus landed on one —
which the context menu and the pickers reliably caused — Compose's focus system began
consuming D-pad keys for focus traversal, and the activity never saw them again.

The giveaway was that the GL background kept animating. That runs on its own thread, so
the screen looked alive while input was gone, which is why it read as "slow" or
"broken" rather than "frozen".

Two changes, either of which would fix it; both are in:

- **`dispatchKeyEvent` instead of `onKeyDown`.** It is called before the view hierarchy
  gets the event, so the cross can never be starved of input by whatever Compose
  believes is focused.
- **`pointerInput` + `detectTapGestures` instead of `clickable`.** Taps still work for
  mouse users, but the nodes are no longer focusable, so the root cause is gone.

If you add any `clickable` to this UI later, use `pointerInput` instead, or you will
reintroduce this.

### Why Continue Watching shows app banners, not video thumbnails

`ContinueWatchingRepository` tries three sources, best first:

1. **Watch Next** — an app saying "you were part-way through this". Real poster art,
   real episode numbers, real playback position.
2. **Preview programs** — the cards inside an app's home-screen channel. Also real
   poster art, often with a playback position. Rows with a position sort first.
3. **`UsageStatsManager` recents** — apps you actually opened, drawn with their TV
   banner. Honest, and labelled by recency rather than dressed up as episode progress.

It reaches step 3 because steps 1 and 2 find nothing. Measured on both devices:

```
watch_next_program   No result found
preview_program      No result found
channel              No result found
program              No result found
recorded_program     No result found
notifications        0 records          (rules out legacy recommendations)
media_session        0 sessions         (rules out live metadata)
READ_EPG_DATA        granted=true       (rules out permissions)
```

**The permission is granted and the tables are simply empty.** Nothing installed is
publishing. YouTube in particular holds `WRITE_EPG_DATA` and does not use it to expose
watch history — its home-screen row is its own integration, not readable by anyone
else. There is no public API anywhere on the device that reports "videos this user
watched".

**To get real thumbnails:** add an app's channel to the Android TV home screen (stock
launcher → the app's row → *Add channel*). The app then publishes preview programs
with poster art, and source 2 picks them up with no further changes. Apps that write
Watch Next properly — Netflix and Prime Video do — populate source 1 automatically
once used.

### Running on weaker Android TV hardware

Tested on an SDMC/Nebula box (`DM8261_NEBULA`, Android 9, **Mali-450 MP**, GLES 2.0
only, 2 GB RAM, 720p) as well as the Fire TV. Two things matter there:

**Mali-400/450 has no `highp` in fragment shaders at all.** The CPU-side phase wrapping
described below is not an optimisation on this hardware — it is the only reason the
ribbon does not quantise into garbage.

**The fragment shader was doing work that does not vary per pixel.** For a while the
wave was drawn into a quarter-size offscreen texture and stretched back up, on the
theory that native resolution was simply unaffordable on this GPU. It was not; the
shader was just the wrong shape. Almost none of its cost actually varied per pixel:

- six `hsl2rgb` conversions that depend only on hue and the time-of-day shade, so they
  are **constant for the whole frame** — now computed once on the CPU and passed as
  uniforms;
- nine `sin` calls that depend only on x, so they are **constant down each column** —
  now evaluated in a vertex shader over a 256-slice strip per band, which also lets each
  strip follow its own centreline and so only rasterise the sliver of screen that band
  covers.

What is left per pixel is one smoothstep and two exps per band. Measured on the
Mali-450 with a `glFinish()` around the draw calls:

| | Per-pixel everything | Into a 0.28x FBO | Split passes |
|---|---:|---:|---:|
| Resolution | 1280x720 | 358x202 | **1280x720** |
| GPU per frame | 200 ms | 34 ms | **11.7 ms** |

Three times cheaper than the version rendering an eighth of the pixels, seventeen times
cheaper than the one rendering the same pixels, and the offscreen texture is gone rather
than merely smaller — 289 KB at 0.28x on a 720p panel, around 2 MB at 0.5x on a 1080p
one. `GL_RENDERER` now selects only the frame rate (15fps on
Mali-4xx-class parts, 30fps elsewhere); resolution is always native. Against the 66 ms
pump, 11.7 ms is 18% of the GPU, and the headroom is deliberately left unspent on a
passively cooled box.

Two defects went with it, both of which had been read as "the upscale looks bad":

- **Edges were quantised by fp16 cancellation, not by the upscale.** `d = abs(y - cy)`
  differenced two ~1000-magnitude numbers, and fp16 spacing at that magnitude is 0.5 px
  *regardless of how small the result is*. Every ribbon edge was stepped before anything
  was stretched. The band's distance from its centreline is now subtracted in the vertex
  shader's `highp` and passed as a small varying; traced edge jitter is 0.08 px median,
  well under the old 0.5 px floor.
- **The dither was being applied and then averaged away.** It ran inside the low-res
  pass, so the bilinear upscale erased it. It also multiplied `gl_FragCoord` by ~123,
  which overflows the fp16 ceiling of 65504 past about x=530, feeding `fract()` an
  infinity. It is now a 4x4 ordered (Bayer) pattern in the full-resolution pass, where
  every intermediate stays under 16.

**Still outstanding on that box:** `Number Slow UI thread: 103` of 266 frames, where
the Fire TV shows 0. The background is no longer the bottleneck there; Compose is.
Reducing recomposition during category changes is the next thing worth doing if that
device matters.

### Performance, and one mistake worth not repeating

The first build on hardware looked stepped and banded and felt sluggish. Three causes,
all measured rather than guessed:

1. **The shimmer was the frame.** Three mote layers x nine cells of hashing per pixel,
   for two million pixels, every frame. On a real panel it also read as a static dot
   grid rather than sparkle. Removed.
2. **`uTime` destroyed `mediump` precision.** The shader computed `x * fr + uTime * sp`
   and `uTime` grows without bound — ten minutes in it is 600, leaving almost no bits
   for the part that varies per pixel. That was the jagged look. Switching to `highp`
   fixed the picture but took idle CPU from 3% to 11.7%. The actual fix is to keep the
   large term off the GPU: the CPU wraps each band's phase into `[0, 2pi)` in double
   precision and uploads that, so `mediump` is exact enough and stays cheap.
3. **The column composed every app.** A 60-app category composed 60 rows, each running
   three animations. It now composes only a window around the selection. Frame stats
   afterwards showed `Number Slow UI thread: 0` — composition had stopped being the
   bottleneck.

Tried and reverted: rendering the background below native resolution via
`holder.setFixedSize()`. Median frame went 27ms → 21ms, but the renderer kept being
handed pre-resize dimensions, so `uRes` disagreed with the real buffer and the bands
drew at the wrong scale. Not worth the fragility for that gain. The FBO that replaced
it has since gone too — see above; reducing resolution turned out to be the wrong lever
entirely, and both attempts at it were treating a symptom.

### Verified on hardware

Two devices, both over network ADB, deliberately at opposite ends of the range:

| | Fire TV | Android TV box |
|---|---|---|
| Model | **AFTCU864A2** | **SDMC DM8261_NEBULA** |
| OS | Fire OS 8.1.8.5 / Android 11 | Android 9 |
| GPU | — | Mali-450 MP, GLES 2.0 only |
| Panel / RAM | 1080p | 720p / 2 GB |

Confirmed on the Fire TV: the GL background composites under Compose, the shader links,
app icons and labels load from `PackageManager`, D-pad navigation, long-press context
menu with Uninstall correctly disabled for system packages, the seasonal theme, and the
theme picker persisting across restarts. Idles at ~3% CPU, `Slow UI thread: 0`.

Confirmed on the Nebula box: the background at native 1280x720 and 11.7 ms of GPU per
frame, the frame pump holding `avg=66ms worst=72ms` against a 66 ms target, the ordered
dither surviving to the framebuffer, ribbon edge jitter at 0.08 px median, and category
reordering end to end — including the order surviving a force-stop.

**The signed release build**, which is what the Download link serves, was installed on the
Nebula box and run. R8 and the resource shrinker are the risk in a Compose app — they are
where a release build fails while the debug build it was cut from works — so what matters is
that it starts at all: no `ClassNotFoundException`, no `VerifyError`, nothing in logcat from
`AndroidRuntime`. It reaches the home screen, the PS3 drawables render (the shrinker renames
resource *files*, so seeing them on screen is the only honest check), `PackageManager` icons
decode in the Apps column, D-pad navigation works, and the process survives it. The package
flags no longer carry `DEBUGGABLE`.

One number moved the right way: `Slow UI thread` went from 103 frames of 266 on the debug
build to **0**, which is R8 doing what it is for. Overall jank still reads high — 161 of 195
frames — but that figure counts anything over 16 ms, and the frame pump deliberately targets
33 ms on this hardware, so almost every frame is "janky" by a measure the app is not trying
to satisfy. The 50th percentile of 25 ms is the number to read.

**What has not been checked where.** The Fire TV figures above predate the background
rewrite, the PS3 icon artwork, the preview-program path in Continue Watching and the
rearrangeable category row; that box has been off the network since, so those were verified
on the Nebula box only — including everything in the paragraph above. Neither device is a
Google-certified Android TV or Google TV box, so the Google TV row in the install table is
standard platform procedure rather than something measured here.

---

## Tuning

| What | Where |
|---|---|
| Wave speed | `WaveRenderer.speed` (1.0 = as designed) |
| Frame pacing | `FRAME_MS_DEFAULT` / `FRAME_MS_LOW_END` in `WaveView.kt` (33 = 30fps, 66 = 15fps) |
| Ribbon shape | the three `Band(...)` entries in `WaveRenderer.BANDS`, in `WaveView.kt` |
| Band colours | `syncColours()` in `WaveView.kt`; the shader only reads the uniforms |
| Palette spread | `SPREAD_LO` / `SPREAD_HI` in `XmbPalette.kt` (the analogous window) |
| Cross geometry | `rememberMetrics` in `XmbScreen.kt` |

The last three rows moved when the background was rewritten. Ribbon shape used to
live in `band(...)` calls inside a single `Shaders.FRAGMENT`; the bands are now
triangle strips whose geometry comes from `BANDS`, so there is no per-pixel band
function left to edit. The shimmer `motes(...)` layer was dropped at the same time.

### Typeface

The prototype used Titillium Web, which is close to the PS3's Rodin. No font is
bundled here, so it renders in the system sans. To match the prototype, drop a
`.ttf` into `app/src/main/res/font/` and set it as the `fontFamily` in `XmbLabel`.
