# Security

## Supported versions

Only the latest build from the `main` branch (see the repository's Releases) gets fixes. Please update before reporting.

## Reporting a vulnerability

Please report problems privately, not in a public issue.

1. Open the repository's **Security** tab and choose **Report a vulnerability**.
2. Say what you found, the steps to repeat it, the app version, and the phone model and Android version.

If you do not see that button, open a public issue that only asks for a private way to get in touch. Leave out all details of the problem. This is a personal project, so replies are best effort.

## What is protected

- **Accounts.** Nobody gets in until the admin approves them on the phone. Approving, turning off, deleting and password resets can only be done on the phone, never over the network.
- **Passwords.** Stored only as a salted PBKDF2-SHA256 hash (120,000 rounds). Sign-in sessions are random cookies (HttpOnly, SameSite=Strict) that last up to 14 days and end when the theater closes. Resetting a password, turning an account off, deleting it, or "Sign everyone out" ends the affected sessions at once.
- **Guessing.** After 8 wrong sign-in tries from one device, it must wait 10 minutes. Sign-up requests are limited per device, and at most 20 can be waiting.
- **Files.** Read-only. Only folders picked with Android's folder picker are reachable, and `..` paths are refused. File names are shown as plain text on the page.
- **App lock.** An optional 4 to 6 digit PIN, stored scrambled, with a growing wait after every 5 wrong tries. An option hides the app in recent apps and blocks screenshots.
- **Backups.** Android backup is off, so secrets are not copied to cloud backups.

More detail is in [docs/security/threat-model.md](docs/security/threat-model.md).

## Known limits

- **No HTTPS yet.** Traffic is plain HTTP. Someone on the same Wi-Fi or hotspot could read it, including a password as it is typed. Use a network you trust. HTTPS is planned.
- **The PIN is a door lock, not encryption.** It stops someone who picks up your phone. It does not protect against someone who copies the phone's storage.
- **The admin is whoever holds the unlocked phone.** Keep the PIN on and the phone locked.
- **Approved people see every shared folder.** There are no per-person permissions yet.
- **Password resets are handed over by you.** The phone shows the new password and you pass it on.

## Staying safe

- Give your hotspot a strong WPA2 or WPA3 password.
- Never forward port 8080 on your router to the internet. The theater is meant for your own network only.
- Approve only people you know, and turn off new sign-ups in Settings when you do not need them.
- Share only the folders people need.
- Turn on the PIN lock.

## Build and release

- APKs are built by GitHub Actions from this repository and published as releases. Only install APKs from your own repository's Releases.
- Every build is signed with one permanent key, so updates install over each other. The key is kept in the repository's secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`) and must never be committed.
- Before a build is published, a check confirms the APK's package name, version, permissions and that it is signed with that exact key.
- If the key ever leaks, make a new one. After that, each phone has to uninstall the app once.

## Out of scope

Attacks that need an unlocked phone, a rooted phone, or the admin's own approval. Weaknesses in Android itself. Problems that only exist on a network you do not control.
