# Security notes (v0.2)

Protected now:
- Everyone has their own account. A new person asks for access on the website, and nothing works for them until the admin approves the request in the phone app. Approving, turning accounts off, deleting them and resetting passwords can only be done on the phone, never over the network.
- Passwords are never stored. The phone keeps only a salted, scrambled form (PBKDF2-SHA256, 120,000 rounds) that cannot be turned back into the password.
- Signing in gives a random session cookie (HttpOnly, SameSite=Strict) that lasts 14 days. Closing the theater signs everyone out. Resetting a password, turning an account off or deleting it signs that person out at once.
- Guessing is slowed down: after 8 wrong tries from one device it must wait 10 minutes, and each wrong try is delayed. Sign-up requests are limited per device, and at most 20 can be waiting.
- Folders are read-only. The server never writes, deletes or uploads files. The only things it accepts are sign-up requests, sign in/out and a person changing their own password.
- Only folders picked in the app are reachable. The app never uses raw file paths, and `..` paths are rejected.
- File names are shown as plain text on the page, so a strange file name cannot run code.

Not protected yet:
- Traffic is plain HTTP, so someone on the same Wi-Fi could read it, including a password as it is typed. Use a network you trust until HTTPS is added.
- Anyone who can open the phone app is the admin. Turn on the PIN lock in Profile > Settings > Privacy and data (4 to 6 digits, stored scrambled, 30 seconds or more of waiting after every 5 wrong tries). It stops someone who picks up your phone. It does not encrypt the app's data, so it cannot stop someone who copies the phone's storage. The lock only covers the phone app. The website keeps running while the app is locked.

Planned: HTTPS with a certificate made on the phone, QR pairing.

See also [SECURITY.md](../../SECURITY.md) and [PRIVACY.md](../../PRIVACY.md).
