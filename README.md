# TajerTrack Phone

Native Android companion for sending the one SMS tracking link created for each new TajerTrack order, using the merchant's own SIM.

**This is not a Termux wrapper.** It is a Kotlin Android application that uses Android's `SmsManager` directly.

## Merchant experience

1. Install TajerTrack Phone from the APK download in TajerTrack.
2. Tap **ربط المتجر**. The app opens TajerTrack in the browser; the merchant signs in there and approves this phone. The app receives a short-lived, one-time pairing code through its deep link.
3. Allow SMS, SIM access, and notification permissions when Android asks.
4. Choose the sending SIM when the phone has more than one active SIM.
5. Tap **تفعيل إرسال الرسائل**. A permanent Android notification shows that the relay is active.

There is no Termux, command line, copied API token, environment file, or manual device ID.

## What it sends

- One tracking URL SMS when a new order is created and SMS tracking is enabled for that merchant.
- No repeated status-update SMS messages.
- It uses the selected SIM in the physical Android phone. A phone number typed into a settings field cannot change the sender; the active SIM and carrier determine the sender identity.
- Android's SMS submission callback is not a carrier delivery receipt. The app reports `submitted` only when Android reports that the send was accepted by its SMS stack.
- If the phone cannot determine whether a message was submitted, it reports `unknown`. The backend must not automatically resend an unknown job, to avoid duplicate messages.

## Important integration status

The legacy Termux gateway still uses one global token and one global worker. The native app does **not** call those endpoints.

The matching merchant-scoped backend implementation is in draft TajerTrack PR [#5](https://github.com/ni3yyn/dz-tracker/pull/5), on branch `feat/native-merchant-sms-gateway`. It includes one-time browser pairing, hashed per-phone credentials, merchant-scoped queue claiming/reporting, and admin phone inventory. The SQL migration `20261112_native_sms_gateway.sql` must be applied after the two existing SMS migrations before native pairing can work in production. The PR is not merged or deployed yet. Keep the legacy Termux gateway available during the transition: after a merchant pairs a native phone, the migration excludes that merchant from the legacy global claim function, while merchants who have not paired a native phone can continue using the old gateway. In-flight legacy jobs are marked `unknown` during the switch so they are never blindly resent.

## Build

Requirements:

- Android Studio with Android SDK Platform 36
- JDK 17
- Gradle 8.13 (the CI workflow installs this version)

Install JDK 17, Android SDK Platform 36, and Gradle 8.13. Open this folder in Android Studio and sync the Gradle project. To build from a terminal with Gradle installed:

```bash
gradle assembleDebug
```

To generate the standard Gradle wrapper for local development, run `gradle wrapper --gradle-version 8.13` once; commit the generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/` files to the new repository.

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs `assembleDebug` and `lintDebug` and stores the APK as a workflow artifact. It is a **debug build for development/testing**, not the signed release APK that should be published on TajerTrack. A production APK needs a stable release signing key stored in GitHub Actions secrets; never commit that key to this repository.

## Platform behaviour

- The relay runs as a user-started foreground service with a permanent notification and a partial CPU wake lock so the polling loop can continue with the screen off. Each idle poll uses one authenticated database RPC; the local delay backs off from 10 to 30 seconds. The user can stop it from the app.
- Keeping the relay active uses additional battery; a dedicated phone should remain on its charger where practical.
- The first version does not silently restart itself after a full phone reboot; the merchant opens the app and starts the relay again. Automatic reboot recovery will be added only after device/OEM testing.
- Android can restrict background execution or SMS use depending on the device and OS version. Sideloading avoids Play Store distribution review, but it does not remove Android's runtime permission prompts or foreground-service rules.
- The app does not read the SIM's phone number. Android/carrier APIs do not reliably expose it on every device. The selected SIM is what determines the outgoing line.

## Repository layout

```text
app/                 Native Android app
  src/main/          Kotlin, Compose UI, manifest and resources
.github/workflows/   Debug build and lint
 docs/                Backend API contract and merchant flow
```

## Publish as a new GitHub repository

After extracting the project folder, authenticate the GitHub CLI once (`gh auth login`) and run this inside the folder:

```bash
git init -b main
git add .
git commit -m "Initial TajerTrack Phone Android app"
gh repo create ni3yyn/tajertrack-phone --public --source=. --remote=origin --push
```

Use `--private` instead of `--public` to keep the source private.
