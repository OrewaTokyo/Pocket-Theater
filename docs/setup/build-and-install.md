# Build and install

1. Upload `site.zip` to the repo root. The unpack workflow puts every file in its folder and starts the build.
2. The quick checks run first (folder layout, web helper tests). If one fails, nothing is built.
3. The build then checks the APK itself (app id, version, Android 12 minimum, permissions, signature) and publishes it as a release.
4. On the phone, open the repo's **Releases** page, download `PocketTheater-v….apk` and install it.
5. Open Pocket Theater. The first time, it asks you to agree to the Terms of use and the Privacy policy. Then tap **Add folder**. Pick a folder and choose what is in it (Series, Anime, Movies, Gaming and so on).
6. Tap **Open theater**. The phone shows the website link (https, port 8443). Open it from your laptop browser, or tap **QR code** and scan it with a phone or tablet.
   The first time, the browser warns that the connection is not private. That is because the phone made its own certificate. Open **Profile > Settings > Connection** (or the QR code window) to see the phone's fingerprint, compare it with the one in the browser's warning, then choose Advanced and continue.
7. Each person taps **Request access** on the sign-in page and picks a username and password. You get a notification on the phone.
8. In the app, under **Admin tools**, tap **Approve** on their request. After that they sign in with their own username and password.
9. Someone forgot their password? In **Admin tools > Accounts**, tap their name and choose **Reset password**. The phone shows a new password. Give it to them. They can change it from **Account** on the website.

The phone and laptop only need to share a Wi-Fi network (or the laptop joins the phone's hotspot). No internet is used.

## Updating
Every build is signed with the same permanent key, kept in the repo's secrets, so each new build installs over the old one and keeps your folders and settings.

Setup, once: in the repo open **Settings > Secrets and variables > Actions** and add two repository secrets:
- `ANDROID_KEYSTORE_BASE64` - the whole content of the keystore text file
- `ANDROID_KEYSTORE_PASSWORD` - the keystore password

Keep a copy of the keystore file and password somewhere safe. If they are lost, a new key means uninstalling the app once.

If you installed a build made before the permanent key existed, Android will refuse the first permanent-key build over it. Uninstall the old app once; after that, updates install over each other.
