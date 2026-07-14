# Between — private chat and phone files

Between is a two-device sideload app. Android owns the authoritative chat transcript and exposes selected shared-storage folders. The browser keeps its own transcript and unsent queue locally. Both connect **outward** to one public HTTPS/WSS relay, so they can be on unrelated Wi-Fi or mobile networks.

## Privacy model

The relay keeps only live WebSocket connections, temporary request routing, and unclaimed pairing codes in memory. It does not persist:

- messages or an offline message queue
- file listings, file chunks, or downloads
- chat transcripts
- device endpoints

Android saves the transcript as newline-delimited JSON in an app-private `messages.txt`. Each change rewrites it atomically. The browser saves its transcript and pending messages in browser local storage. A browser message waits locally if the phone is offline and sends when that same browser and the phone are online together.

The relay can see traffic while forwarding it. HTTPS/WSS encrypts the network path, but this version is not end-to-end encrypted from browser to phone.

Images and other attachments are forwarded as live chunks only. The relay does not store the files,
queue them for offline delivery, or write them to a database. Each connected client keeps its own
local copy after receiving an attachment.

## Local-only testing app

The long-lived `testing` branch includes a side-by-side sandbox named `testing _001`. It uses the
package ID `com.example.privatevault.testing`, has a bug launcher icon, and removes internet,
pairing, foreground-service, notification, and broad storage permissions. Messages and attachments
remain inside this sandbox app and never connect to the relay.

Build it locally with:

```powershell
.\build-testing-apk.bat
```

The APK is written to `android/app/build/outputs/apk/sandbox/app-sandbox.apk`. Pushes to the
`testing` branch also produce a short-lived `testing_001` artifact in GitHub Actions without
creating a production release.

## Required public setup

Different-location use requires a stable public host that supports long-lived WebSockets. Deploy the repository's `Dockerfile` and configure:

```text
DEVICE_REGISTRATION_KEY=<the generated private key>
PORT=8787
```

The host must terminate HTTPS and forward WebSocket upgrades on `/relay`. The same process serves the built web portal and `/auth/login`, so the public base URL is also the site URL.

`render.yaml` is included for a single-instance Singapore Render deployment. Its free plan can sleep after 15 minutes without inbound HTTP or WebSocket traffic and may take about a minute to wake; the clients reconnect automatically.

Generate/read the matching key once:

```powershell
.\scripts\initialize-public-relay.ps1
```

Set that exact value as `DEVICE_REGISTRATION_KEY` on the public host. Do not share it; possession of it permits a client to register as a phone.

After deployment, build the APK with the public URL:

```powershell
.\start-private-chat.cmd https://your-relay.example
adb install -r ".\android\app\build\outputs\apk\debug\app-debug.apk"
```

The URL is remembered in ignored `output/config/relay-url.txt`. Re-running `start-private-chat.cmd` without an argument reuses it. Remote setup rejects plain HTTP because codes, chat, tokens, and files must travel over HTTPS/WSS.

## Daily workflow

The PC launcher is not required after the relay/site is deployed and the APK is installed.

1. Open Between on Android. On first launch, grant all-files access once and tap **Start chat**.
2. The chat home screen shows a six-digit one-time code after the phone reaches the relay.
3. Open `https://your-relay.example` from any location and enter that code.
4. Chat and Files work whenever both devices overlap online.

The code is accepted only while the phone is connected and advertising it. It is consumed after one successful pairing. Create a replacement under **Settings → Pairing** for a new browser.

## Chat receipts

- `✓` — saved on the sender's device
- `✓✓` — copied to the other device
- blue `✓✓` — read on the other device

With no server-side message storage, closing the browser leaves its unsent queue in that browser until it is opened again. Phone-originated messages remain in the phone's local transcript and are copied when the paired browser reconnects.

## Android updates

The Android app checks the repository's latest public GitHub Release whenever it comes to the foreground. If the release tag is newer than the installed `versionName` and the release contains an APK, the app offers to download it. The download opens in the phone's browser; Android still asks the user to approve the sideloaded installation.

Push a tag matching the version in `android/app/build.gradle.kts` (for example, `v0.2.2`) to run `.github/workflows/android-release.yml`. The workflow builds a signed APK and attaches it to a GitHub Release. GitHub Actions must have `ANDROID_BACKEND_URL` and `DEVICE_REGISTRATION_KEY` for the production relay, plus `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` for signing. Release builds fail if the public HTTPS relay URL or registration key is missing. Keep the signing key unchanged: Android rejects updates signed with a different key, and uninstalling to switch keys erases the app's local messages.

## Shared storage boundary

The one-time Android all-files permission is requested through the system settings screen. The remote interface exposes only readable shared content:

- Downloads, DCIM, Pictures, Documents, Music, Movies, and user-created public folders
- readable `Android/media` content under the **App media** root

Hidden folders, `Android/data`, `Android/obb`, app-private storage, system files, and path traversal are blocked. Remote delete, rename, move, and upload are not implemented. **Pause files** locks file requests while leaving chat connected.

## Local development only

```powershell
.\scripts\start-private-chat.ps1 -LocalDev
```

This intentionally uses the PC LAN address and is only for testing on one network. It is not the remote workflow.

## Verification

```powershell
npm run check
npm run build
docker build -t between-relay .

cd android
.\gradlew.bat :app:assembleDebug --no-daemon `
  -PpocBackendUrl=https://your-relay.example `
  -PpocRegistrationKey=your-private-registration-key
```

## Project structure

```text
android/   Kotlin + Compose app, local messages.txt, outbound relay client
backend/   In-memory WebSocket relay, pairing endpoint, built-site host
web/       React chat-first portal, local transcript and offline queue
shared/    TypeScript API contracts
```
