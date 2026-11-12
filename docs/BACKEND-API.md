# TajerTrack Phone — backend contract

The native app intentionally does not use `SMS_GATEWAY_DEVICE_TOKEN`, `android-sms-gateway`, or the current global claim/report endpoints. Those endpoints represent the single Termux prototype and cannot provide merchant isolation.

The app expects these routes on the TajerTrack web origin. The corresponding implementation is currently in draft [TajerTrack PR #5](https://github.com/ni3yyn/dz-tracker/pull/5); the database migration must be applied after review and merge.

| Route | Authentication | Purpose |
|---|---|---|
| `GET /mobile/sms-gateway/connect?redirect_uri=tajertrack-sms%3A%2F%2Fpair` | Existing merchant browser session | Shows the store being connected and asks the merchant to approve this phone. After approval, redirects to `tajertrack-sms://pair?code=...`. |
| `POST /api/mobile/sms-gateway/pair/complete` | One-time pairing code | Atomically consumes the code and returns a new per-device credential. |
| `POST /api/mobile/sms-gateway/device/claim` | Device bearer token | Heartbeats the device and leases one pending SMS job belonging only to that device's merchant. |
| `POST /api/mobile/sms-gateway/device/report` | Device bearer token | Reports `submitted`, `failed`, or `unknown` for a job owned by that device. |

## Pairing

The browser route must use the merchant's existing Supabase cookie session and resolve the authenticated user to their merchant row. The native app must never receive the merchant's Supabase password, browser cookie, Supabase service-role key, or long-lived user access token.

On approval:

1. Generate a cryptographically random, single-use pairing code with at least 128 bits of entropy.
2. Store only a hash of the code, together with `merchant_id`, `created_by`, and an expiry no longer than five minutes.
3. Redirect to the exact allow-listed app scheme `tajertrack-sms://pair` with the code in the query string.
4. `pair/complete` must atomically consume the unexpired code. Reject reuse, expiry, unknown merchants, and malformed input.
5. Generate a separate high-entropy device token. Return it once to the app; store only its hash server-side. The app encrypts it with an Android Keystore key.
6. Register the device against that `merchant_id`. For the first release, allow one active sending phone per merchant. Re-pairing should revoke the previous phone before activating its replacement.

Suggested successful response:

```json
{
  "deviceId": "<device-uuid>",
  "deviceToken": "<one-time-returned-random-token>",
  "storeName": "اسم المتجر"
}
```

## Claim request and response

Request:

```json
{
  "appVersion": "0.1.0"
}
```

Successful response with no available job:

```json
{ "job": null }
```

Successful response with a job:

```json
{
  "job": {
    "id": "<outbox-uuid>",
    "phone": "+2135xxxxxxxx",
    "message": "TajerTrack - Suivez votre commande: https://tajertrack.vercel.app/t/<tracking-id>"
  }
}
```

The `poll_native_sms_gateway` transaction must:

- Authenticate the bearer token hash and resolve its device and merchant.
- Reject revoked devices and return an explicit paused state for disabled devices.
- Update heartbeat/app version, check the global gateway control and `merchants.sms_tracking_enabled`.
- Claim only rows where `sms_tracking_outbox.merchant_id` equals the authenticated device's `merchant_id`.
- Lock the job to the authenticated device ID and create a bounded lease.
- Preserve one job per order and never return another merchant's customer data.
- Keep the existing Algerian mobile-number validation and tracking URL construction.
- Perform the idle-poll checks in one RPC call rather than separate auth, heartbeat, control and queue calls.

## Report request

```json
{
  "jobId": "<outbox-uuid>",
  "status": "submitted",
  "error": null
}
```

The backend must verify the job is owned by this device's merchant and that the current lease belongs to this device. Preserve these result rules:

- `submitted`: Android accepted the SMS submission. Do not describe this as confirmed delivery.
- `failed`: Android explicitly rejected the send; use the existing bounded retry/dead-letter policy.
- `unknown`: the send outcome is ambiguous. Do not automatically resend.

Late duplicate reports must be idempotent and must never downgrade `submitted` to `unknown` or `failed`.

## Database changes required

Extend `sms_gateway_devices` with `merchant_id`, token hash, platform/app version, model label, and revocation metadata. Add a service-role-only pairing-code table. Add a unique partial index allowing one active device per merchant for the first release. Keep RLS enabled and grant no direct table access to `anon` or `authenticated`.

Add a tenant-scoped claim RPC (or replace the current one with a safely versioned signature) that takes the authenticated device and merchant IDs. Do not simply change the hard-coded worker ID while leaving the global queue selection intact.

The admin SMS manager should list all merchant devices, including connection heartbeat and enable/disable state. Existing primary Termux device controls must remain separate until the old agent is retired.


## Legacy coexistence

When a merchant pairs a native phone, the migration marks any processing job held by the legacy Termux worker as `unknown` before activating the new phone. The updated legacy claim RPC excludes all merchants with an unrevoked native device. Merchants without a native device continue using the old global gateway.
