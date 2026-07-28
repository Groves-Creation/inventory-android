# Inventory Android

Native Android 12+ client for the Inventory Convex backend. This build intentionally preserves the existing employee name/PIN session model and is suitable only for controlled internal testing.

## Configure

Add the development deployment URL to your user Gradle properties file so it is not committed:

```properties
INVENTORY_CONVEX_URL=https://your-deployment.convex.cloud
```

On Windows this file is normally `C:\Users\<you>\.gradle\gradle.properties`. The URL may also be supplied per build with `-PINVENTORY_CONVEX_URL=https://...convex.cloud`.

## Brother Print SDK

Label printing uses Brother's official Android SDK. The AAR is not on Maven Central — download **Brother Print SDK for Android** (v4.13.0 or newer) from [Brother's developer site](https://support.brother.com/g/s/es/dev/en/mobilesdk/android/index.html) and copy `BrotherPrintLibrary.aar` into `app/libs/`. The build fails early with that path if the file is missing.

For compile-only checks without the licensed AAR, pass `-PbrotherStub=true` (uses the `brother-stub/` sources). Real print jobs still need the official AAR.

## Build and test

```powershell
cd Inventory-Android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it only on managed pilot devices connected to the matching non-production Convex deployment.

## Purchase orders

**More → Purchase orders** stages a delivery so you can print every label for that order in one job. Lines can be existing catalogue items or new products. New products get a UPC reserved when the line is added, so labels can be printed before Clover knows about the item. Managers then use **Receive** to create those products in Clover (resumable if interrupted). Clerks can add existing items; only managers/owners can add new products or receive.

## Label printer (QL-810W)

**More → Label printer** stores the printer IP and loaded roll for the tablet. **Labels** and **Purchase orders** print directly over Wi-Fi; **Share PDF** remains as a fallback.

The QL-810W has **no Bluetooth** — only USB, Wi-Fi (2.4 GHz 802.11b/g/n), Wireless Direct, and AirPrint. Pairing attempts over Bluetooth will always fail on this model.

### Setup checklist

1. Put the printer on the same **2.4 GHz** network as the tablet (not a 5 GHz-only SSID, and not a guest network with client isolation).
2. In the app, open **More → Label printer**, allow nearby devices if prompted, then **Search the network**, or type the printer IP from its settings printout.
3. Set the model to **QL-810W** and match the loaded roll (default **DK-1221** 23×23 mm).
4. Use **Test connection**, then **Print a test label**.

### If it will not connect

| Symptom | What to check |
| --- | --- |
| Search finds nothing | Tablet and printer on the same LAN; Wi-Fi light solid on the printer; nearby-devices permission granted on Android 13+; try entering the IP manually |
| Timeout / no answer | 2.4 GHz vs 5 GHz; guest/client-isolation SSIDs; printer IP changed after DHCP — print the settings page and update the IP |
| Connection refused | Printer busy with another device, or wrong IP |
| Wrong roll / set label size | App roll setting must match the loaded DK cassette |
| Cover open / empty / jam | Physical printer status — the app surfaces these from the Brother SDK |

## Messages

Every user has an inbox, reachable from the mail icon in the top bar (the badge shows unread count, capped at 99+) or from **More → Inbox**. Any user can send a direct message to any other active user. Owners and managers can additionally broadcast — either to every active user, or scoped to the store they are working in, which delivers to the users with access to that store plus all owners. Senders do not receive their own broadcast. Read state and removal are per recipient, so clearing your copy leaves everyone else's intact.

Messaging requires a connection; it is not part of the offline count queue. Broadcast fan-out happens in one Convex transaction and is capped at 500 recipients.

## Offline behavior

Inventory reads and Clover operations require a connection. A count that was opened from live data can be stored in the local Room queue when connectivity drops. WorkManager replays it after reconnection. If another user counted the same item first, the queued entry pauses and requires an explicit submit-or-discard decision.
