# ACC for Shizuku

Non-root version of [ACC](https://github.com/raafi-abir/ACC-Automatic-Cache-Cleaner).
Same idea (cache, thumbnails/logs, leftover folders, exclusions, daily schedule), but it's a normal
app. It gets its privileges from [Shizuku](https://shizuku.rikka.app/) or, if you have it, direct root
(`su`). With neither, it can drive the Settings app through an accessibility service.
"Access method" in the app picks Auto (Shizuku, then root, then accessibility), Shizuku, Root, or Accessibility.

## Build

Push to GitHub. The `Build APK` workflow produces `ACC-Shizuku-apk` under the run's artifacts.

Locally: open the folder in Android Studio, or run `gradle wrapper` once and then `./gradlew :app:assembleRelease`.
Without signing secrets the APK is signed with the debug key. Add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD` as repo secrets to sign with your own.

## Use

1. Install and start Shizuku (wireless debugging or root), or just have a root manager ready.
2. Open ACC, tap the status link to grant permission (for root, allow the su prompt). ACC then grants itself usage access through Shizuku so it can read cache sizes.
3. Clean now, or turn on scheduled cleaning.

## How it works

| Piece | What it does |
|---|---|
| `ShellService` | Shizuku user service. Runs `app/src/main/assets/clean.sh` as shell. |
| `RootBridge` / `Executor` | Runs the same script through `su`, or through Shizuku, depending on the access method. |
| `clean.sh` | `pm clear --cache-only <pkg>` per app, deletes `Android/data/*/cache`, thumbnails, `logcat -c`, optional leftover folders. With root it also empties `cache` and `code_cache` directly under `/data/user/0` and `/data/user_de/0`. |
| `CacheScanner` | Per-app cache size via `StorageStatsManager`. |
| `Scheduler` / `CleanWorker` | WorkManager, 24 h period, first run at the chosen time. |

## Limits without root

- Shizuku in ADB mode (not root) can't delete other apps' private files directly. App cache goes through `pm clear --cache-only`, which needs a reasonably recent Android. If it's unsupported, ACC falls back to `pm trim-caches` (all apps, ignores exclusions).
- If Shizuku isn't running at the scheduled time (it has to be restarted after every reboot in ADB mode), the run is skipped and you get a notification.
- WorkManager is approximate. The clean runs around the chosen time, not on the second.
- Some devices block shell access to `Android/data`. The external-cache and leftover steps then do nothing.

## Without Shizuku or root (accessibility)

Set Access method to Accessibility, then turn on ACC under Settings > Accessibility. On Android 13+ a
sideloaded app is blocked first: open ACC's App info, tap the three-dot menu, choose "Allow restricted
settings", then enable the service. Also grant usage access (the status link opens it) so ACC knows which apps
have cache.

For each app with at least 1 MB of cache (largest first, 40 apps at most per run) ACC opens App info, then
Storage, then taps Clear cache, as you would by hand.

- It can only clear app cache. Thumbnails, logs, external cache and leftover folders need Shizuku or root.
- A run takes several seconds per app and takes over the screen. Press Home to stop it.
- The screen must be on and unlocked, so a scheduled run while the phone is locked is skipped and you get a notification.
- Button labels come from the Settings app's own strings, so they match your language. OEM Settings layouts that
  differ from stock Android may not work.
- The service only reads and taps Settings windows, and only taps a button labelled exactly "Clear cache".
