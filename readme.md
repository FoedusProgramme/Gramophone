# Gramophone
![GitHub](https://img.shields.io/github/license/FoedusProgramme/Gramophone?style=flat-square&logoColor=white&labelColor=black&color=white)
![GitHub tag (with filter)](https://img.shields.io/github/v/tag/FoedusProgramme/Gramophone?style=flat-square&logoColor=white&labelColor=black&color=white)
[![Static Badge](https://img.shields.io/badge/Telegram-Content?style=flat-square&logo=telegram&logoColor=black&color=white)](https://t.me/AkaneDev)

[日本語](./readme_ja.md)

A sane music player built with media3 and material design library that is following android's standard strictly.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/org.akanework.gramophone/)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/org.akanework.gramophone)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=org.akanework.gramophone&utm_source=github.com&utm_campaign=readme)

## Features
- Up-to-date material 3 design
- Monet themed icon on Android 12+
- Dynamic player UI monet color
- View and play your favorite music
- Search your favourite music
- Uses MediaStore to quickly access music database
- Synced lyrics
- Read-only Playlist support

## Screenshots
| ![Screenshot 1](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_1.jpg) | ![Screenshot 2](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_2.jpg) | ![Screenshot 3](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_3.jpg) |
|------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| ![Screenshot 4](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_4.jpg) | ![Screenshot 5](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_5.jpg) | ![Screenshot 6](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_6.jpg) |
| ![Screenshot 7](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_7.jpg) | ![Screenshot 8](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_8.jpg) | ![Screenshot 9](https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/screenshot_9.jpg) |


## Installation
You can download the latest stable version of the app from [GitHub releases](https://github.com/FoedusProgramme/Gramophone/releases/latest), or from [F-Droid](https://f-droid.org/packages/org.akanework.gramophone/).

Beta versions and sneak peeks are available in the [telegram channel](https://t.me/FoedusProgramme) or [chat](https://t.me/FoedusDiscussion).

## Building
To build this app, you will need the latest beta version of [Android Studio](https://developer.android.com/studio) and a fast network.

### Submodules

Gramophone includes certain dependencies such as media3 as git submodule. Make sure you download git submodules by running `git submodule update --init --recursive` before trying to build Gramophone.

### Set up package type
Gramophone has a package type that indicates the source of the application package. Package type string is extracted from an external file named `package.properties`.

Simply open your favorite text editor, type `releaseType=SelfBuilt`, and save it in the root folder of the repository as `package.properties`.

After this, launch Android Studio and import your own signature. You should be able to build Gramophone now.

## License
This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](https://github.com/FoedusProgramme/Gramophone/blob/beta/LICENSE) file for details.

## Translation
<a href="https://hosted.weblate.org/engage/gramophone/">
<img src="https://hosted.weblate.org/widget/gramophone/strings-xml/horizontal-auto.svg" alt="Translation status" />
</a>

## Notice
- For bug reporting: [Telegram](https://t.me/FoedusDiscussion)
- GitHub/F-Droid/IzzyOnDroid certificate SHA-256 digest: f451197ad7b80bd1bc981ba38a2c49d471856fb38bcc333676d6e8f8f3ce5d6e
- Play Store certificate SHA-256 digest: 178869b0f9130d145b53404df4d4e5e311095406cb3c51a3e7a4b03bb3e87786

## FAQ

**Why can't I see songs shorter than 60 seconds?**
Gramophone hides songs shorter than 60 seconds by default. You can change it in _Three dots > Settings > Behaviour_ (set the setting to 0 to show all songs).

**I changed the min length setting, but some songs are still missing!**
Make sure you haven't excluded the folder in _Behaviour > Folder blacklist_.
Then, try to reboot your phone, then wait a few minutes (this will rescan the system-wide media database Gramophone uses to find songs).
If it's still not visible, your system version may not support the song: this most commonly is observed for .opus, which will only be found since Android 10.

**My song isn't playing! / My song is playing, but it's completely silent, yet the volume is turned up!**
Please note that Gramophone relys on system media codecs to make the app smaller. This means
- int32 (32-bit) FLAC files will only work on Android 14 or later
- FLAC files in general will only work on Android 8 or later
- ALAC files will most likely not work at all
- Dolby Digital (AC-3) / Dolby Digital Plus (E-AC-3) requires a device that has licensed decoders for these formats

## Friends
[SongSync](https://github.com/lambada10/songsync)
