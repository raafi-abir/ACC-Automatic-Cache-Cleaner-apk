EXT=${EXT:-/storage/emulated/0/Android}
SD=${SD:-/storage/emulated/0}
DATA=${DATA:-/data}
OK=0
LEFT=0

if [ "$DO_CACHE" = 1 ]; then
  # Per-app cache, same call Settings uses for "Clear cache".
  for p in $PKGS; do
    case "$(pm clear --cache-only "$p" 2>&1)" in
      *Success*) OK=$((OK + 1)) ;;
    esac
  done
  # If --cache-only isn't supported, fall back to a global trim (can't honour exclusions).
  if [ "$OK" -eq 0 ] && [ -n "$PKGS" ] && [ "$ALLOW_TRIM" = 1 ]; then
    pm trim-caches 4096G >/dev/null 2>&1
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

echo "ok=$OK left=$LEFT"
