# Merchant manager and sender-phone installation

TajerTrack Phone separates merchant device management from SMS sending. The merchant's password is never shared with invited phone owners.

## Pair the first phone as the main device

1. Install TajerTrack Phone on the Android phone that will be the main merchant phone.
2. Open the app and choose **ربط المتجر**.
3. The app opens TajerTrack in the browser. Sign in with the merchant account. Administrator accounts cannot establish merchant pairing.
4. Choose the carrier of the SIM this phone will use for SMS and approve. If another main phone is already registered, the page requires explicit confirmation before replacing it.
5. The app exchanges a five-minute pairing code for two independent credentials: a manager credential and a sender credential for the chosen carrier. Both are encrypted locally using Android Keystore.
6. Grant Android's SMS, SIM-state, and notification permissions, select the active SIM, and start the relay.

The manager credential can only manage sender devices. It is not accepted by the SMS polling, dispatch, or reporting endpoints. The sender credential can send only for its assigned carrier.

## Invite another sender phone

1. On the main phone, open **إدارة أجهزة الإرسال**.
2. Choose the invited phone's carrier and select **إنشاء رابط الدعوة**.
3. Use **إرسال عبر SMS**, the Android share menu, **نسخ الرسالة الكاملة**, or copy either link separately. The prepared message includes the stable signed APK download and the invitation link.
4. The invited person installs TajerTrack Phone, opens the invitation on the intended phone, checks the store and carrier, and approves. No merchant login is required.
5. The recipient app exchanges the one-time code for a sender-only credential for that carrier. It cannot create invitations or manage devices.
6. Allow SMS, SIM-state, and notification permissions, select the matching active SIM, and start the relay.

An invitation is carrier-scoped, expires after 30 minutes, and can be redeemed once. A manager-created invitation becomes unusable if the manager credential is revoked or the merchant ownership changes. Pairing codes expire after five minutes.

## Manage sender devices

The main phone can refresh the list of sender lines and inspect their carrier, device name, and recent connection state. It can pause a line, resume it, or revoke it permanently. Revocation invalidates the credential and conservatively marks any in-flight SMS as unknown rather than automatically sending a duplicate.

Only one active sender phone is allowed for each merchant/carrier line. A phone can hold multiple sender profiles when it has more than one SIM. To add another network to the same physical phone, create a second carrier invitation and open it on that phone.

## Replace or recover the main phone

If the main phone is lost or replaced, open TajerTrack Phone on a new phone and start the normal **ربط المتجر** flow. Sign in with the merchant account and explicitly confirm replacing the previous main phone. This revokes the previous manager credential and its primary sender line. Invited sender phones remain independent and can be managed by the new main phone.

## SIM selection

- A single active SIM is selected automatically on a single-SIM phone.
- On a dual-SIM phone, either one or both SIMs can be enabled from the same selector. Enabling both does not cause alternation or duplicate messages; this phone supports up to two enabled SIMs.
- The service matches the customer number's operator to the sender-line credential and the active SIM carrier.
- If a matching SIM is missing or disabled, no other network is silently substituted. The job stays pending for the correct line. Invalid or unsupported customer numbers are marked skipped rather than routed to a different SIM.
- A phone may hold more than one sender-line credential if it has multiple SIMs. A line on another physical phone keeps its own independent credential.

## Operational expectations

The phone needs mobile network service for SMS and internet access to receive new order jobs. Keep TajerTrack Phone running as a foreground service; Android displays a persistent notification while the relay is active.

Stopping the relay pauses sending from that phone. The merchant can also pause or revoke the line from the main phone. Never assume a message was delivered solely because Android accepted it for sending.
