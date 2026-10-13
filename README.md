<h1 align="center">Glimpse for Android</h1>

<p align="center">
  <b>Your Nextcloud photos, searchable by what is in them, in your pocket.</b><br>
  A native Android app for <a href="https://github.com/Infraviored/nextcloud-immich-search">Glimpse</a>,
  the Nextcloud app that finds photos by content and by the people in them, powered by your own
  <a href="https://immich.app">Immich</a>.
</p>

<p align="center">
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3ddc84?logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-6a3fd8?logo=kotlin&logoColor=white">
  <img alt="Needs Glimpse on your Nextcloud" src="https://img.shields.io/badge/needs-Glimpse%20on%20Nextcloud-0082c9?logo=nextcloud&logoColor=white">
  <a href="LICENSE"><img alt="License: AGPL-3.0-or-later" src="https://img.shields.io/badge/license-AGPL--3.0--or--later-2b7de9"></a>
</p>

<p align="center">
  <img src="docs/screenshots/app-timeline.png" width="24%" alt="Timeline with year jump">
  <img src="docs/screenshots/app-search.png" width="24%" alt="Search by content">
  <img src="docs/screenshots/app-people.png" width="24%" alt="People">
  <img src="docs/screenshots/app-viewer.png" width="24%" alt="Full-screen viewer with the people in the photo">
</p>

---

Type "beach at sunset" or a friend's name and get the picture, from the photos you already keep in
Nextcloud. There is no second cloud and no extra account: the app talks to your Nextcloud, and
Nextcloud decides what you may see.

## Features

- **Search by content.** The search lens sits at the bottom of every screen. Describe what is in
  the picture, narrow the results to photos, videos or a tag, and come back to recent searches.
- **Timeline.** All your pictures, newest first, with "On this day" memories. Jump to any year,
  keep scrolling in both directions, and go "Back to today".
- **People.** Everyone Immich recognised in pictures you can open. Find people by name and open
  their pictures.
- **Folders.** Your Nextcloud folders as galleries.
- **Viewer.**
  - zoom into photos and play videos full screen
  - see who is in a photo
  - jump to similar photos
- **Do things with many at once.** Select pictures, then:
  - share them to other apps
  - favorite them
  - download them to *Downloads*
  - delete them (they go to the Nextcloud trash bin)
- **Private stays private.** Folders you marked private in Glimpse stay hidden. You unlock them
  on the web.
- **Light and dark**, following the system setting, in Glimpse's design. English and German.

## Requirements

- **A Nextcloud with the [Glimpse app](https://github.com/Infraviored/nextcloud-immich-search)**
  installed and enabled for you, connected to an Immich server. The app checks this when you
  log in and tells you if Glimpse is missing.
- **Android 10** or newer.

## Install

1. Download the latest APK from
   [Releases](https://github.com/Infraviored/glimpse-android/releases) and open it on your phone.
   Android asks once whether your browser or file manager may install apps.
2. Open Glimpse and enter your Nextcloud address, e.g. `cloud.example.com`.

## How the login works

1. You enter your Nextcloud address.
2. Nextcloud's own login page opens in the browser
   ([Login Flow v2](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html)).
   Your password, two-factor authentication and single sign-on all stay with Nextcloud. **The app
   never sees your password.**
3. After you allow access, Nextcloud gives the app an **app password**. The app stores it
   encrypted, with a key from the Android keystore. You can see and revoke it in Nextcloud under
   *Settings → Security → Devices & sessions*.

*Log out* (tap your avatar) deletes the app password on the server and in the app.

## Build

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew testDebugUnitTest assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease                        # signed only with keystore.properties, see app/build.gradle.kts
```

- Debug builds install next to release builds as `com.infraviored.glimpse.debug`.
- Release builds are minified.
- The APK carries no Google dependency metadata, so it is suitable for F-Droid.

Stack:

- Kotlin and Jetpack Compose (Material 3)
- OkHttp
- Coil 3 with telephoto for zooming
- Media3 ExoPlayer

The app only uses Glimpse's JSON API (`/apps/glimpse/api/…`) and Nextcloud's login and WebDAV
endpoints. Thumbnails come through Glimpse, which checks permissions on the server.

## License

AGPL-3.0-or-later, like Glimpse itself. The Bricolage Grotesque font is under the SIL Open Font
License ([licenses/OFL-BricolageGrotesque.txt](licenses/OFL-BricolageGrotesque.txt)).
