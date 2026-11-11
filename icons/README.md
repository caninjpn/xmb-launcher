# PS3 XMB icons

All 66 icons cut out of the source sheet onto transparent backgrounds, plus the two
scripts that did it so the extraction is repeatable rather than a one-off.

## Provenance, plainly

**These are Sony's PlayStation 3 XMB icons.** Using them to theme a launcher on your
own TV is ordinary personal modding. They are not ours to redistribute, so this
project should not be published or shared with the artwork in it — note that fourteen
of them are also in `app/src/main/res/drawable-nodpi/`, which means the built APK
contains them too. Keep it to your own devices.

`icons/ps3-extracted/` is gitignored for the same reason `pulled/apk/` is.

## How the extraction works

`find_icons.py` → `cut_icons.py`, run in that order:

```bash
python find_icons.py <sheet.png> out/
python cut_icons.py  <sheet.png> out/boxes.json cut/
```

Three things made this more than a crop:

**Multi-part glyphs.** Most of the Settings icons are a wrench *beside* an object —
two disconnected blobs that plain connected-component labelling would cut in half. The
finder labels components first, then merges any whose boxes sit within 10px, which
reassembles them.

**Stacked icons merging into one.** That merge then over-joined three pairs that were
vertically adjacent (the house and the add-user face, two wrench icons, the pencil and
the discs). `cut_icons.py` splits any box tall enough to be two icons at its widest
internal empty row band, then shrinks the halves back onto their ink.

**Keeping the antialiasing.** Alpha is `255 - luminance` rather than a hard white key,
so the original edge softness survives. RGB is left as the source pixels, which costs
nothing because the launcher tints at draw time, and tinting replaces RGB while
keeping alpha.

`labelled_sheet.png` is the index map — useful if you want to swap any assignment.

## What the launcher uses

| Index | File in `res/drawable-nodpi` | Used for |
|---:|---|---|
| 3 | `ps3_users.png` | Users category, and the profile row |
| 22 | `ps3_continue.png` | Continue Watching (playlist glyph) |
| 28 | `ps3_games.png` | Games |
| 25 | `ps3_video.png` | Video |
| 20 | `ps3_music.png` | Music |
| 17 | `ps3_photo.png` | Photo |
| 29 | `ps3_apps.png` | Apps, and the Applications settings row |
| 4 | `ps3_settings.png` | Settings — the authentic XMB toolbox |
| 39 | `ps3_network.png` | Network |
| 21 | `ps3_theme.png` | Theme |
| 12 | `ps3_display.png` | Display & Sound |
| 11 | `ps3_developer.png` | Developer Options |
| 41 | `ps3_device.png` | Device |
| 40 | `ps3_about.png` | About |

The **context menu keeps its own vector icons on purpose.** That panel is deliberately
Android TV's look rather than the XMB's, and PS3 glyphs would clash inside it.

## The one real limitation

The source sheet is small — these cut out at 34–70px. On a 720p screen they land close
to native. On a 1080p TV the category row draws them around 76–85px, so they are
magnified roughly 1.5–2x and go slightly soft.

There is no fix that keeps the promise of pixel-exact shapes: upscaling cannot invent
detail, and tracing them to vectors would be redrawing them rather than using them. If
crispness on 1080p matters more than exactness, a higher-resolution source sheet is
the only real answer.
