# Security notes (v0.1)

Protected now:
- Every request needs the password (HTTP Basic). Failed logins are slowed down.
- Read-only: the server never writes, deletes or uploads.
- Only folders picked in the app are reachable. The app never uses raw file paths, and `..` paths are rejected.
- File names are shown as plain text on the page, so a strange file name cannot run code.

Not protected yet:
- Traffic is plain HTTP, so someone on the same Wi-Fi could read it. Use a network you trust until HTTPS is added.

Planned: HTTPS with a certificate made on the phone, QR pairing, a sign-in page that expires.
