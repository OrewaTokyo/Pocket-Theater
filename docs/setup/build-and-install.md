# Build and install

1. Upload `site.zip` to the repo root. The unpack workflow puts every file in its folder and starts the build.
2. The quick checks run first (folder layout, web helper tests). If one fails, nothing is built.
3. The build then checks the APK itself (app id, version, Android 12 minimum, permissions, signature) and publishes it as a release.
4. On the phone, open the repo's **Releases** page, download `PocketTheater-v….apk` and install it.
5. Open Pocket Theater, tap **Add folder**, pick a folder, and choose its category.
6. Tap **Open theater**. Open the link shown on the phone from your laptop browser.
   Sign in with user `theater` and the password shown in the app.

The phone and laptop only need to share a Wi-Fi network (or the laptop joins the phone's hotspot). No internet is used.

## Updating
Every build is signed with a temporary key that GitHub creates fresh each time, so Android may refuse to install a new build over the old one.
If that happens, uninstall the old app first. You will need to add your folders again. A permanent signing key fixes this.
