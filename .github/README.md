# BlockTube

Android TV video player with a manual channel blocker.
Unofficial fork of [yuliskov/SmartTube](https://github.com/yuliskov/SmartTube).

> **Not affiliated with, endorsed by, or connected to Google or YouTube.**
> All credit for the player and its engine goes to the SmartTube authors.

## What's different from SmartTube

- Blocked channels are matched by channel id (not by display name), so renamed channels stay blocked and channels with the same name don't collide.
- Autoplay never picks a video from a blocked channel.
- Blocking removes every card of the channel from the screen right away (Home, Search, Channel, player suggestions).
- **Settings → Content filter**: blocked channels, blocked keywords for searches (no results) and
  video titles (hidden), AI similar channels, 4-digit PIN for all Settings, update check.
- Keywords ignore case and accents and match whole words ("tro choi" blocks "Trò Chơi").
- Optional on-device AI (Android 7+): finds channels similar to the ones you blocked and blocks
  them automatically (or lets you review them). Model (~120 MB) is downloaded on first use.

Giới thiệu tiếng Việt: [docs/GIOI_THIEU.md](../docs/GIOI_THIEU.md)

Installs side by side with SmartTube (package `io.github.hoanghaisol.blocktube`).
Upstream code changes are listed in [PATCHES.md](../PATCHES.md).

## Install

Download the APK for your device from [Releases](https://github.com/hoanghaisol/BlockTube/releases)
(`arm64-v8a` for most modern TV boxes, `universal` if unsure) and sideload it.
Each release lists SHA-256 checksums. Updates are offered inside the app.

Release signing certificate SHA-256:
`34:E4:48:BC:59:FC:4B:5A:79:C2:44:61:D7:E6:16:30:B3:EC:BF:82:93:28:74:59:39:0B:CB:E7:2B:B7:1F:C1`

## Build

```bash
git clone --recursive https://github.com/hoanghaisol/BlockTube.git
cd BlockTube
./gradlew :smarttubetv:assembleStstableDebug
```

JDK 17. Release builds need a `keystore.properties` (never commit it).

## License

MIT, same as upstream. See [LICENSE](../LICENSE).
