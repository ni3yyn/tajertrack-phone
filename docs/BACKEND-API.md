# TajerTrack Phone — backend contract

The native app uses only merchant-scoped mobile gateway APIs. It does not use `SMS_GATEWAY_DEVICE_TOKEN`, the old global `android-sms-gateway` worker identity, or the legacy claim/report endpoints.

## Routes

| Route | Authentication | Purpose |
|---|---|---|
| `POST /api/mobile/sms-gateway/invites` | Merchant browser session + same-origin request | Creates a 30-minute, single-use invitation for one operator slot. |
| `GET /mobile/sms-gateway/connect?invite=...` | High-entropy invitation token | Shows store identity and operator without requiring the invited phone owner to sign in. |
| `POST /mobile/sms-gateway/connect` (server action) | Single-use invite token | Redeems the invite and redirects to the app with a five-minute one-time pairing code. |
| `POST /api/mobile/sms-gateway/pair/complete` | One-time pairing code | Atomically consumes the code and returns a new per-device credential plus operator code. |
| `POST /api/mobile/sms-gateway/device/claim` | Device bearer token | Authenticates the line, updates heartbeat and claims only a job for its operator. |
| `POST /api/mobile/sms-gateway/device/report` | Device bearer token | Reports `submitted`, `failed`, or `unknown` for a job held by the device. |
| `POST /api/mobile/sms-gateway/device/manage` | Merchant browser session + same-origin request | Enables, disables, or revokes one sender line. |

The app never receives the merchant's Supabase password, browser cookie, long-lived user access token, or service-role key.

## Secure pairing

1. The merchant selects one of the fixed operator slots: `djezzy`, `mobilis`, or `ooredoo`.
2. The invite endpoint generates a cryptographically random 256-bit token, persists only its SHA-256 hash, and sets a 30-minute expiry.
3. The invited phone's page shows the store name, logo, and purpose. The owner explicitly approves. No merchant login is required on that device.
4. The server atomically consumes the invitation and inserts a five-minute pairing-code hash. The raw invite and code are never stored.
5. The Android app posts the code to `pair/complete`. The server atomically consumes it, creates a random device token, and stores only its hash.
6. The app stores each line token encrypted with a non-exportable Android Keystore key. One installation can hold more than one line credential.

There is one active native device per merchant/operator pair. Replacing Djezzy revokes only the old Djezzy device and preserves Mobilis and Ooredoo. The first carrier-specific pairing also retires the legacy unassigned device for that merchant. Disabled lines cannot be bypassed by creating a new invitation; they must be explicitly re-enabled or revoked.

## Carrier routing

The migration creates `sms_tracking_outbox.recipient_operator`, centrally derived by `public.detect_algerian_mobile_operator(text)`:

- `07xxxxxxxx` → `djezzy`
- `06xxxxxxxx` → `mobilis`
- `05xxxxxxxx` → `ooredoo`

The normalizer accepts local `0...`, `+213...`, `213...`, and `00213...` forms after removing presentation characters. Unknown or invalid numbers yield `NULL`; carrier-specific devices never claim those jobs.

The database creates one outbox job per order under the existing unique constraint. `poll_native_sms_gateway` authenticates the exact line token and claims only rows belonging to the same merchant with the matching `recipient_operator`. A legacy `operator_code IS NULL` device retains all-network compatibility only until it is replaced by the first carrier-specific line.

There is no automatic fallback to another operator. If the matching line is offline, paused, or unavailable, the job remains pending. One order still means one message. An unknown result is never automatically resent.

## Claim and report semantics

The app sends its version with each claim. The server performs token authentication, heartbeat, global and merchant pause checks, bounded leasing, and the carrier-filtered claim in one database RPC.

`submitted` means Android accepted the SMS send request; it is not proof of delivery. `failed` means Android explicitly rejected the send. `unknown` means the send result is ambiguous. Unknown jobs must never be automatically resent.

The server verifies each report against the merchant and exact device lease. Late duplicate reports must be idempotent and must not downgrade a submitted job.

## Merchant line controls

- `set_native_sms_gateway_device_enabled` pauses/resumes one device. Disabling a device with a processing job moves that job to `unknown`.
- `revoke_native_sms_gateway_device` invalidates the token and marks an in-flight job `unknown`.
- RLS is enabled on invite storage, with direct access denied to `anon` and `authenticated`; service-role access is used by server routes.
- Merchant ownership is resolved from the authenticated server session, never from a client-supplied merchant ID.

## Migrations and legacy coexistence

Apply these existing SMS migrations in order:

1. `20261112_android_sms_gateway.sql`
2. `20261112_sms_gateway_admin_manager.sql`
3. `20261112_native_sms_gateway.sql`
4. `20261116_sms_carrier_routing.sql`

The last migration replaces the one-active-phone-per-merchant index with one active line per carrier slot, annotates pending outbox jobs, creates invitation storage, and installs the carrier-scoped pairing, polling, enable/disable, and revoke functions.

The old Termux gateway remains available for merchants without a native phone. Once any native device is registered for a merchant, the legacy global worker excludes that merchant's jobs. Replacing or revoking a sender line converts only that merchant/line's in-flight sends to `unknown`; it never auto-retries an ambiguous send.
