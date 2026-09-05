# Fix Connectivity Issues in CamNet

The app is experiencing connectivity issues between the WebView and the embedded server, as well as between camera phones and the monitor. This plan addresses potential root causes in the Ktor server configuration, SSL proxy, and network security policies.

## User Review Required

> [!IMPORTANT]
> The changes include exempting `localhost` and `127.0.0.1` from rate limiting. This is safe as these are local connections within the device.

## Proposed Changes

### [Server & Networking]

#### [MODIFY] [CamNetServer.kt](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/java/com/camnet/app/CamNetServer.kt)
- Explicitly bind the Ktor engine to `127.0.0.1`. This ensures it's only reachable via the `SslProxy` (for remote devices) or directly via `localhost` (for the internal WebView), increasing security and avoiding issues with multiple network interfaces.
- Update `isRateLimited` to exempt `localhost` and `127.0.0.1`. This prevents the internal monitor and cameras connected via proxy from being accidentally blocked.
- Increase the default rate limit to 20 attempts per minute to be more permissive for legitimate multi-camera setups.

#### [MODIFY] [SslProxy.kt](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/java/com/camnet/app/SslProxy.kt)
- Improve the TCP bridge to be more resilient by using `try-with-resources` (via `use`) and ensuring both directions are properly joined.
- Ensure the `SSLContext` uses `TLSv1.2` or higher (via `TLS`) and explicitly set supported protocols if needed to ensure compatibility with modern WebViews.

#### [MODIFY] [network_security_config.xml](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/res/xml/network_security_config.xml)
- Add `127.0.0.1` to the `<domain-config>` for `localhost`. This ensures that if the app uses the IP instead of the hostname, the same security policy (cleartext allowed, self-signed cert trusted) applies.

### [UI & Bridge]

#### [MODIFY] [AndroidBridge.kt](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/java/com/camnet/app/AndroidBridge.kt)
- Update the server polling logic to try both `localhost` and `127.0.0.1` if one fails, or just prefer `127.0.0.1` for consistency.

## Verification Plan

### Automated Tests
- N/A (Unit tests for networking logic could be added but require complex mocking of Android/Ktor components).

### Manual Verification
- Start the app on a device and tap "Monitor". Verify that the server starts and the "Monitor" UI loads correctly.
- Open the "Camera" setup on another phone (or emulator) and connect to the Monitor's LAN IP. Verify that the camera joins the room and starts streaming.
- Rapidly disconnect and reconnect a camera to verify that the rate limiter does not block legitimate local traffic.
