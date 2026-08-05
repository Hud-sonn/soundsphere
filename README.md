<div align="center">

<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/icon.png" alt="Soundsphere app icon" width="200" />

# Soundsphere

### A YouTube Music client for Android

<br/>

[![Latest release](https://img.shields.io/github/v/release/Hud-sonn/soundsphere?style=for-the-badge&labelColor=0d1117)](https://github.com/Hud-sonn/soundsphere/releases)
[![License](https://img.shields.io/github/license/Hud-sonn/soundsphere?style=for-the-badge&labelColor=0d1117)](https://github.com/Hud-sonn/soundsphere/blob/main/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/Hud-sonn/soundsphere/total?style=for-the-badge&labelColor=0d1117)](https://github.com/Hud-sonn/soundsphere/releases)

<br/>

[**Download**](#download) · [**Features**](#features) · [**Account**](#account) · [**Build**](#build-from-source) · [**FAQ**](#faq)

</div>

> [!WARNING]
> **Regional Restriction** - If YouTube Music is unavailable in your region, this app will not work without a **VPN or proxy** connecting to a supported region.

---

<div align="center">

<h1><a id="about"></a>About</h1>

Soundsphere is an independent, open-source YouTube Music client for Android. It is a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist) with a rebranded identity, its own account system, and its own development direction. It follows Material 3 design guidelines closely and is built with Kotlin and Jetpack Compose.

</div>

---

<div align="center">

<h1><a id="screenshots"></a>Screenshots</h1>

<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/screenshots/screenshot_1.png" alt="Home screen" width="30%" />
<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/screenshots/screenshot_2.png" alt="Artist screen" width="30%" />
<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/screenshots/screenshot_3.png" alt="Recognize music screen" width="30%" />
<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/screenshots/screenshot_4.png" alt="Listen together screen" width="30%" />
<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/screenshots/screenshot_5.png" alt="Player screen" width="30%" />
<img src="https://github.com/Hud-sonn/soundsphere/blob/main/fastlane/metadata/android/en-US/images/screenshots/screenshot_6.png" alt="Player lyrics screen" width="30%" />

</div>

---

<div align="center">

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- Stream any song or video from YouTube Music
- Background playback
- Download & cache for offline use
- Skip silence
- Sleep timer

</td>
    <td width="50%" valign="top">

#### Audio
- Audio normalization
- Tempo & pitch control
- Equalizer

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Lyrics & Discovery
- Live synced lyrics
- AI-powered lyrics translation
- Personalized quick picks
- Search songs, albums, artists, videos, and playlists

</td>
    <td width="50%" valign="top">

#### Library & Account
- Full library management
- Local playlists
- Import playlists
- Reorder songs in playlist or queue
- Soundsphere account login
- Optional YouTube Music account login

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Social
- Listen together with friends in real-time

</td>
    <td width="50%" valign="top">

#### Interface
- Home screen widget
- Light / Dark / Black / Dynamic theme modes
- Dynamic color + 19 preset color palettes
- Built with Material 3

</td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="account"></a>Account</h1>

Soundsphere has its own account system:

- Register with an email address and verify it via a one-time code
- Log in, reset your password, and stay signed in with an encrypted local session
- Your Soundsphere account is completely separate from your YouTube Music login

The account backend lives in the [`backend-auth/`](backend-auth) directory of this repository.

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

<h2>Releases</h2>

Releases are published on the [GitHub Releases page](https://github.com/Hud-sonn/soundsphere/releases). Two variants are built for every release:

<table>
  <tr>
    <th align="center">Soundsphere (FOSS)</th>
    <th align="center">Soundsphere with Google Cast</th>
  </tr>
  <tr>
    <td align="center">
      `Soundsphere.apk` (universal) · `app-&lt;arch&gt;-release.apk`<br/>
      No proprietary Google dependencies.
    </td>
    <td align="center">
      `Soundsphere-with-Google-Cast.apk` (universal) · `app-&lt;arch&gt;-with-Google-Cast.apk`<br/>
      Includes Google Cast support.
    </td>
  </tr>
</table>

The in-app updater checks GitHub Releases, notifies you when a new version is out, and links to the APK matching your device's architecture and variant.

</div>

---

<div align="center">

<h1><a id="build"></a>Build from Source</h1>

<h3>Prerequisites</h3>

- JDK 17 or newer
- Android SDK

<h3>Debug build</h3>

```
./gradlew :app:assembleFossDebug
```

The APK is written to `app/build/outputs/apk/universalFoss/debug/`.

<h3>Release build</h3>

The release signing config reads the following environment variables: `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`, and expects the keystore at `app/keystore/release.keystore`.

```
./gradlew :app:assembleFossRelease
```

</div>

---

<div align="center">

<h1><a id="faq"></a>FAQ</h1>

<h3>Is this affiliated with YouTube or Google?</h3>
No. Soundsphere is an independent project and is not affiliated with, funded, authorized, endorsed by, or in any way associated with YouTube, Google LLC, or any of their affiliates and subsidiaries.

<h3>Do I need a Soundsphere account?</h3>
Yes. The app is gated behind the Soundsphere account system; you can register with any email address and verify it via a one-time code.

<h3>Does the app still work with my YouTube Music account?</h3>
Yes. Logging in with your YouTube Music account is supported in addition to the Soundsphere account and remains optional.

</div>

---

<div align="center">

<h1>Credits</h1>

Soundsphere is a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist) (GPL-3.0). We thank the Metrolist team and every contributor to the open-source libraries that power this project, including:

- [Better Lyrics](https://better-lyrics.boidu.dev) - time-synced lyrics with word-by-word highlighting
- [metroserver](https://github.com/MetrolistGroup/metroserver) - listen-together real-time backend
- [MusicRecognizer](https://github.com/aleksey-saenko/MusicRecognizer) - music recognition feature
- [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher) - YouTube cipher deobfuscation and PoToken generation

</div>

---

<div align="center">

<h1>License</h1>

This project is licensed under the [GPL-3.0 License](https://github.com/Hud-sonn/soundsphere/blob/main/LICENSE).

</div>
