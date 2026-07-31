# STATIC

A side-loadable Android internet radio app for stations you actually
curate yourself — no built-in directory, no accounts, no cloud sync.
Everything lives on your device.

Built as a snappier, more personal alternative to apps like Transistor:
add stations by pasting a stream URL or searching Radio Browser, tag them
with your own genre/mood/style vocabulary, place them on a world map, and
bookmark SoundCloud/Mixcloud DJ mixes alongside them.

## Features

- **Add stations** by manual stream URL, Radio Browser name search, or
  Radio Browser genre-tag search
- **List, grid, and map views** of your station collection, with
  favourites, popularity tiers, and live-broadcast time windows
- **User-defined genre/mood/style tags** — not scraped from unreliable
  station metadata
- **Saved Mixes**: bookmark SoundCloud/Mixcloud DJ sets with auto-fetched
  title/artist/artwork, tracklists, and share-to-app support
- **Playback**: Media3/ExoPlayer with software auto-gain (internet radio
  streams carry no loudness metadata), a configurable buffer, and a
  persistent now-playing bar with live ICY metadata
- **Sleep timer**, Android Auto support (browse and pick a station from
  the car screen), and opt-in **Chromecast** support
- **Full-fidelity backup/restore** for both stations and mixes (own zip
  format), plus one-way import from Transistor collection exports
- **Post-brutalist Material You** visual style — raw concrete tones, one
  accent color, hard keylines instead of soft shadows

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for full architecture,
data model, and build details.

## Changelog

### v1.3
- **Android Auto**: added a Random Station entry to the browse list, and
  Previous/Next/Shuffle transport buttons on the now-playing screen (also
  now show in the phone's own notification/lock screen)
- Station artwork (including manually uploaded/URL images, not just
  Radio Browser ones) now shows correctly in Android Auto's dual-app
  "now playing" card
- Fixed a rare but real bug where playback could go silent (needing a
  manual mute/unmute to recover) right as Android Auto connects — an
  ExoPlayer audio-focus-regain race during the car's audio device
  attaching
- Playback now actually stops when disconnecting from Android Auto
  (wired or wireless), instead of continuing through the phone speaker

### v1.2
- **Chromecast support**, off by default (Settings → Cast) — cast any
  station to a Chromecast or Cast-compatible speaker, with automatic
  fallback to local playback if the cast session drops
- Note: casting a live radio stream currently has a noticeable (~15s)
  delay before playback fully settles — this looks like an inherent
  limitation of casting raw, containerless radio streams to Cast
  receivers, not something fixable client-side

### v1.1
- **Android Auto**: full browsable station list from the car screen
  (previously play/pause of whatever was already loaded only) — tap
  through STATIC's media source to pick any saved station directly
- Favourited stations now sort to the top of the Android Auto list and
  show a ★ prefix
- Fixed station artwork not showing in Android Auto for manually
  uploaded images (Radio Browser–sourced images were unaffected) — the
  car's host process couldn't read this app's private local image
  storage directly, so artwork is now resolved and embedded in-app
  before being sent across

### v1.0
- First public release

## Installing

Grab the latest signed APK from the
[Releases](../../releases) page and side-load it — enable "Install
unknown apps" for whichever app you use to open the APK file.

## Building from source

Requires Android Studio (for the bundled JDK) and the Android SDK.

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Screenshots

<img width="570" height="1280" alt="screenshot 1" src="screenshots/screenshot-1.jpg" />

<img width="570" height="1280" alt="screenshot 2" src="screenshots/screenshot-2.jpg" />

<img width="570" height="1280" alt="screenshot 3" src="screenshots/screenshot-3.jpg" />

<img width="570" height="1280" alt="screenshot 4" src="screenshots/screenshot-4.jpg" />

<img width="570" height="1280" alt="screenshot 5" src="screenshots/screenshot-5.jpg" />

<img width="570" height="1280" alt="screenshot 6" src="screenshots/screenshot-6.jpg" />

## License

MIT — see [LICENSE](LICENSE).

## Support

If you find this useful, consider a [Ko-fi tip](https://ko-fi.com/W4T623HDPA) —
same link as the button in the app's Settings screen.
