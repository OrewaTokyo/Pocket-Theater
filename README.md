# 🎬 Pocket Theater

Turn your old Android phone into a private, offline movie theater.

Pocket Theater runs a small server on your phone. Open it from your laptop
over Wi-Fi and browse the folders you choose: movies, anime, games, images,
PDFs and more. No internet, no accounts, no cloud.

## Features
- 📱 Runs as a background server on Android 12+
- 📂 Share only the folders you pick, read-only
- 🎞️ Video streaming with seeking, plus an image and PDF viewer
- 🔒 Password login (HTTPS is planned)
- 📶 Works with just Wi-Fi or the phone's hotspot
- 🎟️ Movies, Anime, Games and Documents shelves with a ticket-stub look

## Project layout
| Folder | What lives there |
| --- | --- |
| `app/src/main/java/.../core` | Server, security and folder access |
| `app/src/main/java/.../service` | Background service and Wi-Fi locks |
| `app/src/main/java/.../ui` | Phone screens, components, theme |
| `app/src/main/assets/web` | The site your laptop sees (pages, components, styles, scripts) |
| `docs` | Setup and security notes |
| `.github/workflows` | APK build |

## Build
See [docs/setup/build-and-install.md](docs/setup/build-and-install.md).

## Status
Early development 🚧 (v0.1)

## License
Apache-2.0
