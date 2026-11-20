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
Every build is signed with the same permanent key, kept in the repo's secrets, so each new build installs over the old one and keeps your folders and settings.

Setup, once: in the repo open **Settings > Secrets and variables > Actions** and add two repository secrets:
- `ANDROID_KEYSTORE_BASE64` - the whole content of the keystore text file
- `ANDROID_KEYSTORE_PASSWORD` - the keystore password

Keep a copy of the keystore file and password somewhere safe. If they are lost, a new key means uninstalling the app once.

If you installed a build made before the permanent key existed, Android will refuse the first permanent-key build over it. Uninstall the old app once; after that, updates install over each other.
