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
8. Choose the primary SIM. For a dual-SIM device, choose a secondary SIM and explicitly enable it when it should be eligible for its matching carrier.
9. Start the relay.

The invited phone owner does not sign in as the merchant, and the merchant must never share their account password. The invitation is scoped to one operator slot.

## SIM selection

- The primary SIM is eligible by default.
- The secondary SIM is disabled by default. Enabling it adds it to the eligible SIM set; it does not cause alternation or additional messages.
- The service matches the customer number's operator to the sender-line credential and the active SIM carrier.
- If a matching SIM is missing or disabled, no other network is silently substituted. The job stays pending for the correct line.
- A phone may hold more than one sender-line credential if it has multiple SIMs. A line on another physical phone keeps its own independent credential.

## Operational expectations

The phone needs mobile network service for SMS and internet access to receive new order jobs. Keep TajerTrack Phone running as a foreground service; Android displays a persistent notification while the relay is active.

Stopping the relay pauses this phone. The merchant can also disable or revoke the line from the dashboard. Never assume a message was delivered solely because Android accepted it for sending.
