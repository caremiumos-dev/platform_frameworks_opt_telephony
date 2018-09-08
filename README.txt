-- README for frameworks/opt/telephony --

This directory contains telephony libraries which function as the backing code
for APIs in TelephonyManager, SubscriptionManager and others.

These libraries run in the com.android.phone process and exist to handle voice
and data connections created by the user’s apps, or needed by the system. Many
APIs are plumbed down to the radio through HIDL calls defined in
hardware/interfaces/radio/<version>/IRadio.hal and return values that are sent
back up as responses.

We define several AIDL interfaces in
frameworks/base/telephony/java/com/android/internal/telephony/ which we
implement in this directory. This IPC scheme allows us to run public API code in
the calling process, while the telephony-related code runs in the privileged
com.android.phone process. Such implementations include PhoneInterfaceManager,
SubscriptionController and others.

The declaration of the com.android.phone process is in
packages/services/telephony and the top-level application class is PhoneApp,
which initializes everything else.

-- Testing --
Unit tests are found in frameworks/opt/telephony/tests and can be run on a
device connected throuh ADB with the command:

atest FrameworksTelephonyTests
