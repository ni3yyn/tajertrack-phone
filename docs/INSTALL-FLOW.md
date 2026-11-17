# Merchant and family-device install flow

The intended experience keeps merchant credentials private while allowing another person to contribute a SIM.

## Pairing a sender line

1. Install TajerTrack Phone on the Android phone that owns the intended SIM.
2. In the merchant dashboard, open Settings → SMS sender lines.
3. Choose the carrier slot (Djezzy, Mobilis, or Ooredoo) and create its invitation.
4. Send the invitation link to the phone owner. It expires after 30 minutes and can be redeemed once.
5. On that phone, open the link, verify the store name and carrier, then approve.
6. TajerTrack redirects to the app with a five-minute one-time pairing code. The app exchanges it for a device-specific token and stores that token encrypted using Android Keystore.
7. Grant Android's SMS, SIM-state, and notification permissions.
8. On a single-SIM phone, the only active SIM is selected automatically. On a dual-SIM phone, use the same selector to enable either one or both SIMs; enabled SIM names remain visible when the selector is closed.
9. Start the relay.

The invited phone owner does not sign in as the merchant, and the merchant must never share their account password. The invitation is scoped to one operator slot.

## SIM selection

- A single active SIM is selected automatically on a single-SIM phone.
- On a dual-SIM phone, either one or both SIMs can be enabled from the same selector. Enabling both does not cause alternation or additional messages; this phone supports up to two enabled SIMs.
- The service matches the customer number's operator to the sender-line credential and the active SIM carrier.
- If a matching SIM is missing or disabled, no other network is silently substituted. The job stays pending for the correct line. Invalid or unsupported customer numbers are marked skipped rather than routed to a different SIM.
- A phone may hold more than one sender-line credential if it has multiple SIMs. A line on another physical phone keeps its own independent credential.

## Operational expectations

The phone needs mobile network service for SMS and internet access to receive new order jobs. Keep TajerTrack Phone running as a foreground service; Android displays a persistent notification while the relay is active.

Stopping the relay pauses this phone. The merchant can also disable or revoke the line from the dashboard. Never assume a message was delivered solely because Android accepted it for sending.
