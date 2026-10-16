# QuickBuds

> Open-source HeyMelody alternative for Android, Windows and Linux. Control OnePlus / OPPO / realme
> earbuds over Bluetooth, no root: battery, noise cancellation (ANC), equalizer, hearing profile,
> gestures, wear detection, fit test, find my earbuds, dual connection, low latency mode, home screen
> widgets and a tray icon.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208%2B%20%7C%20Windows%20%7C%20Linux-3DDC84.svg)](#install)
[![Release](https://img.shields.io/github/v/release/spizganed/QuickBuds)](https://github.com/spizganed/QuickBuds/releases/latest)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?logo=kofi&logoColor=white)](https://ko-fi.com/spizganed)

# QuickBuds Glass

An enhanced version of the open-source **QuickBuds** Android application featuring a custom **Liquid Glass UI**, updated build workflows, and side-by-side installation support.

## Features
* **Liquid Glass UI**: Modern, sleek translucent design elements integrated into the app interface.
* **Side-by-Side Installation**: Modified package configuration allowing it to be installed alongside the original QuickBuds application.
* **Automated CI/CD**: Streamlined GitHub Actions workflows configured with JDK 21 for reliable building of debug and release APKs.

## Build Setup
The project uses Gradle and requires **JDK 21** for compilation[span_0](start_span)[span_0](end_span). 

To build the debug APK locally:
```bash
./gradlew assembleDebug
 hardware and tested on the device: the gesture values, the
equalizer writes, the hold's noise control cycle and more (see PROTOCOL.md).

The desktop app is built with [Slint](https://slint.dev) (used under the GPLv3) and
[tray-icon](https://github.com/tauri-apps/tray-icon).

The Pixel art style's font is [Doto](https://github.com/oliverlalan/Doto) (SIL Open Font License 1.1,
bundled with its license in `app/src/main/assets/Doto-OFL.txt`).
The Material style's widget font is [Google Sans Flex](https://github.com/googlefonts/googlesans-flex) (SIL Open
Font License 1.1, a medium and a semibold instance with Latin, Greek and Cyrillic, bundled with its license in
`app/src/main/assets/GoogleSansFlex-OFL.txt`).

## License

Copyright (C) 2026 spizganed

QuickBuds is free software under the GNU General Public License v3.0 or later, distributed without
any warranty; see [LICENSE](./LICENSE). The protocol sources above were used as documentation; check
each one's license before copying text from it.

QuickBuds is an independent project, not affiliated with, endorsed by or sponsored by OnePlus, OPPO,
realme, HeyTap or any of their affiliates. OnePlus, OPPO, realme, HeyMelody and the earbud model names
are trademarks of their respective owners and are used here only to say which devices the app works
with. The protocol was studied only to make these earbuds work with this app.
