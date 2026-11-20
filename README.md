# 🎬 Pocket Theater

Turn your old Android phone into a private, offline movie theater.

Pocket Theater runs a small server on your phone. Open it from your laptop
over Wi-Fi and browse the folders you choose: series, anime, movies, games, images,
PDFs and more. No internet, no accounts, no cloud.

## Features
- 📱 Runs as a background server on Android 12+
- 📂 Share only the folders you pick, read-only
- 🎞️ Video streaming with seeking, plus an image and PDF viewer
- 🔒 Real accounts: people request access on the website, you approve them on the phone, and you can reset passwords there (HTTPS is planned)
- 📶 Works with just Wi-Fi or the phone's hotspot
- 🎟️ Series, Anime, Movies, Gaming and Documents shelves, each with its own colour and a ticket-stub look

## Project layout
| Folder | What lives there |
| --- | --- |
| `app/src/main/java/.../core` | Server, accounts, sessions and folder access |
| `app/src/main/java/.../service` | Background service and Wi-Fi locks |
| `app/src/main/java/.../ui` | Phone screen (website link and admin tools), components, theme |
| `app/src/main/assets/web` | The site your laptop sees (pages, components, styles, scripts) |
| `docs` | Setup and security notes |
| `tests` | Quick checks that run before every build (layout, web helpers) |
| `tools/ci` | Script that verifies the finished APK |
| `.github/workflows` | APK build |

## Build
See [docs/setup/build-and-install.md](docs/setup/build-and-install.md).

## Status
Early development 🚧 (v0.1)

## License
Apache-2.0
