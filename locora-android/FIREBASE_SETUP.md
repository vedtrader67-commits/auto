# LOCORA Firebase setup

## One-time Firebase console setup

1. In **Authentication → Sign-in method**, enable **Email/Password**.
2. In **Realtime Database**, create a database. Choose a region close to both phones and copy the exact database URL.
3. In the Realtime Database **Rules** tab, paste the contents of `database.rules.json` from this folder and publish them.
4. The project must have the Android app registered with package `com.locora.app`. The matching `google-services.json` is already included in the Android app module.

Both phones install the same APK, create separate accounts, paste the same database URL, and use the one-time invite code.

## Included cloud paths

- Account authentication: Firebase Authentication email/password.
- Pair membership and one-use invite: `/pairInvites` and `/pairs/{pairId}/members`.
- Locations, consent, battery, presence, activity, messages, read receipts, typing, and Missing You events: under `/pairs/{pairId}`.
- Device tokens, readable only by their owning account: `/userTokens/{uid}`.

The Realtime Database rules deny access by default and restrict writes to the authenticated member's own records. Invite codes are random, expire after 24 hours, and are single-use. A pair is capped at two members.

## Media and notification constraints

Photo and voice attachments are compressed and stored as small Base64 values in Realtime Database to avoid Firebase Storage. This is for small personal messages, not large media libraries.

The app registers FCM device tokens and can display minimal push notifications. A trusted sender service is still needed to send FCM messages. Firebase's Android client SDK must never contain an Admin SDK key. Offline Missing You push delivery is not active until that server is deployed and configured.

Firebase Cloud Storage requires the Blaze billing plan for access to its buckets, including the default bucket. Do not enable it unless you accept pay-as-you-go billing. Firebase Realtime Database and Authentication offer no-cost quotas/features, subject to their published limits.

## Build

GitHub Actions builds the debug APK using `.github/workflows/locora-android.yml`.
