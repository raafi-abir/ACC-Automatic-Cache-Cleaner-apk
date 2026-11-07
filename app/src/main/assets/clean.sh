EXT=${EXT:-/storage/emulated/0/Android}
SD=${SD:-/storage/emulated/0}
DATA=${DATA:-/data}
OK=0
LEFT=0
TRIM_RC=-
TRIM_ERR=

# Runs a command with a time limit when `timeout` exists, so one stuck call can't hang the whole clean.
run() {
  secs=$1
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "$secs" "$@"
  else
    "$@"
  fi
}

if [ "$DO_CACHE" = 1 ]; then
  if [ "$ALLOW_TRIM" = 1 ]; then
    # Global trim: the call the shell user is most reliably allowed to make. Frees every app's cache.
    TRIM_OUT=$(run 180 pm trim-caches 4096G 2>&1)
    TRIM_RC=$?
    if [ "$TRIM_RC" -ne 0 ]; then
      TRIM_ERR=$(printf '%s\n' "$TRIM_OUT" | head -n 1)
    fi
  fi
  if [ "$TRIM_RC" != 0 ]; then
    # Per-app clear: needed when apps are excluded, and the fallback if the global trim failed.
    FAILS=0
    for p in $PKGS; do
      case "$(run 10 pm clear --cache-only "$p" 2>&1)" in
        *Success*)
          OK=$((OK + 1))
          FAILS=0
          ;;
        *)
          FAILS=$((FAILS + 1))
          # Refused three times in a row with no success: this phone won't do it, stop wasting time.
          if [ "$OK" -eq 0 ] && [ "$FAILS" -ge 3 ]; then break; fi
          ;;
      esac
    done
  fi
  # Root only: also empty cache and code_cache straight from app storage.
  if [ "$ROOT" = 1 ]; then
    for base in "$DATA/user/0" "$DATA/user_de/0"; do
      for d in "$base"/*/cache "$base"/*/code_cache; do
        [ -d "$d" ] || continue
        p=${d#"$base"/}
        p=${p%%/*}
        [ "$p" = "$SELF" ] && continue
        case "$EXCL" in *" $p "*) continue ;; esac
        rm -rf "$d"/* "$d"/.[!.]* 2>/dev/null
      done
    done
  fi
  # External cache folders.
  for d in "$EXT"/data/*/cache; do
    [ -d "$d" ] || continue
    p=${d#"$EXT"/data/}
    p=${p%/cache}
    case "$EXCL" in *" $p "*) continue ;; esac
    rm -rf "$d"/* "$d"/.[!.]* 2>/dev/null
  done
fi

if [ "$DO_JUNK" = 1 ]; then
  rm -rf "$SD"/DCIM/.thumbnails/* "$SD"/Pictures/.thumbnails/* 2>/dev/null
  logcat -c 2>/dev/null
fi

if [ "$DO_LEFT" = 1 ]; then
  INST=$(pm list packages 2>/dev/null | sed 's/^package://')
  # Safety: if the package list looks broken, delete nothing.
  if [ "$(printf '%s\n' "$INST" | wc -l)" -gt 10 ]; then
    for base in data obb; do
      for d in "$EXT/$base"/*/; do
        [ -d "$d" ] || continue
        p=$(basename "$d")
        case "$p" in *.*) ;; *) continue ;; esac
        case "$EXCL" in *" $p "*) continue ;; esac
        if ! printf '%s\n' "$INST" | grep -qx "$p"; then
          rm -rf "$d"
          LEFT=$((LEFT + 1))
        fi
      done
    done
  fi
fi

echo "ok=$OK left=$LEFT trim=$TRIM_RC"
if [ -n "$TRIM_ERR" ]; then
  echo "err=$TRIM_ERR"
fi
