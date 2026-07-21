# Inventory Android

Native Android 12+ client for the Inventory Convex backend. This build intentionally preserves the existing employee name/PIN session model and is suitable only for controlled internal testing.

## Configure

Add the development deployment URL to your user Gradle properties file so it is not committed:

```properties
INVENTORY_CONVEX_URL=https://your-deployment.convex.cloud
```

On Windows this file is normally `C:\Users\<you>\.gradle\gradle.properties`. The URL may also be supplied per build with `-PINVENTORY_CONVEX_URL=https://...convex.cloud`.

## Build and test

```powershell
cd Inventory-Android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it only on managed pilot devices connected to the matching non-production Convex deployment.

## Offline behavior

Inventory reads and Clover operations require a connection. A count that was opened from live data can be stored in the local Room queue when connectivity drops. WorkManager replays it after reconnection. If another user counted the same item first, the queued entry pauses and requires an explicit submit-or-discard decision.
