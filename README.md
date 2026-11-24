# 🎬 Pocket Theater

Turn your old Android phone into a private, offline movie theater.

Pocket Theater runs a small server on your phone. Open it from your laptop
over Wi-Fi and browse the folders you choose: series, anime, movies, games, images,
PDFs and more. No internet, no accounts, no cloud.

## Features
- 📱 Runs as a background server on Android 12+
- 📂 Share only the folders you pick, read-only
- 🎞️ Video streaming with seeking, plus an image and PDF viewer
- 📊 Phone app with three tabs (Dashboard, Folders, Profile): Dashboard (open/close, link, wake time, users, data sent, requests, CPU, RAM, battery, traffic), Folders (shared folders by shelf, with counts, rename, move and stop sharing) and Profile (Admin tools, Settings)
- 🔐 Optional PIN lock for the phone app (Profile > Settings > Privacy and data), with a hide-in-recent-apps switch and searchable, categorised settings
- 🖥️ Optional admin website: open admin tools in a browser by scanning a QR code with the phone and entering the admin password
- 💬 Subtitles: put `.srt`, `.vtt`, `.ass` or `.ssa` files next to a video (for example `Movie.en.srt`) and they appear in the player's CC menu
- 🙈 Empty folders, and shelves with nothing in them, stay hidden on the website until something is added
- 🔏 HTTPS with a certificate the phone makes itself, plus a QR code to open the link on another device
- 🔒 Real accounts: people request access on the website, you approve them on the phone, and you can reset passwords there 
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

## Privacy and security

- [TERMS.md](TERMS.md): the rules for using Pocket Theater
- [PRIVACY.md](PRIVACY.md): what the app stores, what it can see and what leaves the phone
- [SECURITY.md](SECURITY.md): how to report a problem, what is protected and the known limits
