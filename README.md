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

## Brutalist release channel

The Brutalist build is a separate update channel. Its Settings page records the `Master` release and commit that it was based on, as well as the exact build revision. It only checks GitHub releases whose tags follow this format:

```
v<major>.<minor>.<patch>-<MMDDyyHHmm>-brut
```

For example, release `v0.3.1-0721260230-brut` represents minor version `0.3.1` built on July 21, 2026 at 02:30. Before each release, update `appVersionName` and increment `appVersionCode` in `app/build.gradle.kts`, then create the identical Git tag. The release workflow verifies the match before publishing. Standard `v<major>.<minor>.<patch>` releases remain on the stable channel and are never offered to Brutalist builds.

## Offline behavior

Inventory reads and Clover operations require a connection. A count that was opened from live data can be stored in the local Room queue when connectivity drops. WorkManager replays it after reconnection. If another user counted the same item first, the queued entry pauses and requires an explicit submit-or-discard decision.
