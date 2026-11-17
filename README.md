# TajerTrack Phone

Native Android companion for sending the single tracking-link SMS created for each new TajerTrack order, using the merchant's own SIM cards.

**This is not a Termux wrapper.** It is a Kotlin Android application using Android's `SmsManager` directly.

## Merchant and family-device setup

1. Install TajerTrack Phone from the APK download in TajerTrack.
2. Open the app on the phone that will send from a SIM, then keep it ready for the pairing link.
3. In TajerTrack → Settings → SMS sender lines, choose Djezzy, Mobilis, or Ooredoo and create an invitation.
4. Open/share that one-time invitation on the intended phone. It displays the store and carrier purpose; the invited person approves without signing in as the merchant or receiving the merchant's password.
5. Allow SMS, SIM access, and notification permissions requested by Android.
6. Choose the primary SIM. On dual-SIM phones, choose the secondary SIM and explicitly enable it only when it should also be used.
7. Start the relay and keep the phone online.

The merchant can pair up to three sender lines, one per carrier. They can all be installed on one multi-SIM Android phone, or distributed across separate phones belonging to relatives. Each line receives its own credential and can be paused or revoked independently from TajerTrack Settings.

## Carrier routing and what gets sent

- Every eligible new order creates exactly one tracking SMS job.
- Algerian recipient prefixes are mapped centrally: 07 → Djezzy, 06 → Mobilis, 05 → Ooredoo.
- The backend assigns the job to the corresponding sender line. The Android app then selects an enabled, active SIM whose carrier matches that line.
- Enabling a second SIM does **not** rotate or duplicate messages. It simply makes that SIM eligible for customer numbers on its carrier. Disabling it means that handset uses only its primary SIM.
- If the matching sender line or matching SIM is unavailable, the job remains pending. There is no silent cross-network fallback. Invalid or unsupported destination numbers are explicitly marked `skipped` instead of remaining stuck in the queue.
- The app does not read or depend on the SIM phone number. Android/carrier APIs do not reliably expose the SIM's own MSISDN.
- Only one link SMS is sent when a new order is created. There are no repeated status-update SMS messages.
- Android's SMS submission callback is not a carrier delivery receipt. The app reports `submitted` only when Android accepts the send request.
- If the result cannot be determined, it reports `unknown`. The backend does not automatically resend an unknown job, avoiding duplicate messages.

Carrier prefix allocation is centralized in `detect_algerian_mobile_operator` so the mapping can be updated if numbering or portability rules change. The merchant's own tariff and SMS bundle determine actual cost; TajerTrack cannot inspect a SIM's package balance or promise that every message is free.

## Security and device management

- A merchant-created invitation has at least 256 bits of random entropy, is stored only as a SHA-256 hash, expires after 30 minutes, and can be redeemed once.
- Redemption creates a five-minute one-time pairing code. The phone receives a separate random device token, stored server-side only as a hash.
- Android encrypts each line token using a non-exportable Android Keystore key.
- A device never receives the merchant's password, browser cookie, Supabase access token, or service-role key.
- The backend permits one active device for each merchant/carrier slot. Replacing one carrier line does not revoke the other two.
- Merchants can pause, re-enable, or revoke each line independently. Revocation invalidates its token and converts an in-flight job to `unknown`.
- Invite, pairing, device and queue operations are merchant-scoped. The legacy Termux gateway remains isolated from merchants that have moved to native sender lines.

## Build and distribution

Requirements:

- Android Studio with Android SDK Platform 36
- JDK 17
- Gradle 8.13 (the CI workflow installs this version)

To build locally:

```bash
gradle assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the Android APK build and lint checks. Debug artifacts are for testing, not public distribution.

### Publish a merchant-facing release

Every push to `main` automatically starts a signed release. The workflow reads the latest stable GitHub Release and increments its patch version (for example, `v1.0.0` → `v1.0.1`), then builds and verifies the APK, generates a SHA-256 checksum, creates the GitHub Release, and attaches the files below. Releases are serialized so concurrent pushes cannot calculate the same next version; when several pushes arrive while a release is running, the newest pending push is the one retained. Pushing a `v*` tag still publishes that exact version. A manual **GitHub → Actions → Signed Android release → Run workflow** remains available for exceptional releases, and asks for an explicit version.

- `tajertrack-phone.apk`
- `SHA256SUMS.txt`

The dashboard uses this stable download URL (unless `TAJERTRACK_PHONE_APK_URL` overrides it):

`https://github.com/ni3yyn/tajertrack-phone/releases/latest/download/tajertrack-phone.apk`

Before the first release, configure these repository Actions secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded release keystore file.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Generate and back up the keystore securely, then keep using that exact keystore for every update. Never commit the keystore or put its passwords in the repository. Losing the signing key prevents existing installations from receiving normal in-place updates; users would need to uninstall and reinstall, losing local app data.

Do not publish the debug APK as the merchant download. It is signed with a different debug key and is intended only for testing.

## Platform behaviour

- The relay is a user-started foreground service with a persistent notification and partial CPU wake lock.
- It polls each locally eligible carrier line and backs off while idle. It can continue with the screen off while Android permits the foreground service.
- Keeping the relay active uses battery; a dedicated phone should remain on its charger where practical.
- It does not silently restart itself after a full phone reboot. Open the app and start it again.
- Android/OEM versions may restrict background execution, SMS, or notifications. Runtime permission prompts and the foreground-service notification remain under Android's control.

## Repository layout

```text
app/                 Native Android app
  src/main/          Kotlin, Compose UI, manifest and resources
.github/workflows/   Android build and lint
docs/                Backend API contract and merchant flow
```
