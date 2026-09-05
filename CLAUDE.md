# CamNet – Multi-Phone Camera Monitor

## Project Overview

CamNet is a peer-to-peer multi-phone LAN security camera app. One phone acts as a **Monitor** (viewing hub), and other phones become **Cameras** (video sources). All communication happens locally over WiFi — no cloud, no APIs.

**Core Value:** Transform spare old phones into a cohesive security camera system with on-device AI motion detection, timelapse, and recording.

---

## Architecture

### Two-Port Design
- **Port 3000 (HTTP):** Ktor CIO server for Monitor UI (web app served via `loadDataWithBaseURL`)
- **Port 3443 (HTTPS):** SslProxy (raw TCP byte-copying SSL termination) for Camera phones on LAN

### WebRTC Signaling Flow
1. Monitor starts session (8-char room code, QR code, LAN IP)
2. Camera joins with code → WebSocket handshake with signaling server
3. ICE candidates & SDP offers/answers exchanged via WS
4. Peer connection established → audio/video stream flows directly between phones
5. Viewer receives stream, renders in `<video>`, motion detection on 160×120 canvas

### Key Components

| File | Role |
|------|------|
| `CamNetServer.kt` | Ktor server, WS signaling, HTTP asset serving, session management |
| `SslProxy.kt` | Raw socket SSL termination (passes WS upgrades transparently) |
| `MainActivity.kt` | Native Android UI wrapper; loads web views via `loadDataWithBaseURL` |
| `AndroidBridge.kt` | JavascriptInterface: `saveSnapshot`, `saveVideo`, `startStreaming`, `stopStreaming` |
| `public/js/viewer.js` | Monitor: WebRTC peer mgmt, motion detection, recording, timelapse, AI detection |
| `public/js/camera.js` | Camera: media capture, torch, mic, quality, bitrate, stealth mode, recording |
| `public/js/solo.js` | Solo: standalone motion detection, recording, flash strobe, audio alarm, ntfy push |
| `public/viewer.html` | Monitor UI (session mgmt, layout, settings, camera cards) |
| `public/camera.html` | Camera UI (join form, live screen, quality settings) |
| `public/solo.html` | Solo UI (preview, arm/disarm, flash/alarm/record controls, settings sheet) |
| `public/index.html` | Home screen with role selection (Monitor/Camera) |
| `public/css/app.css` | All styling (fullscreen, motion indicator, timelapse picker, etc.) |

---

## Current Features (Complete)

### Monitor (Viewer)
- ✅ Real-time multi-camera grid (auto, 1, 2, 3 col layout)
- ✅ Per-camera controls: snapshot, record, timelapse, mute, nightvision, flip, quality, stealth
- ✅ Motion detection: pixel-diff on downsampled canvas, polygon zone picker with point-in-polygon analysis
- ✅ **Smart Detection (AI):** TensorFlow.js + COCO-SSD (lite_mobilenet_v2)
  - Lazy-loaded from jsDelivr CDN (~3 MB)
  - Filters detected objects by `smartClasses` (8 configurable: Person/Car/Motorcycle/etc.)
  - Per-camera 3s cooldown + `pendingSmartDetect` guard prevents parallel inference
  - **Fallback:** If AI model fails to load, basic motion alerts fire automatically
- ✅ Motion alerts: text + toast, 4s display, auto-dismiss
- ✅ Settings panel: motion sensitivity (Low/Mid/High), auto-snapshot on motion, flash on motion + duration (1/2/5/10 min), mute all, mirror front cams, photo quality (480/720/1080/Source)
- ✅ Timelapse picker: interval/duration inputs, photo quality (use global / local override), video quality (480/720/1080), unlimited toggle, live file size estimate (JPEG + video bytes)
- ✅ Recording: local + remote options, 5-min segmentation (REC_SEGMENT_MS), WebM codec
- ✅ Fullscreen: CSS-based (native API fails on Android WebView)
- ✅ Session info panel: room code, QR code, copy code/link buttons
- ✅ Auto-snapshot and flash on motion (with persistence across disconnects)
- ✅ **Auto-update:** On launch, checks GitHub Releases API; shows dialog if newer APK found. Downloads via `DownloadManager`, installs via `FileProvider`. "⬆ Check for updates" button on home screen for manual trigger.
- ✅ **Two-way audio:** Monitor mic → all connected cameras. 🎤 header button; camera plays via hidden `<audio>`, shows "🎤 MONITOR" badge when active. Uses `sendrecv` transceiver + `replaceTrack`.
- ✅ **DVR rolling buffer:** Per-camera 24/7 rolling 30-min buffer (1-min segments, 30 max). 📼 button per card; playback modal with segment list (newest first) and video player. Oldest segments auto-purged.

### Solo Remote Admin (from Monitor)
- ✅ **📡 Solo Devices panel in Monitor viewer:** Header button opens admin panel. Add any Solo device by ntfy topic URL + name. Per-device card shows: 🟢/🟡/🔴 online dot (based on heartbeat age), armed/disarmed state, last seen, alert count, last alert time. Action buttons: Arm, Disarm, 📸 Snapshot, Ping, ↻ Restart, ✕ Remove. Devices persisted in `localStorage camnet.solo.devices`. Poll interval: 15s.
- ✅ **Solo heartbeat (solo.js):** When armed + ntfy configured, POSTs status JSON (`armed`, `motionCount`, `lastAlertAt`, `uptime`) to `{topic}-hb` every 60s with `Priority: min` (silent — no notification). Also sends immediate heartbeat on arm/disarm.
- ✅ **Solo command channel (solo.js):** Polls `{topic}-cmd/json?poll=1` every 10s for commands from the Monitor panel. Commands: `arm`, `disarm`, `snapshot` (captures + sends via ntfy), `ping` (immediate heartbeat), `restart` (reloads solo.html in-place). Uses ntfy as bidirectional bus — no direct device-to-device connection needed.

### Solo Mode (Single Phone)
- ✅ Standalone mode — no Wi-Fi, no Monitor phone, no Ktor server required
- ✅ Arm/Disarm toggle with status badge and WATCHING indicator
- ✅ Two-layer motion detection: pixel-diff + optional COCO-SSD AI (same as viewer.js)
- ✅ Polygon zone editor (ray-cast point-in-polygon, same as viewer.js)
- ✅ Torch strobe on motion: Off / Steady / Slow 1Hz / Fast 10Hz, auto-off duration
- ✅ Audio alarm on motion: Off / Beep (880Hz bursts) / Sustained tone (440Hz), Web Audio API, no files
- ✅ 5-min segment recording to DCIM/CamNet gallery
- ✅ Record-on-motion with configurable idle-stop window
- ✅ Push notifications via ntfy.sh (or any webhook) — `AndroidBridge.sendWebhookNotification`
- ✅ Native Android motion notification via `AndroidBridge.fireMotionAlert`
- ✅ Camera flip, snapshot, all settings persisted (localStorage `camnet.solo.*`)
- ✅ Back button → home; StreamingService keeps camera alive

### Camera (Phone)
- ✅ Join session with 6-char code or auto-join via QR `?room=XXXX` param
- ✅ 30-second connection timeout: if no `joined` msg after 30s, returns to setup
- ✅ Local recording (5-min segments), save to gallery
- ✅ Mic toggle (echoCancellation + noiseSuppression)
- ✅ Torch/flash control
- ✅ Quality picker (240/480/720/1080p) with bitrate enforcement
- ✅ Camera flip (front/rear)
- ✅ Stealth mode: black overlay, wake lock held, 3-tap exit
- ✅ Keep-alive: silent 0.001v audio + MediaSession to prevent OS tab suspension
- ✅ Wake lock (WakeLock API)
- ✅ Toast feedback on all button actions
- ✅ **Cancel button on setup screen** (visible while "Connecting…" state)
- ✅ **Back button on live screen** (return to setup)

---

## Known Issues & Fixes

No open issues. See [CHANGELOG.md](./CHANGELOG.md) for full fix history.

**Notable recent fixes (post-v1.118):**
- Solo AI detection is now a labeler, not a gate — always fires `onMotionDetected` (null when no class matched); basic detection fires immediately when AI inference is pending
- Solo stealth mode: 🥷 button, full-screen overlay, 3×-tap exit; all detection/recording/ntfy continue behind overlay
- ntfy push includes auto-captured 320px JPEG snapshot via `captureMotionSnap()` helper
- S24 Ultra freeze: stall watchdog (re-assigns `srcObject` if `currentTime` stalls), ICE `disconnected` → 8s delayed `restartIce()`, Monitor keep-alive oscillator added
- Moto G update: `setDestinationInExternalFilesDir`, removed blocked MIME type pre-enqueue, `canRequestPackageInstalls()` guard with Settings redirect

### Testing Checklist
- [ ] Motion detection fires in both basic and AI modes (motion on by default)
- [ ] Settings persist across app restarts (layout, motion, sens, quality, smart classes)
- [ ] Stealth mode button stays highlighted on Monitor; 3-tap exits stealth on Camera
- [ ] Camera name stays stable when camera disconnects and rejoins
- [ ] Monitor returns Camera to setup after MAX_CONNECT_ATTEMPTS failed retries
- [ ] Camera bottom bar scrolls horizontally on narrow phones (no wrap)
- [ ] Notification permission prompt appears on first Settings panel open (not on every motion start)
- [ ] Cancel button works on setup screen during connection attempt
- [ ] Back button on live screen returns to setup
- [ ] Timelapse warns at 800 frames and stops at 1000
- [ ] QR scanner accessible after navigating back from live screen
- [ ] Flash stays on for selected duration after motion
- [ ] Recording segments at 5 minutes

---

## Tech Stack

| Layer | Tech |
|-------|------|
| Server | Kotlin 2.2.10 (bundled via AGP builtInKotlin), Ktor 3.1.3 (CIO engine) |
| Android | AGP 9.0.1 + built-in Kotlin 2.2.10, compileSdk/targetSdk 35, minSdk 29 |
| Build | Gradle 9.5.1, Java 17 (Temurin), build-tools 36.0.0 |
| Web (Monitor/Camera) | HTML5, Vanilla JS, WebRTC, MediaRecorder |
| Styling | CSS Grid, flexbox, safe-area-inset, 100dvh |
| AI | TensorFlow.js 4.22.0 + COCO-SSD 2.2.3 (lite_mobilenet_v2) |
| Assets | SVG icons, JSON manifests |

---

## Development Notes

### Motion Detection Pipeline
1. Start motion analysis: `startMotion(cameraId)` creates 160×120 canvas, copies video frames every 400ms
2. Pixel-diff: compare each pixel ±threshold, count changed pixels
3. Zone constraint: if zone exists, only analyze rect region; else full frame
4. Trigger: if `changed/total > fraction` AND `now >= lastMotionAt + 15s cooldown` → fire alert
5. AI branch: if `smartDetectionEnabled && cocoModel && !pendingSmartDetect` → run inference (3s per-camera cooldown)
6. Fallback: if AI mode but model unavailable (or disabled) → basic alert always fires
7. Alert display: 4-second toast + motion indicator pill (green pulsing "MOTION ON" / "WATCHING ZONE" / "AI WATCHING" / "AI · ZONE")

### Recording Strategy
- **Monitor (viewer.js):** MediaRecorder on received stream, 5-min segments, auto-upload to Monitor phone gallery
- **Camera (camera.js):** MediaRecorder on local stream, WebM codec, 5-min segments, save via AndroidBridge.saveVideo() to gallery
- **Multipart upload:** Camera sends POST /api/save-video with X-Filename header (for on-device storage if Monitor is unavailable)

### Timelapse Strategy
- Frame capture: snapshot every N interval, compress as JPEG (quality varies by `photoQuality`)
- Photo mode: save each frame directly to gallery (individual JPEGs)
- Video mode: accumulate frames → render via MediaRecorder canvas stream at 24fps → save WebM with selected bitrate
- Size estimate: JPEG bytes by resolution bucket + (bitrate × frame count / 24 / 8) for video

### CSS Fullscreen
- Approach: `.cam-fullscreen { position:fixed; inset:0; z-index:9000; }` applied to card
- Why not native: Android WebView's fullscreen API requires `WebChromeClient.onShowCustomView()` callback (app would need custom WebChromeClient)
- Trade-off: CSS fullscreen works reliably, no native UI bloat, user can still see system time/battery

### AI Model Lazy Loading
- Path: `https://cdn.jsdelivr.net/npm/@tensorflow-models/coco-ssd@2.2.2/dist/coco_ssd.js`
- Trigger: first toggle of Smart Detection, or auto-load on Settings panel open
- Promise: `cocoLoadingPromise` prevents duplicate fetches during parallel toggles
- Fallback: if load fails (network, CDN down), `cocoModel = null`, basic motion fires

---

## Build & Deployment

### CI / GitHub Actions (`build-apk.yml`)
Every push to `main` or `claude/*` branches triggers a build:
1. Java 17 (Temurin) + Android SDK 34/35 + build-tools 35.0.0/36.0.0 + Gradle 9.5.1 installed
2. Signing keystore (`camnet-debug.jks`) restored from Actions cache (generated once, reused so APK updates install without uninstalling)
3. `BUILD_NUMBER` env var set to `github.run_number`; `build.gradle` reads it via `System.getenv("BUILD_NUMBER")` — no `sed` manipulation needed
4. `gradle assembleDebug --no-daemon` builds the APK
5. APK uploaded as a workflow artifact (90-day retention)
6. GitHub Release created with tag `v{run_number}` and APK attached (`permissions: contents: write` required at job level)

### Android (local)
```bash
cd android
# No gradlew wrapper — use system gradle or Android Studio
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Web (Development)
```bash
npm start  # or: node public/server.js
# Server: http://localhost:3000
# Camera: https://LAN_IP:3443/camera.html
```

### Deployment Steps
1. Push to branch → CI builds APK automatically → GitHub Release published
2. Install APK on Monitor phone + all Camera phones (app auto-updates on launch)
3. Start Monitor app → note session code / QR code
4. Open Camera app on each phone → enter code → stream begins
5. All communication stays on LAN (no internet required)

---

## Session Persistence & State

### Monitor (viewer.js)
- **Peers map:** `cameraId → { pc, name, stream, motion, recorder, timelapse, zone, lastMotionAt, ... }`
- **Recording state:** per-peer + global settings (sensitivity, flash duration, auto-snapshot, smart detection, etc.)
- **UI state:** hidden in localStorage (layout choice, settings panel open/close) — *not currently persisted, could be added*

### Camera (camera.js)
- **Local state:** `roomId, cameraId, localStream, recordActive, torchOn, facingMode, quality, micEnabled`
- **Reconnect:** on WS close, auto-retry every 3s up to 10 attempts (30s total), then give-up timer fires
- **Recording reset:** on `joined` msg, clear any stale recording state + call `stopCameraRecording()`

### Persistence
- ✅ Monitor settings persisted via `lsSave`/`lsLoad` (namespace `camnet.viewer.`): globalMotion, motionSens, muteAll, mirrorFront, currentLayout, photoQuality, smartDetectionEnabled, smartClasses
- Camera name optional; no persistent phone identity

---

## Future Work

See [ROADMAP.md](./ROADMAP.md) for planned features and deferred items.

---

## Security

### What's Protected

| Layer | Mechanism |
|-------|-----------|
| **WebRTC media** | DTLS-SRTP — end-to-end encrypted, mandatory. Monitor server cannot read media. |
| **Signaling (LAN)** | TLS via SslProxy — self-signed cert, acceptable for LAN. Requires real cert for WAN. |
| **Room code entropy** | 8 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (omits O/0/I/1). 32⁸ = 1.1 trillion combinations. Generated with `SecureRandom` (Java) / `crypto.randomInt` (Node.js). |
| **Join nonce** | 128-bit hex secret generated with room. Required alongside room code to join. QR and share link carry both `?room=CODE&nonce=HEX`. Typing the 8-char code without the nonce is rejected with `BAD_TOKEN`. |
| **Rate limiting** | 10 join attempts per IP per 60 s. 11th attempt rejected with `RATE_LIMITED`. In-memory, cleaned every 5 min. |
| **Optional session password** | Viewer sets password via settings panel. Hashed client-side with SHA-256 (`crypto.subtle`). Camera must enter matching password. Server compares using `timingSafeEqual` (Node.js) / string equality (Kotlin). Off by default. |

### Current Limitations
- No 2FA
- Signaling server (this app) can read SDP offers/answers — but NOT media (that's E2E encrypted)
- Password hash is stored in-memory on server; restarts clear it
- Self-signed TLS cert; camera phones get a browser warning on first connect

### WAN Deployment (future)
Requires: real TLS cert (Let's Encrypt), TURN server (coturn / Oracle Always Free), network-level rate limiting (Cloudflare WAF or iptables), and moving signaling to a persistent host.

---

## Getting Help

**For Claude Code sessions:**
- This CLAUDE.md is auto-loaded; ask directly about features, architecture, or bugs
- Refer to file paths: e.g., "Fix the motion detection in `public/js/viewer.js:974`"

**For Chat projects:**
- Upload this file to a Claude.ai Project so Chat has context
- Re-upload whenever I say "update CLAUDE.md"
- Chat can then answer high-level questions about the app state

---

## Repository Structure

```
camnet/
├── android/
│   └── app/src/main/
│       ├── java/com/camnet/app/
│       │   ├── MainActivity.kt          # WebView host, home/setup/camera screens
│       │   ├── CamNetServer.kt         # Ktor server + WS signaling
│       │   ├── SslProxy.kt             # HTTPS termination
│       │   ├── AndroidBridge.kt        # JS interface (save snapshots/videos)
│       │   ├── SignalingService.kt      # Foreground service: runs Ktor server + WS signaling
│       │   └── StreamingService.kt     # Foreground service: keeps camera alive with screen off
│       └── res/
│           ├── drawable/ic_launcher_foreground.xml  # App icon vector
│           ├── mipmap-anydpi-v26/ic_launcher.xml    # Adaptive icon
│           └── values/colors.xml
├── public/
│   ├── index.html                      # Home screen (role select)
│   ├── viewer.html                     # Monitor UI
│   ├── camera.html                     # Camera UI
│   ├── js/
│   │   ├── viewer.js                   # Monitor logic (motion, recording, timelapse, AI)
│   │   └── camera.js                   # Camera logic (media, recording, settings)
│   ├── css/
│   │   └── app.css                     # All styling
│   ├── icons/
│   │   ├── icon-192.svg                # App icon (192×192)
│   │   └── icon-512.svg                # App icon (512×512)
│   ├── manifest.json                   # PWA manifest
│   └── sw.js                           # Service worker (offline support)
├── CHANGELOG.md                        # Fix history (pre-v1.91)
├── ROADMAP.md                          # Planned features and deferred items
├── package.json                        # Node dependencies (Express, WS, etc.)
├── CLAUDE.md                           # This file
└── .gitignore

```

---

**Last Updated:** May 2026 (post-v1.118 — Solo remote admin panel: heartbeat + ntfy command channel; S24 freeze fix; Moto G update fix; home screen polish)
