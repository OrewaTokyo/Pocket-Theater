# Build and install

1. Upload `site.zip` to the repo root. The unpack workflow puts every file in its folder and starts the build.
2. The quick checks run first (folder layout, web helper tests). If one fails, nothing is built.
3. The build then checks the APK itself (app id, version, Android 12 minimum, permissions, signature) and publishes it as a release.
4. On the phone, open the repo's **Releases** page, download `PocketTheater-v….apk` and install it.
5. Open the **Folders** tab in Pocket Theater and tap **Add folder**. Pick a folder and choose what is in it (Series, Anime, Movies, Gaming and so on).
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

## Admin tools in a browser (optional)

1. On the phone open **Profile > Settings > Admin website**, set an admin password, then turn on **Allow the admin website**. It needs HTTPS and the theater open.
2. Sign in on the laptop's browser with your own account, then click **Admin sign-in** in the top bar. A QR code appears on the laptop's screen.
3. On the phone open **Profile > Scan admin QR** and point the camera at the code. The first time, Android asks to allow the camera. If the camera does not work, choose **Type code** and enter the code printed under the QR code.
4. The laptop says the code was scanned. Type the admin password on the phone.
5. The admin page opens on the laptop by itself. It closes after 30 minutes without use. **End admin** under Settings on the phone closes it at once.

You can also start from the phone: under **Devices on the website** tap **Show QR** next to the laptop, then scan it the same way.

## Subtitles

Put the subtitle file in the same folder as the video, with the same name: `Movie.mp4` and `Movie.srt`. For several languages add the language: `Movie.en.srt`, `Movie.fr.srt`, `Movie.en.forced.srt`. A `Subs` or `Subtitles` folder beside the video works too. Supported types are `.srt`, `.vtt`, `.ass` and `.ssa`. The phone turns them into the format browsers read.

In the player, use the CC button to pick one. The choice is remembered for the next video. Subtitles built into an `.mkv` file are not read by browsers, so export them to a `.srt` file with a tool such as MKVToolNix.
