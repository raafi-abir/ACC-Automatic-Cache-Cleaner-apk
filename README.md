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

## Publish a release

1. One time: make a signing key and add it as repo secrets (Settings, Secrets and variables, Actions).
   ```
   keytool -genkeypair -v -keystore acc-release.jks -alias acc -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 acc-release.jks     # paste into KEYSTORE_BASE64
   ```
   The other secrets are `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`acc`) and `KEY_PASSWORD`. Back the `.jks` file up somewhere
   safe. If you lose it, nobody can update the app without uninstalling first.
2. Wait for the `Build APK` workflow to pass on `main`.
3. Tag and push: `git tag v1.0 && git push origin v1.0`.
4. The `Release` workflow builds the APK, names it `ACC-Shizuku-v1.0.apk`, adds a SHA-256 file, and publishes it
   under the repo's Releases page. `versionName` comes from the tag and `versionCode` from the run number.

## Use

1. Install and start Shizuku (wireless debugging or root), or just have a root manager ready.
2. Open ACC, tap the status link to grant permission (for root, allow the su prompt). ACC then grants itself usage access through Shizuku so it can read cache sizes.
3. Clean now, or turn on scheduled cleaning.

## How it works

| Piece | What it does |
|---|---|
| `ShellService` | Shizuku user service. Runs `app/src/main/assets/clean.sh` as shell. |
| `RootBridge` / `Executor` | Runs the same script through `su`, or through Shizuku, depending on the access method. |
| `clean.sh` | `pm trim-caches` (global, the call shell is most reliably allowed to make); `pm clear --cache-only <pkg>` per app only when apps are excluded or the trim fails. Both run under a time limit. Also deletes `Android/data/*/cache`, thumbnails, `logcat -c`, optional leftover folders. With root it also empties `cache` and `code_cache` directly under `/data/user/0` and `/data/user_de/0`. |
| `CacheScanner` | Per-app cache size via `StorageStatsManager`. |
| `CleaningService` | Foreground service (type specialUse) that hosts a long accessibility clean and shows progress with a Stop action. |
| `Scheduler` / `CleanWorker` | WorkManager, 24 h period, first run at the chosen time. |

## Limits without root

- Shizuku in ADB mode (not root) can't delete other apps' private files directly. App cache goes through `pm trim-caches`, which clears every app and can't skip any. With excluded apps ACC has to clear apps one by one with `pm clear --cache-only`, which some Android versions refuse for the shell user. In that case the run fails with an explanation instead of silently doing nothing, and you can remove the exclusions to use the global clean.
- After the main pass ACC measures again and clears any app still holding 256 KB or more one by one, then shows how much
  is left ("freed 812 MB, 40 MB left"). Some cache always comes back, because running apps rebuild it straight away.
- If a clean fails, Access method > Copy last clean log copies the script output for a bug report.
- If Shizuku isn't running at the scheduled time (it has to be restarted after every reboot in ADB mode), the run is skipped and you get a notification.
- WorkManager is approximate. The clean runs around the chosen time, not on the second.
- Some devices block shell access to `Android/data`. The external-cache and leftover steps then do nothing.

## Without Shizuku or root (accessibility)

Set Access method to Accessibility, then turn on ACC under Settings > Accessibility. On Android 13+ a
sideloaded app is blocked first: open ACC's App info, tap the three-dot menu, choose "Allow restricted
settings", then enable the service. Also grant usage access (the status link opens it) so ACC knows which apps
have cache.

By default, for each app with at least 1 MB of cache (largest first, 40 apps at most per run) ACC opens App info,
then Storage, then taps Clear cache, as you would by hand. Turn on "Clean every app" under What to clean to remove
both limits (manual runs only, and it takes much longer). The screen is kept on during a run.

- It can only clear app cache. Thumbnails, logs, external cache and leftover folders need Shizuku or root.
- A run takes several seconds per app and takes over the screen. It runs in a foreground service, so Android keeps
  ACC alive, and the notification has a Stop button. Pressing Home also stops it.
- The screen must be on and unlocked, so a scheduled run while the phone is locked is skipped and you get a notification.
- Button labels come from the Settings app's own strings, so they match your language. OEM Settings layouts that
  differ from stock Android may not work.
- The service only reads and taps Settings windows, and only taps a button labelled exactly "Clear cache".

### Banking apps (bKash and similar)

Some banking and wallet apps refuse to run while any accessibility service is on. That is their own security check,
and ACC doesn't try to hide from it. Instead ACC keeps the service off whenever it isn't cleaning:

- **Turn it off after cleaning** (Access method screen, on by default): after each run ACC switches its own service off.
  Without more setup you turn the service on again in Settings before the next clean.
- **Fully automatic:** grant ACC one extra permission once from a computer with USB debugging:
  `adb shell pm grant io.github.raafiabir.acc android.permission.WRITE_SECURE_SETTINGS`.
  ACC then switches the service on right before a clean and off right after, including scheduled ones, so the
  service is only on for the few minutes a clean takes. The Access method screen shows the command with a Copy button.
- If your banking app still complains outside a clean, check that the service really is off under Settings > Accessibility.

## Updates

ACC checks GitHub Releases for a newer version about once a day (and when you open the app if the last check is
over 12 hours old), shows an "Update available" banner on the home screen, and sends one notification per new
release. Download opens the APK in your browser; nothing is installed automatically.

- It needs the `INTERNET` permission and only talks to `api.github.com`. Turn it off under Updates > Check automatically.
- The repo it checks is set at build time: the workflows pass `-PupdateRepo=<owner>/<repo>`, so builds from your repo
  look at your repo. For local builds the default is `raafi-abir/ACC-Automatic-Cache-Cleaner-apk`; change it in `app/build.gradle.kts`.
- Versions are named `1.0`, then `1.1`, `1.2`, `1.3` and so on, and the git tag is the same with a `v` (`v1.0`, `v1.1`).
  The app compares the release tag with its own version number by number, so `v1.10` counts as newer than `v1.9`,
  and a patch release like `v1.2.1` works too.
- `tests/VersionsTest.kt` covers the comparison. Run it with a Kotlin compiler:
  `kotlinc app/src/main/java/io/github/raafiabir/acc/Versions.kt tests/VersionsTest.kt -include-runtime -d t.jar && java -jar t.jar`

## Fonts

The UI uses DM Sans (variable font in `app/src/main/res/font/dmsans.ttf`), licensed under the SIL Open Font
License 1.1. Source: https://github.com/google/fonts/tree/main/ofl/dmsans
