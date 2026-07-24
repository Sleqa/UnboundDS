# GBA Pal

Android companion app for GBA Pokemon games, running via RetroArch's mGBA
core. Talks to RetroArch's Network Command Interface (UDP, default port
`55355`) to read live game memory. Built for dual-screen handhelds like the
Ayn Thor, but works as a normal single-screen app too.

Formerly "UnboundDS" — under the hood it's still wired up for **Pokemon
Unbound** specifically (its memory map, name tables, and sprites), but the
rename marks the intent to genericize the game-specific pieces so it can
support other GBA Pokemon titles/hacks later.

## Status

Live companion hub: connects to RetroArch, tracks your party (sprites,
nicknames, levels) in banners on the home screen, auto-opens an opponent
view on battle start and returns to the hub once you move again, and has a
detail screen per Pokemon (stats, moveset, type match-ups). A handful of
read-only dev tools (memory inspector, diff scanner, anchor verification,
DexNav probe) sit behind a settings shortcut for confirming/discovering RAM
addresses.

## Building

```
./gradlew assembleDebug   # debug build
./gradlew assembleRelease # release build
```

`assembleRelease` is signed with the keystore committed at
`keystore/release.keystore`. It's intentionally not a secret — this is a
personal, read-only companion app with nothing sensitive in it. The only
reason it's fixed is so Obtainium/sideloaded updates install in place
instead of forcing an uninstall (which a changing signature would require).
If you ever want a private key instead, override it with Gradle properties
(`-PreleaseStoreFile=... -PreleaseStorePassword=...` etc.) without touching
the committed defaults.

## Using with RetroArch

1. In RetroArch: **Settings → Network → Network Commands** → enable, port
   `55355`.
2. Load Pokemon Unbound with the mGBA core.
3. Install this app on the same device and tap **Test connection**.

## Installing updates via Obtainium

Every push to `main` builds a release APK via GitHub Actions and publishes
it as a GitHub Release (tag `v0.1.<build number>`). Add this repo to
[Obtainium](https://github.com/ImranR98/Obtainium) as a GitHub source to
track and auto-update from those releases.
