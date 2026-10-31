#!/bin/sh
# Runs app/src/main/assets/clean.sh against a fake filesystem with a stubbed `pm`.
# Usage: sh tests/clean_test.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SCRIPT="$HERE/../app/src/main/assets/clean.sh"
FAILS=0

check() { # description, test-expression
  if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; FAILS=$((FAILS + 1)); fi
}

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

run() { # $1 dir, $2 ROOT, $3 DO_LEFT, $4 pm-list-count
  {
    echo "pm() { case \"\$1\" in clear) echo Success ;; list) i=0; while [ \$i -lt $4 ]; do echo package:com.x\$i; i=\$((i+1)); done; [ $4 -gt 0 ] && { echo package:com.keep; echo package:com.skip; } ;; esac; }"
    echo "PKGS='com.keep com.skip'"
    echo "EXCL=' com.skip '"
    echo "DO_CACHE=1"; echo "DO_JUNK=1"; echo "DO_LEFT=$3"; echo "ALLOW_TRIM=0"; echo "ROOT=$2"; echo "SELF=com.me"
    cat "$SCRIPT"
  } > "$1/run.sh"
  sh -n "$1/run.sh"
  EXT="$1/Android" SD="$1" DATA="$1/data" sh "$1/run.sh"
}

T=$(mktemp -d)

echo "# non-root, leftovers off"
fixture "$T/a"; run "$T/a" 0 0 12 >/dev/null
check "external cache cleared"            "[ ! -e $T/a/Android/data/com.keep/cache/a ]"
check "excluded app external cache kept"  "[ -e $T/a/Android/data/com.skip/cache/b ]"
check "thumbnails cleared"                "[ ! -e $T/a/DCIM/.thumbnails/t ]"
check "internal data untouched"           "[ -e $T/a/data/user/0/com.keep/cache/d ]"
check "leftover folders kept when off"    "[ -d $T/a/Android/obb/com.gone ]"

echo "# root"
fixture "$T/b"; run "$T/b" 1 0 12 >/dev/null
check "internal cache cleared"            "[ ! -e $T/b/data/user/0/com.keep/cache/d ]"
check "code_cache cleared"                "[ ! -e $T/b/data/user/0/com.keep/code_cache/e ]"
check "device-encrypted cache cleared"    "[ ! -e $T/b/data/user_de/0/com.keep/cache/h ]"
check "excluded app internal cache kept"  "[ -e $T/b/data/user/0/com.skip/cache/f ]"
check "ACC's own cache kept"              "[ -e $T/b/data/user/0/com.me/cache/g ]"

echo "# leftovers on"
fixture "$T/c"; OUT=$(run "$T/c" 0 1 12)
check "uninstalled app data removed"      "[ ! -d $T/c/Android/data/com.gone ]"
check "uninstalled app obb removed"       "[ ! -d $T/c/Android/obb/com.gone ]"
check "installed app obb kept"            "[ -d $T/c/Android/obb/com.keep ]"
check "result line reports 2 removed"     "echo '$OUT' | grep -q 'left=2'"

echo "# leftovers on but package list is broken"
fixture "$T/d"; run "$T/d" 0 1 0 >/dev/null
check "nothing deleted when list is empty" "[ -d $T/d/Android/data/com.gone ] && [ -d $T/d/Android/obb/com.gone ]"

rm -rf "$T"
[ "$FAILS" -eq 0 ] && echo "all passed" || { echo "$FAILS failed"; exit 1; }
