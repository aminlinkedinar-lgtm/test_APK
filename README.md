# Ping Booster Pro (starter Android project)

A small Android network-diagnostics app. It measures repeated TCP connection latency to
Cloudflare (1.1.1.1:443), estimates variation between successful samples, counts failed
attempts, and times a DNS lookup.

## Build
1. Open this folder in Android Studio.
2. Allow Gradle sync and install the Android SDK platform 35 if prompted.
3. Connect an Android phone with USB debugging enabled, or use an emulator.
4. Press Run.

## Important limitations
This app is a diagnostic tool, not a VPN or traffic-routing service. Android apps cannot
reliably lower a game's ping just by running a script. The TCP measurements here are not
the same as the game's UDP ping and may differ from the game server's route. No credentials
or personal data are collected or transmitted by this sample.
