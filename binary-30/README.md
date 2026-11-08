# BINARY 30

Offline-first binary-options trade journal. Records are stored in this browser on this device only. There is no account, cloud sync, broker connection, or automatic trading.

## Included
- Individual stake and payout percentage for every trade.
- Win, loss, draw, and void results with net P/L calculation.
- Starting balance, configurable daily target, and 30-day progress.
- Trade history, analytics, win rate, payout breakdown, and balance chart.
- Changeable access-code lock.
- Encrypted JSON backup and restore for moving the journal to another device.
- Installable PWA with a service worker that caches the app after first launch.

## Getting started
Open the website in Chrome on Android. Create an access code, then add trades. Default settings are USD 10 starting balance, 20% daily target, and 30 days. Change them in Settings.

## Move to another phone
On the original device, open Settings & backup and export an encrypted backup. Enter the access code active at the time of export. On the new device, open the site and choose Restore from backup file on the first screen, then provide that same backup code. The backup contains the settings and the access-code lock.

## Storage and safety
Live records use local IndexedDB and are not encrypted at rest. The access-code screen is a privacy lock, not disk encryption. Exported backups are encrypted with AES-256-GCM using a PBKDF2-derived key. If browser site data is cleared, records can be lost; keep backups. The app is a record-keeping tool only, not financial advice, predictions, signals, or trade execution.
