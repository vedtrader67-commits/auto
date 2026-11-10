# LOCORA Android

Native Android project for a private two-person couple location and chat app.

## Project status
This branch contains the first native Android scaffold and a working foreground GPS collection service. The service caches the latest GPS coordinates locally and shows Android's persistent location-sharing notification.

## Features specified
- Two-person private account pairing
- Mutual live GPS map and last-known location
- Offline GPS caching and sync after internet returns
- Battery percentage and charging status
- Last five opened apps with each owner's Usage Access consent
- Real-time chat, photos, voice messages, delivery/read receipts
- Typing indicator, online/offline presence and last seen
- Local chat storage and TXT/JSON/PDF/ZIP export with media
- Per-phone 4-digit chat PIN, fingerprint unlock, one-minute auto-lock
- Offline-only "Missing You" minimal push notification with cooldown

## Not yet connected
The cloud-dependent features are not yet live. They need a Firebase project owned by the user:
- Firebase Authentication for the two accounts and private invite pairing
- Realtime Database or Firestore for location, battery, chat and presence
- Firebase Storage for media and chat archives
- Firebase Cloud Messaging for messages and Missing You notifications
- Google Maps SDK key for the map display

The Android project deliberately does not contain secret API keys or service-account credentials. Do not commit private credentials to a public repository.

## Build
GitHub Actions workflow: `.github/workflows/locora-android.yml`.
It builds `app-debug.apk` from this folder and uploads it as a workflow artifact.

## Android constraints
- GPS works without mobile data, but the other phone cannot receive fresh coordinates until the sender reconnects.
- Background location requires explicit runtime permission and an ongoing foreground-service notification. Android/OEM battery controls can still affect update frequency.
- Usage Access must be manually granted by each phone owner.
- Notifications require notification permission and internet access.
- Online presence can briefly be stale after abrupt network loss.
- The app must not conceal tracking or bypass either person's sharing controls.


Build workflow maintenance: SDK licence acceptance is handled in CI without treating the `yes` process's expected broken pipe as a build failure.
