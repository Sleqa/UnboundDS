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

`assembleRelease` falls back to debug signing when no release keystore is
configured (fine for local testing). CI signs with a real keystore kept out
of the repo — see below.

### CI release signing setup (one-time)

Obtainium needs release APKs signed with the *same* key across builds, or it
can't install an update over an existing install. Generate a keystore once
and store it as GitHub Actions secrets (never commit it):

```
keytool -genkeypair -v -keystore release.keystore -alias unboundds \
  -keyalg RSA -keysize 2048 -validity 36500
base64 -w0 release.keystore   # copy this value
```

Add these repo secrets (Settings → Secrets and variables → Actions):

- `RELEASE_KEYSTORE_BASE64` — output of the `base64` command above
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS` (`unboundds` if you used the command above)
- `RELEASE_KEY_PASSWORD`

Without these secrets, CI still builds and releases a (debug-signed) APK —
it just won't support seamless in-place updates until the secrets are set.

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
