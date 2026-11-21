# Security notes (v0.2)

Protected now:
- Everyone has their own account. A new person asks for access on the website, and nothing works for them until the admin approves the request in the phone app. Approving, turning accounts off, deleting them and resetting passwords can only be done on the phone, never over the network.
- Passwords are never stored. The phone keeps only a salted, scrambled form (PBKDF2-SHA256, 120,000 rounds) that cannot be turned back into the password.
- Signing in gives a random session cookie (HttpOnly, SameSite=Strict) that lasts 14 days. Closing the theater signs everyone out. Resetting a password, turning an account off or deleting it signs that person out at once.
- Guessing is slowed down: after 8 wrong tries from one device it must wait 10 minutes, and each wrong try is delayed. Sign-up requests are limited per device, and at most 20 can be waiting.
- Traffic is encrypted with HTTPS (TLS 1.2/1.3). The certificate is made on the phone and its private key stays in Android's key store. The plain port only redirects, and session cookies are Secure.
- The admin website (off by default) can only be opened on a browser by the phone: pick the device on the phone, scan the QR code on its screen, enter the admin password on the phone. Nothing about this can be guessed or submitted over the network. It needs HTTPS, lasts 30 minutes without use (4 hours at most), can be ended from the phone, and cannot touch folders, settings, the PIN or the certificate.
- Folders are read-only. The server never writes, deletes or uploads files. The only things it accepts are sign-up requests, sign in/out and a person changing their own password.
- Only folders picked in the app are reachable. The app never uses raw file paths, and `..` paths are rejected.
- File names are shown as plain text on the page, so a strange file name cannot run code.

Not protected yet:
- The HTTPS certificate is self-signed, so a browser warns on the first visit and the warning only helps if you compare the fingerprint. Without that check, someone on the network could pretend to be the phone.
- If HTTPS is turned off or cannot start, traffic is plain HTTP and could be read by someone on the same Wi-Fi.
- While the admin website is open on a browser, whoever uses that browser has the admin tools. Pair only your own device.
- Anyone who can open the phone app is the admin. Turn on the PIN lock in Profile > Settings > Privacy and data (4 to 6 digits, stored scrambled, 30 seconds or more of waiting after every 5 wrong tries). It stops someone who picks up your phone. It does not encrypt the app's data, so it cannot stop someone who copies the phone's storage. The lock only covers the phone app. The website keeps running while the app is locked.

Planned: pairing that carries the certificate fingerprint to the other device, so the browser warning can be checked automatically.

See also [SECURITY.md](../../SECURITY.md) and [PRIVACY.md](../../PRIVACY.md).
