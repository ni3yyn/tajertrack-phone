# TajerTrack Phone

Native Android companion for sending the single tracking-link SMS created for each new TajerTrack order, using the merchant's own SIM cards.

**This is not a Termux wrapper.** It is a Kotlin Android application using Android's `SmsManager` directly.

## Merchant and family-device setup

1. Install TajerTrack Phone on the first Android phone. Open it and choose **ربط المتجر**.
2. The app opens TajerTrack in the browser. Sign in with the merchant account, choose the carrier of the SIM this phone will use, and approve the pairing. This establishes the **main merchant device**.
3. Grant SMS, SIM access, and notification permissions. On a single-SIM phone, the only active SIM is selected automatically. On a dual-SIM phone, enable either one or both; enabled SIM names remain visible when the selector is closed.
4. On the main phone, open **إدارة أجهزة الإرسال**, choose Djezzy, Mobilis, or Ooredoo, and create an invitation.
5. Send the invitation from inside the app by SMS, share it through Telegram or another installed app, or copy the complete message. It includes the signed APK download link and the one-time invitation link.
6. The invited person installs TajerTrack Phone, opens the invitation on the intended phone, checks the store and carrier, and approves. They do not sign in as the merchant and never receive the merchant's password.
7. Start the relay on each phone that will send SMS and keep it online.

The main phone has a separate manager credential and its own carrier-scoped SMS sender credential. Invited phones receive sender-only credentials and cannot create invitations or manage other phones. Up to three carrier lines can be active per merchant; each line can be on the main multi-SIM phone or on a separate phone. The main phone can refresh the sender inventory, pause/resume a line, or revoke it.

## Carrier routing and what gets sent

- Every eligible new order creates exactly one tracking SMS job.
- Algerian recipient prefixes are mapped centrally: 07 → Djezzy, 06 → Mobilis, 05 → Ooredoo.
- The backend assigns the job to the corresponding sender line. The Android app then selects an enabled, active SIM whose carrier matches that line.
- Enabling both SIMs does **not** rotate or duplicate messages. It makes both networks eligible for their matching customer numbers. Only enabled SIMs can be used; this phone supports up to two enabled SIMs.
- If the matching sender line or matching SIM is unavailable, the job remains pending. There is no silent cross-network fallback. Invalid or unsupported destination numbers are explicitly marked `skipped` instead of remaining stuck in the queue.
- The app does not read or depend on the SIM phone number. Android/carrier APIs do not reliably expose the SIM's own MSISDN.
- Only one link SMS is sent when a new order is created. There are no repeated status-update SMS messages.
- Android's SMS submission callback is not a carrier delivery receipt. The app reports `submitted` only when Android accepts the send request.
- If the result cannot be determined, it reports `unknown`. The backend does not automatically resend an unknown job, avoiding duplicate messages.

Carrier prefix allocation is centralized in `detect_algerian_mobile_operator` so the mapping can be updated if numbering or portability rules change. The merchant's own tariff and SMS bundle determine actual cost; TajerTrack cannot inspect a SIM's package balance or promise that every message is free.

## Security and device management

- The authenticated merchant account establishes one active manager device. Replacing it requires a fresh account login and explicit confirmation; the previous manager credential is revoked.
- The manager device has a separate random credential used only for device management. It is never accepted by SMS sender endpoints.
- The main phone also receives one carrier-scoped SMS sender credential, so manager authority does not bypass carrier routing.
- Invited phones receive sender-only credentials. They cannot create invitations, inspect the merchant account, or manage other phones.
- A device invitation has at least 256 bits of random entropy, is stored only as a SHA-256 hash, expires after 30 minutes, and can be redeemed once. Manager-created invitations become invalid if the manager device is revoked or the merchant account ownership changes.
- Pairing codes are one-time and expire after five minutes. Each phone receives random credentials that are stored server-side only as SHA-256 hashes.
- Android encrypts each manager and sender token using a non-exportable Android Keystore key.
- A device never receives the merchant's password, browser cookie, Supabase access token, or service-role key.
- The backend permits one active sender device for each merchant/carrier slot. Replacing one carrier line does not revoke the other two.
- The main phone can inspect, pause, re-enable, or revoke sender lines. Revocation invalidates the token and converts an in-flight job to `unknown`.
- Account recovery is performed by pairing a new main phone through an authenticated merchant session, not by using an invitation link.
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
