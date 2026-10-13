# Glimpse for Android

The Android app for [Glimpse](https://github.com/Infraviored/nextcloud-immich-search), the Nextcloud
app that finds photos by what is in them (people, things, places), powered by Immich.

It shows Glimpse full screen, like an app of its own, and keeps you logged in.

**Needs:** a Nextcloud with the Glimpse app installed and enabled for you. Android 10 or newer.

## How the login works

1. You enter your Nextcloud address.
2. Nextcloud's own login page opens in the browser (Login Flow v2). Your password, two-factor
   authentication and single sign-on stay with Nextcloud; the app never sees your password.
3. After you allow access, Nextcloud hands the app an **app password**. It is stored encrypted
   with a key from the Android keystore. You find it in Nextcloud under
   *Settings → Security → Devices & sessions* as "Glimpse (Android)", where you can revoke it.

"Log out" in Nextcloud's user menu logs the app out and deletes the app password on the server.

## What the app adds to the web page

- Full screen, back button walks back through Glimpse, status bar in your Nextcloud's color.
- Downloads land in *Downloads*; "Download as ZIP" works too (the page hands the form to the app).
- Uploads through the file picker, full-screen videos.
- Links to other sites open in your browser.

## Build

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew testDebugUnitTest assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease                        # needs keystore.properties, see app/build.gradle.kts
```

## License

AGPL-3.0-or-later, like Glimpse itself.
