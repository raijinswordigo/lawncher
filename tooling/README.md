# tooling/ — base-APK snapshot + mod build pipeline

The launcher runs the game from whatever is installed at
`com.touchfoo.swordigo` (see `MainActivity.startGame()`: it extracts
`libswordigo.so` and mounts the APK's assets at runtime). This directory
contains the **host-side automation** around that:

1. **Snapshot a pristine base** — pull the installed game APK from a connected
   device (or import any custom Swordigo APK) and store it untouched.
2. **Build mods over the base** — produce a signed, installable APK that is the
   base game *plus* a mod payload, ready to `adb install` in place of the game.

```
bases/                        ← stored pristine APKs (gitignored, large binaries)
  vanilla/  com.touchfoo.swordigo-v<ver>-base.apk   (+ ABI split APKs, manifest.txt)
  custom/   <pkg>-v<ver>-custom.apk                 (custom Swordigo builds)
tooling/
  apk-workflow.sh             ← fetch | list | build | install
  out/mods/*.apk              ← built mod APKs
  out/…                       ← staging, keystore (gitignored)
```

---

## Commands

```bash
# 1. Fetch the installed game from a connected device/emulator
./tooling/apk-workflow.sh fetch                    # first connected device
./tooling/apk-workflow.sh fetch --serial R58M2...  # specific device
./tooling/apk-workflow.sh list                     # show stored bases

# 2. Import a custom Swordigo APK as a base instead (e.g. a
#    SwordigoDesktop-style build, or a backup of the game APK)
./tooling/apk-workflow.sh fetch --custom /path/to/swordigo-custom.apk

# 3. Build a mod APK over a base
./tooling/apk-workflow.sh build -b vanilla -m mods/my-mod
./tooling/apk-workflow.sh build -b custom  -m mods/my-mod -o out.apk --install

# 4. Install a built mod APK
./tooling/apk-workflow.sh install out.apk          # or: install  (latest built)
```

`-b` accepts `vanilla` (newest snapshot), `custom` (newest imported), or an
explicit path. `--splits <dir>` merges native libs from stored ABI split APKs
into the build (needed when the base was pulled from a split-APK install).

Env: `ANDROID_HOME` (SDK root), `ADB` (defaults to `$ANDROID_HOME/platform-tools/adb`).

---

## Mod packaging spec (a mod = a directory)

```
my-mod/
  properties.toml     ← optional metadata (same schema as the in-app ModManager)
  assets/             ← merged into the APK's assets/ (overrides game assets)
    resources/...         asset paths are the game's own paths, e.g. resources/foo.png
  lib/<abi>/*.so      ← optional extra native libs (e.g. a custom engine)
```

```toml
[mod]
id="com.raijin.mymod"
name="My Mod"
version="1.0.0"
description="Replaces some assets."
```

Building runs: unpack base → drop old `META-INF` → merge `assets/` + `lib/` →
repack (libs stored uncompressed) → zipalign → sign with the persistent
`tooling/debug.keystore` → verify with `apksigner`.

Because every build signs with the **same** debug key, mods can be updated
over each other with `adb install -r`.

---

## Important: signatures & installs

- The Play Store game is signed by the developer. A debug-signed mod APK
  **cannot** be installed *over* it (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
  To use built mod APKs you must **uninstall the vanilla game first**
  (game saves are lost — back them up via the launcher's Files screen).
- The launcher doesn't care *which* signature is installed: it reads whatever
  is at `com.touchfoo.swordigo` at runtime. So after installing a modded base,
  the launcher works with it exactly as with the vanilla.
- **Alternative (no uninstall):** keep the vanilla game and use the launcher's
  built-in runtime mods (Mod Store / Mods screen → `ModManager`), which
  override assets at runtime without touching the installed APK. Baked mods
  and runtime mods compose: the baked APK is the base layer, runtime mods
  layer on top of it.

---

## Custom Swordigo bases ("like the SwordigoDesktop launcher")

SwordigoDesktop is a custom engine with its own build. If you can produce an
Android APK from it (or have any modified Swordigo APK), just import it:

```bash
./tooling/apk-workflow.sh fetch --custom swordigo-custom.apk
./tooling/apk-workflow.sh build -b custom -m mods/my-mod --install
```

For the **launcher** to run a custom build, its package id should stay
`com.touchfoo.swordigo` (the launcher looks that package up at runtime).
The script warns when the package differs — the custom base can still be used
for building mods, it just won't be auto-detected by the launcher unless it is
installed under the vanilla package id.

> Compiling SwordigoDesktop itself into an Android APK is a separate project
> (it targets the desktop ARM64 emulator); this pipeline consumes the *result*
> APK, whatever the source.
