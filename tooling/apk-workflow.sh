#!/usr/bin/env bash
# ============================================================================
# tooling/apk-workflow.sh — base-APK snapshot + mod-over-base build pipeline
#
# Raijin's Lawncher loads the game from whatever is installed at
# com.touchfoo.swordigo (see MainActivity.startGame). This script automates
# the surrounding workflow on a host machine:
#
#   fetch   – search a connected device for the installed game APK, pull it
#             and store it as a pristine "base" (vanilla), or import any
#             custom Swordigo APK (e.g. a SwordigoDesktop-style build) as a
#             custom base.
#   list    – show the stored bases.
#   build   – build a signed, installable mod APK *on top of* a base:
#             base APK + mod payload (assets/ + lib/ + properties.toml).
#   install – adb install a built APK.
#
# Requirements (all part of a standard Android SDK, no Gradle needed):
#   platform-tools/adb, build-tools/{aapt2,zipalign,apksigner}, zip, unzip,
#   keytool (JDK).
#
# Usage:
#   ./tooling/apk-workflow.sh fetch                       # from connected device
#   ./tooling/apk-workflow.sh fetch --serial SERIAL
#   ./tooling/apk-workflow.sh fetch --custom custom.apk   # import any APK as base
#   ./tooling/apk-workflow.sh list
#   ./tooling/apk-workflow.sh build -b vanilla -m mods/my-mod -o out.apk
#   ./tooling/apk-workflow.sh build -b custom -m mods/my-mod --install
#   ./tooling/apk-workflow.sh install mods/my-mod-1.0.0.apk
#
# Env overrides:
#   ANDROID_HOME   SDK root (defaults to the documented location)
#   ADB            adb binary path
# ============================================================================
set -euo pipefail

SDK="${ANDROID_HOME:-/run/media/quantumcreeper/TVPG/linuxFiles/Applications/android-ndk}"
BT="${SDK}/build-tools/37.0.0"
ADB="${ADB:-${SDK}/platform-tools/adb}"
PKG="com.touchfoo.swordigo"

TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${TOOL_DIR}/.." && pwd)"
BASES_DIR="${ROOT}/bases"                 # bases/vanilla, bases/custom
OUT_DIR="${TOOL_DIR}/out"                 # built mod APKs + keystore
KEYSTORE="${TOOL_DIR}/debug.keystore"

# Clean up build staging even when set -e aborts mid-build
STAGE_DIR=""
trap 'rm -rf "${STAGE_DIR:-}" "${OUT_DIR:-}/_stage.zip" "${OUT_DIR:-}/_aligned.apk"' EXIT

# ---------------------------------------------------------------- helpers --

log()  { printf '\033[1;34m[apk]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

require() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1"; }

check_tools() {
  require "${BT}/aapt2"; require "${BT}/zipalign"; require "${BT}/apksigner"
  require unzip; require zip; require keytool
  [ -x "$ADB" ] || die "adb not found at: $ADB (set ADB or ANDROID_HOME)"
}

# badge <apk> -> "package|versionCode|versionName" ("" if not an APK)
badge() {
  "${BT}/aapt2" dump badging "$1" 2>/dev/null | \
    sed -n "s/^package: name='\([^']*\)' versionCode='\([^']*\)' versionName='\([^']*\)'.*/\1|\2|\3/p" | head -1
}

# latest_apk <dir> -> newest .apk path ("" if none)
latest_apk() {
  find "$1" -maxdepth 1 -name '*.apk' -printf '%T@ %p\n' 2>/dev/null \
    | sort -rn | head -1 | cut -d' ' -f2-
}

# pick_device [serial] -> device serial; dies if none connected
pick_device() {
  local serial="${1:-}" devs
  devs="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  if [ -z "$devs" ]; then
    die "no device/emulator connected — plug one in (adb devices), or use: fetch --custom game.apk"
  fi
  if [ -n "$serial" ]; then
    echo "$devs" | grep -qx "$serial" || die "serial '$serial' not connected"
    echo "$serial"
  else
    local n; n="$(echo "$devs" | wc -l)"
    if [ "$n" -gt 1 ]; then warn "multiple devices connected — using '$(echo "$devs" | head -1)' (use --serial)"; fi
    echo "$devs" | head -1
  fi
}

# mod_field <moddir> <key> -> value from properties.toml ([mod] section, key="value")
mod_field() {
  local props="$1/properties.toml" key="$2"
  [ -f "$props" ] || return 0
  sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$props" | head -1
}

resolve_base() {
  case "$1" in
    vanilla) local f; f="$(latest_apk "${BASES_DIR}/vanilla")"
             [ -n "$f" ] || die "no vanilla base stored — run: fetch"; echo "$f";;
    custom)  local f; f="$(latest_apk "${BASES_DIR}/custom")"
             [ -n "$f" ] || die "no custom base stored — run: fetch --custom"; echo "$f";;
    *) [ -f "$1" ] && echo "$1" || die "base not found: '$1' (use 'vanilla' | 'custom' | path/to/base.apk)";;
  esac
}

ensure_keystore() {
  [ -f "$KEYSTORE" ] && return 0
  keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
}

# ------------------------------------------------------------------ fetch --

cmd_fetch() {
  local serial="" custom="" dir="${BASES_DIR}/vanilla"
  while [ $# -gt 0 ]; do
    case "$1" in
      --serial)  serial="$2";  shift 2;;
      --custom)  custom="$2";  shift 2;;
      --out)     dir="$2";     shift 2;;
      *) die "fetch: unknown option '$1'";;
    esac
  done
  check_tools

  if [ -n "$custom" ]; then cmd_import_custom "$custom" "${BASES_DIR}/custom"; return; fi

  local dev; dev="$(pick_device "$serial")"
  local paths
  paths="$(adb -s "$dev" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | sed 's/^package://')"
  [ -n "$paths" ] || die "package $PKG is NOT installed on '$dev' — install the game first, or use: fetch --custom game.apk"

  mkdir -p "$dir"
  # Separate the main APK from split APKs by filename (pm path ordering is not
  # guaranteed to put base.apk first).
  local main="" p
  local -a splits=()
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    case "$p" in
      */base.apk) main="$p";;
      *) splits+=("$p");;
    esac
  done <<< "$paths"
  [ -n "$main" ] || main="$(printf '%s' "$paths" | sed '/^$/d' | head -1)"

  local ver="" label
  local badge_tmp="${TMPDIR:-/tmp}/.lawncher_badge.$$"
  if [ -n "$main" ]; then
    adb -s "$dev" pull "$main" "$badge_tmp" >/dev/null 2>&1 || true
    local b; b="$(badge "$badge_tmp" 2>/dev/null || true)"
    ver="$(echo "$b" | cut -d'|' -f3)"; [ -n "$ver" ] || ver="unknown"
    label="${PKG}-v${ver}-base.apk"
    log "pulling ${main} -> ${dir}/${label}"
    adb -s "$dev" pull "$main" "${dir}/${label}" >/dev/null
  fi
  for p in "${splits[@]:-}"; do
    [ -n "$p" ] || continue
    local name; name="$(basename "$p")"
    label="${PKG}-v${ver:-unknown}-$(echo "$name" | sed 's/\.apk$//').apk"
    log "pulling ${p} -> ${dir}/${label}"
    adb -s "$dev" pull "$p" "${dir}/${label}" >/dev/null
  done
  rm -f "$badge_tmp"

  {
    echo "# vanilla base snapshot — fetched $(date '+%Y-%m-%d %H:%M:%S')"
    echo "device=$dev"
    local b; b="$(badge "${dir}/${PKG}"-v"${ver}"-base.apk 2>/dev/null || true)"
    echo "package=$(echo "$b" | cut -d'|' -f1)"
    echo "versionCode=$(echo "$b" | cut -d'|' -f2)"
    echo "versionName=$(echo "$b" | cut -d'|' -f3)"
    echo "files="
    ls -1 "$dir" | grep -v '^manifest.txt$' | sed 's/^/  /'
  } > "${dir}/manifest.txt"
  log "vanilla base stored under ${dir}"
  log "build a mod over it:  ./tooling/apk-workflow.sh build -b vanilla -m mods/my-mod"
}

cmd_import_custom() {
  local apk="$1" dir="$2"
  [ -f "$apk" ] || die "custom apk not found: $apk"
  local b; b="$(badge "$apk")"
  [ -n "$b" ] || die "not a valid apk: $apk"
  local pkg ver; pkg="$(echo "$b" | cut -d'|' -f1)"; ver="$(echo "$b" | cut -d'|' -f3)"
  if [ "$pkg" != "$PKG" ]; then
    warn "custom base package is '$pkg', not '$PKG' — the launcher only auto-detects '$PKG' at runtime"
    warn "install the custom build AS package '$PKG', or keep it for the mod build pipeline only"
  fi
  mkdir -p "$dir"
  local name="${pkg}-v${ver}-custom.apk"
  cp "$apk" "${dir}/${name}"
  {
    echo "# custom base — imported $(date '+%Y-%m-%d %H:%M:%S') from $apk"
    echo "package=$pkg"; echo "versionCode=$(echo "$b" | cut -d'|' -f2)"; echo "versionName=$ver"
  } > "${dir}/manifest.txt"
  log "custom base stored: ${dir}/${name}  (${pkg} v${ver})"
}

# ------------------------------------------------------------------ list --

cmd_list() {
  local found=0
  for kind in vanilla custom; do
    while IFS= read -r f; do
      [ -f "$f" ] || continue
      found=1
      printf '%-8s %-42s %s\n' "$kind" "$(basename "$f")" "[$(badge "$f")]"
    done < <(find "${BASES_DIR}/${kind}" -maxdepth 1 -name '*.apk' 2>/dev/null | sort)
  done
  if [ "$found" -eq 0 ]; then
    echo "no bases stored yet — connect a device and run: fetch   (or: fetch --custom game.apk)"
    echo "built mods appear in: ${OUT_DIR}/mods/"
  fi
}

# ----------------------------------------------------------------- build --

cmd_build() {
  local base="" mod="" out="" splits="" do_install=""
  while [ $# -gt 0 ]; do
    case "$1" in
      -b|--base)    base="$2";     shift 2;;
      -m|--mod)     mod="$2";      shift 2;;
      -o|--out)     out="$2";      shift 2;;
      --splits)     splits="$2";   shift 2;;
      --install)    do_install=1;  shift;;
      *) die "build: unknown option '$1'";;
    esac
  done
  check_tools
  [ -n "$base" ] && [ -n "$mod" ] || die "build requires: -b BASE (vanilla|custom|path.apk) -m MODDIR"
  [ -d "$mod" ] || die "mod dir not found: $mod"

  local base_apk; base_apk="$(resolve_base "$base")"
  log "base:   $base_apk"
  log "mod:    $mod"

  # Mod payload must contain at least assets/ or lib/
  if [ ! -d "$mod/assets" ] && [ ! -d "$mod/lib" ]; then
    die "mod dir must contain an 'assets/' and/or 'lib/' payload (see tooling/README.md)"
  fi

  local name version
  name="$(mod_field "$mod" name)";    [ -n "$name" ] || name="$(basename "$mod")"
  version="$(mod_field "$mod" version)"; [ -n "$version" ] || version="1.0.0"
  [ -z "$out" ] && out="${OUT_DIR}/mods/${name}-${version}.apk"

  local stage; stage="$(mktemp -d "${TMPDIR:-/tmp}/apk-stage.XXXXXX")"
  STAGE_DIR="$stage"
  local stage_zip="${OUT_DIR}/_stage.zip"

  # 1. Unpack base, drop old signatures
  unzip -q "$base_apk" -d "$stage"
  rm -rf "$stage/META-INF"

  # 2. Merge mod payload
  [ -d "$mod/assets" ] && cp -r "$mod/assets/." "$stage/assets/"
  [ -d "$mod/lib" ]    && cp -r "$mod/lib/." "$stage/lib/"

  # 3. Optional: inject native libs from stored split APKs (e.g. ABI splits)
  if [ -n "$splits" ]; then
    local s
    for s in "${splits}"/*split*.apk; do
      [ -f "$s" ] || continue
      log "merging libs from split: $(basename "$s")"
      unzip -o -q "$s" 'lib/*' -d "$stage"
    done
  fi

  # 4. Repack: assets/classes stored compressed, lib/* stored uncompressed
  mkdir -p "$(dirname "$out")" "$OUT_DIR"
  rm -f "$stage_zip"
  ( cd "$stage" && \
    find . -type f ! -path './lib/*' | zip -X -q "$stage_zip" -@ && \
    { [ -d lib ] && find lib -type f | zip -0 -X -q "$stage_zip" -@ || true; } )

  # 5. Align + sign (persistent debug keystore so all built mods share a key)
  ensure_keystore
  "${BT}/zipalign" -f -p 4 "$stage_zip" "${OUT_DIR}/_aligned.apk"
  "${BT}/apksigner" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android --out "$out" "${OUT_DIR}/_aligned.apk"

  rm -rf "$stage" "$stage_zip" "${OUT_DIR}/_aligned.apk"

  # 6. Verify
  "${BT}/apksigner" verify "$out"
  log "BUILT: $out  [$(badge "$out")]"
  log "install it:  ./tooling/apk-workflow.sh install $out   (or pass --install next time)"
  log "note: a debug-signed mod cannot be installed OVER the Play-signed vanilla —"
  log "      uninstall com.touchfoo.swordigo first (game data is lost), or keep"
  log "      the vanilla installed and use in-app runtime mods instead."

  if [ "$do_install" = "1" ]; then
    local dev; dev="$(pick_device "")"
    log "installing on $dev ..."
    adb -s "$dev" install -r "$out"
  fi
}

# --------------------------------------------------------------- install --

cmd_install() {
  check_tools
  local apk="${1:-}"
  if [ -z "$apk" ]; then
    apk="$(latest_apk "${OUT_DIR}/mods")"
    [ -n "$apk" ] || die "no built mod found under ${OUT_DIR}/mods — build one first"
  fi
  [ -f "$apk" ] || die "apk not found: $apk"
  local dev; dev="$(pick_device "")"
  log "installing $apk on $dev ..."
  adb -s "$dev" install -r "$apk"
}

# ------------------------------------------------------------------ main --

usage() {
  sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
}

case "${1:-}" in
  fetch)   shift; cmd_fetch "$@";;
  list)    cmd_list;;
  build)   shift; cmd_build "$@";;
  install) shift; cmd_install "$@";;
  help|-h|--help) usage;;
  *) usage >&2; die "unknown command: '${1:-}' (use fetch | list | build | install)";;
esac
