# Privacy

Last updated: 21 November 2026

**Pocket Theater keeps everything on your phone.** There is no online account, no analytics, no ads and no tracking. The app sends nothing to the developer or to any online service.

## What the app stores on your phone

| What | Details |
| --- | --- |
| Accounts | Each person's username, whether they are approved or turned off, when they asked to join, and when they accepted the Terms of Use and Privacy Policy. Passwords are never stored. Only a salted, scrambled form (PBKDF2-SHA256) is kept, and it cannot be turned back into the password. |
| Admin password | If you turn on the admin website, a scrambled form of the password you choose, and a counter of wrong tries. |
| PIN lock | If you turn it on, a scrambled form of the PIN and a counter of wrong tries. |
| Shared folders | The folder's name, the shelf you put it under (Series, Anime, Movies, Gaming and so on), and Android's permission link to the folder. The app does not copy your files. |
| HTTPS certificate | A certificate and key the phone makes for itself. The private key stays in Android's key store. |
| Settings | The switches and choices in Profile > Settings. |
| Dashboard totals | Three running numbers: total data sent, total requests, and total time awake. They hold no file names, no usernames and no addresses. |

These live only in memory and disappear when the theater closes or the app stops: who is signed in (with each browser's type, such as "Chrome on Windows", and its address on your network, which the Devices list shows you), who was active in the last 3 minutes, the hourly traffic chart, and the counters that slow down password guessing (which use a device's address on your network).

## What the app can see

Only the folders you pick with Android's folder picker, and only to read them. The app never uploads, edits, renames or deletes your files.

## What other people can see

- Someone you approve can browse and play everything in the folders you share. There are no separate permissions per person, so approve only people you trust.
- As the admin, you see usernames and sign-up requests on the phone. You never see anyone's password. A reset gives them a new one.
- People signed in to the website cannot see each other.

## What leaves the phone

Files and pages are sent to the devices of approved people over your own Wi-Fi or hotspot. No internet service is involved, and the website loads no outside fonts, scripts or trackers. The connection is encrypted with HTTPS, using a certificate the phone makes itself, so browsers warn the first time. If you turn HTTPS off, or it cannot start, the connection is plain HTTP and the sign-in page says so. See [SECURITY.md](SECURITY.md).

## Permissions, in plain words

| Permission | Why |
| --- | --- |
| Internet | Android requires it for any app that opens a network port. It is used only to run the website on your local network. |
| Wi-Fi state and network state | To find the phone's address for the link on the Dashboard. |
| Wake lock | To keep the theater running while people are watching. |
| Foreground service | To stay open with a notification, so Android does not close it. |
| Notifications | For the "theater is open" notice and sign-up request alerts. You can turn the alerts off in Settings. |
| Ignore battery optimisation | Only to ask Android to leave the theater alone. Android shows its own question and you decide. |

## Problem reports

If the app closes unexpectedly, or hits an error it recovers from, it saves a short report on the phone: the error text, the phone model and the Android version. It holds no files, usernames or passwords. The next time you open the app it offers to copy the report so you can send it yourself. It is never sent anywhere automatically.

## The QR scanner

If you use the admin website, the phone scans a QR code with Google's code scanner, which is part of Google Play services. It runs on the phone, and Google's own terms and privacy notice apply to that component. If you type the code instead, nothing from Google is involved.

## Cookies

The website sets one cookie, `pt_session`. It keeps you signed in for up to 14 days, can't be read by page scripts (HttpOnly), is not sent from other sites (SameSite=Strict), and is only sent over HTTPS when HTTPS is on (Secure). There are no tracking or advertising cookies.

## Backups

Android backup is turned off for this app, so accounts, the PIN and settings are not copied into cloud backups.

## Your controls

- Delete an account, turn it off, or reset its password: Profile > Admin tools > Accounts.
- Sign everyone out, or reset the Dashboard totals: Profile > Settings.
- Stop sharing a folder: Folders tab > tap the folder > Stop sharing. You can also move it to another shelf or rename it there.
- Turn off the PIN lock: Profile > Settings > Privacy and data.
- Make a new HTTPS certificate: Profile > Settings > Connection.
- Turn off the admin website, change its password, or end an open admin session: Profile > Settings > Admin website.
- Remove everything: clear the app's data in Android settings, or uninstall the app. This also removes the PIN, the accounts and the folder list.

## Changes

If this changes, the new version replaces this file and the date above changes.

## Questions

Open an issue on the project's GitHub page for anything that is not a security problem. For security problems, follow [SECURITY.md](SECURITY.md).
