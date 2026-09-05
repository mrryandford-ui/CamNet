# Walkthrough - Connectivity Fixes

I have implemented several changes to improve the connectivity and reliability of the CamNet signaling server and its interaction with the Android WebView.

## Changes Made

### Server & Networking
- **[CamNetServer.kt](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/java/com/camnet/app/CamNetServer.kt)**:
    - Explicitly bound the Ktor engine to `127.0.0.1`.
    - Exempted `127.0.0.1`, `localhost`, and `::1` from rate limiting.
    - Increased the default rate limit to 20 attempts per minute.
- **[SslProxy.kt](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/java/com/camnet/app/SslProxy.kt)**:
    - Added `soTimeout` (30s) to both SSL and backend sockets.
    - Improved the TCP bridge thread management with explicit naming and flushing.
    - Ensured output streams are shut down correctly to signal EOF.
- **[network_security_config.xml](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/res/xml/network_security_config.xml)**:
    - Added `127.0.0.1` to the trusted domains list for cleartext and SSL pinning.

### UI & Bridge
- **[AndroidBridge.kt](file:///C:/Users/ZeroPoint/Projects/camnet/android/app/src/main/java/com/camnet/app/AndroidBridge.kt)**:
    - Verified and documented the polling logic for the local server.

## Verification Results

### Manual Verification
- Verified that the server starts and the Monitor UI loads successfully on the internal WebView.
- Confirmed that the rate limiter does not trigger for local loopback traffic.
- The SSL proxy now handles connections more robustly, reducing the chance of hung connections during signaling.
