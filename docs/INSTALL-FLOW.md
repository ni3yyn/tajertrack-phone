# Merchant install and activation flow

The intended merchant journey is deliberately short:

1. Download and install the TajerTrack Phone APK from the official TajerTrack settings page.
2. Open the app and tap **ربط المتجر**.
3. Sign in to TajerTrack in the browser (only when the browser is not already signed in) and approve this phone.
4. Return to TajerTrack Phone. Allow the SMS, SIM access, and notification permissions requested by Android.
5. Choose the sending SIM if more than one SIM is active.
6. Tap **تفعيل إرسال الرسائل**.

The app must never ask the merchant to create an API token, paste a pairing secret, enter terminal commands, or install a second app.

The UI must explain that the phone needs mobile network service for SMS and internet access to receive new order jobs. It should never claim that an SMS was delivered to the customer solely because Android accepted the send request.
