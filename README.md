# UnboundDS

Android companion app for **Pokemon Unbound**, running via RetroArch's mGBA
core. Talks to RetroArch's Network Command Interface (UDP, default port
`55355`) to read live game memory. Built for dual-screen handhelds like the
Ayn Thor, but works as a normal single-screen app too.

## Status

Early scaffold. The current build is a memory inspector: connect to
RetroArch, test the connection (`VERSION`), and read raw bytes from a GBA
memory address (`READ_CORE_MEMORY`). This exists to validate the network
path and to discover/confirm Pokemon Unbound's RAM layout before any
game-specific decoding (party, bag, map, etc.) is built on top of it.

See `.claude`-adjacent planning notes for the full staged roadmap
(networking → memory map/decoding → party/map/bag UI → second-screen
support → visual polish).

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
