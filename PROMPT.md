# The prompt

Paste everything below into an AI coding assistant running in a clone of this repository, with
the TV reachable from the same computer. It walks the assistant through connecting, backing up,
installing Ember XMB and making it the home screen — stopping to ask you before anything that is
awkward to undo.

You do not need this file to use the repo. Every step it describes is a script in `tools/` that
you can run yourself; `README.md` covers that route. This is for when you would rather delegate.

---

You are helping me put the Ember XMB launcher on my Android TV box. The repository you are in
builds it. Work through the steps below in order. Show me the output of each script rather than
summarising it, and stop at every point marked **ask me**.

Ground rules for the whole job:

- Never disable or uninstall a package I have not approved by name.
- `pm disable-user` only, never `pm uninstall`, for anything that shipped with the TV.
- If a script fails, show me its output and stop. Do not improvise a workaround that a later
  `tools/restore.sh` would not undo.
- The TV is my daily television. Treat a broken home screen as a serious outcome, not a retry.

## 1. Connect

Run `tools/connect.sh` and follow what it prints. It will ask whether to use a USB cable or
Wi-Fi, test the connection, and remember the answer in `tools/tv.conf`.

Tell me the model and Android version it reports. If it says the box is a Fire TV, stop and read
step 5 to me before going any further — the outcome there is different.

## 2. Back up before changing anything

Run `tools/backup.sh`. It writes package lists, the current home apps, `getprop` and all three
settings namespaces into `state/backup-<date>-<time>/`.

This is the snapshot `tools/restore.sh` reads settings back from, and it only has value if it is
taken before the first change. Confirm to me that it exists and is not empty.

## 3. Ask me

Before touching anything, ask me these and wait for answers:

- Do I want Ember XMB to become the home screen, or just installed so I can launch it manually?
- Do I want to disable any of the TV's own apps? If yes, show me the candidate list from
  `state/backup-*/packages-system.txt` and let me pick. Do not suggest a default list.
- Is this box a Fire TV? (If so, the home screen question is already answered: no.)

## 4. Install

Run `tools/build-install.sh`. It builds a release APK, installs it, grants usage access, sets the
home activity and then checks whether that actually worked.

If it reports an install failure, read the guidance it prints. `--reinstall` gets past a key or
version mismatch, but it wipes my favourites, category assignments, row order and theme — **ask
me** before using it.

If I said in step 3 that I only want it installed, pass `--build-only` first so I can see the APK
build cleanly, then install without changing the home activity by telling me the single `adb`
command rather than running the full script.

## 5. What "home screen" means on each box

- **Stock Android TV / Google TV** — `set-home-activity` works. If HOME still opens the old
  launcher afterwards, the stock one usually has to be disabled first; the script says so and
  names the package. **Ask me** before disabling it.
- **Fire TV** — it cannot be done, and no amount of retrying changes that. Fire OS gives its own
  launcher an intent-filter priority of 950, and Android forces every non-privileged package to
  0, so a sideloaded launcher can never outbid it. `set-home-activity` returns Success and
  changes nothing. Do not report success on a Fire TV because that command printed Success —
  check what actually has focus. Tell me to launch it from Settings > Applications, or to bind it
  to a remote button with something like Button Mapper.

## 6. Debloat, only if I asked for it

Only if I approved packages in step 3:

```
tools/disable.sh <batch-name> <package> [<package> ...]
```

Small batches, with `tools/shot.sh <name>` between them so I can see what the screen looks like.
Every success is logged to `state/disabled.txt`, which is what makes it reversible.

On a Fire TV, `tools/fire-tv-packages.txt` is a list I have already vetted:

```
tools/disable.sh amazon --list tools/fire-tv-packages.txt
```

Nothing outside that file without asking me first.

## 7. Verify with screenshots

Run `tools/shot.sh home`, then a few more with D-pad keycodes to move around:

```
tools/shot.sh home
tools/shot.sh apps KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
tools/shot.sh column KEYCODE_DPAD_DOWN
```

Look at the images in `state/shots/` and tell me what you see — specifically whether the wave
background is animating, whether app icons loaded, and whether the clock is right. Do not tell me
it works because a script exited 0.

## 8. Finish

Summarise, in plain terms:

- what is installed, and which build (release or debug)
- whether Ember XMB is actually the home screen, and on a Fire TV say plainly that it is not
- every package that was disabled, and that `tools/restore.sh` puts them all back
- anything that failed or that you could not check

Then tell me the one command that undoes all of it:

```
tools/restore.sh
```
