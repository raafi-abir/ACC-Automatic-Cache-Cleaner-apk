#!/bin/sh
# Runs app/src/main/assets/clean.sh against a fake filesystem and a fake `pm` executable.
# Usage: sh tests/clean_test.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SCRIPT="$HERE/../app/src/main/assets/clean.sh"
FAILS=0

check() { # description, test-expression
  if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; FAILS=$((FAILS + 1)); fi
}

T=$(mktemp -d)
BIN="$T/bin"
mkdir -p "$BIN"
cat > "$BIN/pm" <<'STUB'
#!/bin/sh
# Behaviour is set with PM_TRIM=ok|fail and PM_CLEAR=ok|fail. Every call is logged to $PM_LOG.
echo "$*" >> "$PM_LOG"
case "$1" in
  trim-caches)
    if [ "$PM_TRIM" = fail ]; then echo "java.lang.SecurityException: not allowed" >&2; exit 255; fi
    exit 0 ;;
  clear)
    if [ "$PM_CLEAR" = fail ]; then echo Failed; exit 1; fi
    echo Success; exit 0 ;;
  list)
    n=${PM_LIST_N:-12}; i=0
    while [ "$i" -lt "$n" ]; do echo "package:com.x$i"; i=$((i + 1)); done
    [ "$n" -gt 0 ] && { echo package:com.keep; echo package:com.skip; }
    exit 0 ;;
esac
STUB
chmod +x "$BIN/pm"

fixture() { # $1 = dir
  mkdir -p "$1"/Android/data/com.keep/cache "$1"/Android/data/com.skip/cache "$1"/Android/data/com.gone/cache \
           "$1"/Android/obb/com.gone "$1"/Android/obb/com.keep "$1"/DCIM/.thumbnails \
           "$1"/data/user/0/com.keep/cache "$1"/data/user/0/com.keep/code_cache \
           "$1"/data/user/0/com.skip/cache "$1"/data/user/0/com.me/cache "$1"/data/user_de/0/com.keep/cache
  for f in Android/data/com.keep/cache/a Android/data/com.skip/cache/b Android/data/com.gone/cache/c \
           DCIM/.thumbnails/t data/user/0/com.keep/cache/d data/user/0/com.keep/code_cache/e \
           data/user/0/com.skip/cache/f data/user/0/com.me/cache/g data/user_de/0/com.keep/cache/h; do
    touch "$1/$f"
  done
}

# run DIR ROOT DO_LEFT ALLOW_TRIM  (env: PM_TRIM, PM_CLEAR, PM_LIST_N)
run() {
  {
    echo "PKGS='com.keep com.skip'"
    echo "EXCL=' com.skip '"
    echo "DO_CACHE=1"; echo "DO_JUNK=1"; echo "DO_LEFT=$3"; echo "ALLOW_TRIM=$4"; echo "ROOT=$2"; echo "SELF=com.me"
    cat "$SCRIPT"
  } > "$1/run.sh"
  sh -n "$1/run.sh"
  : > "$1/pm.log"
  PM_LOG="$1/pm.log" PATH="$BIN:$PATH" EXT="$1/Android" SD="$1" DATA="$1/data" sh "$1/run.sh"
}

echo "# global trim works (no exclusions)"
fixture "$T/a"; OUT=$(PM_TRIM=ok run "$T/a" 0 0 1)
check "trim-caches was called"             "grep -q '^trim-caches' $T/a/pm.log"
check "per-app clear skipped"              "! grep -q '^clear' $T/a/pm.log"
check "result reports trim=0"              "echo '$OUT' | grep -q 'trim=0'"
check "external cache cleared"             "[ ! -e $T/a/Android/data/com.keep/cache/a ]"
check "thumbnails cleared"                 "[ ! -e $T/a/DCIM/.thumbnails/t ]"
check "internal data untouched (non-root)" "[ -e $T/a/data/user/0/com.keep/cache/d ]"

echo "# global trim refused: fall back to per-app and report why"
fixture "$T/b"; OUT=$(PM_TRIM=fail run "$T/b" 0 0 1)
check "trim attempted"                     "grep -q '^trim-caches' $T/b/pm.log"
check "per-app clear used as fallback"     "[ \$(grep -c '^clear' $T/b/pm.log) -eq 2 ]"
check "result reports ok=2"                "echo '$OUT' | grep -q 'ok=2'"
check "error line carries the reason"      "echo '$OUT' | grep -q 'err=java.lang.SecurityException'"

echo "# exclusions set: trim not allowed, per-app only"
fixture "$T/c"; OUT=$(PM_CLEAR=ok run "$T/c" 0 0 0)
check "trim-caches not called"             "! grep -q '^trim-caches' $T/c/pm.log"
check "per-app clear called"               "grep -q '^clear --cache-only com.keep' $T/c/pm.log"
check "excluded app external cache kept"   "[ -e $T/c/Android/data/com.skip/cache/b ]"
check "result reports ok=2 trim=-"         "echo '$OUT' | grep -q 'ok=2 .*trim=-'"

echo "# nothing works"
fixture "$T/d"; OUT=$(PM_CLEAR=fail run "$T/d" 0 0 0)
check "ok=0 so the app can report an error" "echo '$OUT' | grep -q 'ok=0'"

echo "# root"
fixture "$T/e"; PM_TRIM=ok run "$T/e" 1 0 1 >/dev/null
check "internal cache cleared"             "[ ! -e $T/e/data/user/0/com.keep/cache/d ]"
check "code_cache cleared"                 "[ ! -e $T/e/data/user/0/com.keep/code_cache/e ]"
check "device-encrypted cache cleared"     "[ ! -e $T/e/data/user_de/0/com.keep/cache/h ]"
check "excluded app internal cache kept"   "[ -e $T/e/data/user/0/com.skip/cache/f ]"
check "ACC's own cache kept"               "[ -e $T/e/data/user/0/com.me/cache/g ]"

echo "# leftovers on"
fixture "$T/f"; OUT=$(PM_TRIM=ok run "$T/f" 0 1 1)
check "uninstalled app data removed"       "[ ! -d $T/f/Android/data/com.gone ]"
check "uninstalled app obb removed"        "[ ! -d $T/f/Android/obb/com.gone ]"
check "installed app obb kept"             "[ -d $T/f/Android/obb/com.keep ]"
check "result line reports 2 removed"      "echo '$OUT' | grep -q 'left=2'"

echo "# leftovers on but package list is broken"
fixture "$T/g"; PM_TRIM=ok PM_LIST_N=0 run "$T/g" 0 1 1 >/dev/null
check "nothing deleted when list is empty" "[ -d $T/g/Android/data/com.gone ] && [ -d $T/g/Android/obb/com.gone ]"

rm -rf "$T"
[ "$FAILS" -eq 0 ] && echo "all passed" || { echo "$FAILS failed"; exit 1; }
