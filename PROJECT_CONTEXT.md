# Static Radio — Internet Radio App — Project Context

Source of truth for this project. Read this before writing code — it reflects
the ACTUAL current state of the app (not the original plan), updated as of
2026-07-20 after a long multi-round session of UI/feature amendments. The app
builds, installs, and runs. Both a debug build and a signed release build are
verified working as of this update.

## Concept
Side-loadable Android internet radio app, user-curated stations only (manual
URL entry, Radio Browser name search, or Radio Browser genre-tag search),
plus a bookmarking feature for SoundCloud/Mixcloud DJ mixes. Solo dev,
weekend-project pace. Priority: snappy/responsive — explicit differentiator
from Transistor, which the user finds laggy. Local-only, no backend/account/
cloud sync (Chromecast was prototyped and then fully removed — see "Rejected
directions" below; the app does not touch Google Play Services at all).

## Build environment (read this first if you're picking this up cold)
- **No Android SDK/Gradle/JDK on PATH in the agent shell.** Use Android
  Studio's bundled JBR as JAVA_HOME:
  `C:\Program Files\Android\Android Studio\jbr`
- SDK lives at `C:\Users\ollie\AppData\Local\Android\Sdk` (already in
  `local.properties`, gitignored/machine-specific).
- Build via PowerShell (not Bash — Bash's POSIX path translation mangles
  Windows paths for this project):
  ```
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  cd "C:\Users\ollie\Documents\Static Internet Radio App\static-radio-starter"
  .\gradlew.bat :app:assembleDebug --console=plain
  ```
- Testing device: the user's physical phone over **wireless ADB**
  (`adb-4A170DLAQ002TD-hqwkpq._adb-tls-connect._tcp`). This connection drops
  frequently (screen sleep, wifi, ADB daemon restarts) — always run
  `adb devices` before install/logcat commands, and if empty, ask the user to
  reconnect rather than retrying blindly. Never send blind taps/screen input
  to this device — ask the user to interact with it and report back instead.
  `adb shell input keyevent KEYCODE_BACK` / `KEYCODE_WAKEUP` are fine
  (well-defined system actions) but arbitrary `input tap x y` is not.
- **Debug vs release signature mismatch is a recurring, expected event.**
  Debug and release builds use different signing keys but share one
  `applicationId`, so switching between them (or reinstalling a release build
  after testing debug, or vice versa) reliably produces
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Standing resolution used throughout
  this project: `adb uninstall com.staticradio.app` then reinstall the new
  APK. This **wipes all on-device app data** (Room DB + DataStore prefs) —
  it's happened many times this session and is treated as an accepted cost
  of iterating quickly, but always mention it to the user after doing it so
  they know to re-import their station/mix backups (Settings → Backup).
- Known recurring Kotlin gotcha: **Kotlin nests block comments** (unlike
  Java). Any KDoc/comment containing a literal `/*` substring causes
  "Unclosed comment" — always put a space between adjacent `*Word/*Word`
  tokens in comments.
- Another recurring compile gotcha: Material3's `ExposedDropdownMenu` is a
  member function of `ExposedDropdownMenuBoxScope`, not a top-level
  importable symbol — don't add an `import ...ExposedDropdownMenu` line, it
  won't resolve; just call it inside the `ExposedDropdownMenuBox` trailing
  lambda.
- If a build fails with a weird KSP/incremental-cache exception unrelated to
  your actual change (e.g. "Storage for [...] is already registered"), it's a
  stale cache from a previous failed build — `.\gradlew.bat --stop` then
  delete `app/build` then rebuild.
- Room's `AppDatabase` uses `fallbackToDestructiveMigration()` — there is no
  real migration path, so **every schema-changing version bump wipes all
  on-device data**. Currently **version 6** (added `liveTimesFrom`,
  `liveTimesTo`, `is24x7` to `StationEntity`). Warn the user before/after any
  change that bumps `@Database(version = ...)`.
- `AppDatabase` also has a `RoomDatabase.Callback.onCreate` that seeds the
  Genre/Mood/Style vocabularies with sensible defaults on a brand-new (or
  destructively-recreated) database — see "Data model" below for the actual
  seed lists. If you query the on-device SQLite file directly to debug
  something, remember Room runs in **WAL mode**: `adb shell run-as
  com.staticradio.app cat databases/static-radio.db` alone will NOT show
  recent writes — you need to also pull `static-radio.db-wal` and
  `static-radio.db-shm` alongside it (`adb exec-out run-as ... cat <file>`,
  not `adb shell ... cat <file> >` from PowerShell — the latter's `>`
  redirect re-encodes binary output as UTF-16 text and corrupts the file).

## Release signing
- Keystore: `C:\Users\ollie\Documents\Static Internet Radio App\keystore\static-radio-release.jks`
  — deliberately stored **outside** the `static-radio-starter` project folder
  so it can never be accidentally git-tracked. Alias `static-radio`, valid
  ~27 years.
- Credentials (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) live in `local.properties`
  (gitignored), read into `app/build.gradle.kts`'s `signingConfigs { create
  ("release") { ... } }` via a `Properties()` load — never hardcoded in
  source.
- **Both the `.jks` file and the passwords in `local.properties` need a real
  backup** (password manager, encrypted drive) — losing them means any future
  release build can never update the existing installed app again, only a
  fresh uninstall/reinstall.
- Build a signed release APK: `.\gradlew.bat :app:assembleRelease
  --console=plain` → output at `app/build/outputs/apk/release/app-release.apk`.
  Verify signing with `apksigner verify --verbose <path>` (not `jarsigner` —
  AGP signs with v2 by default, which `jarsigner` can't see, reporting a
  false "jar is unsigned"). Located at
  `C:\Users\ollie\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat`.
- `versionCode = 2`, `versionName = "1.0"` as of this update (bumped from the
  original `1` / `"0.1.0"` placeholder once the app reached a substantial,
  intentionally-versioned "1.0" feature set). Bump both again for the next
  meaningfully different release build.

## Architecture decisions
- Kotlin + Jetpack Compose, single-module app (`com.staticradio.app`)
- Media3 (ExoPlayer + MediaSession) for playback, in `RadioPlaybackService`
  (a `MediaSessionService`). UI talks to it via `RadioController` wrapping
  `MediaController` + custom SessionCommands (`PLAY_STATION`, `PLAY_RANDOM`,
  `SET_SLEEP_TIMER`, `CANCEL_SLEEP_TIMER`). ExoPlayer is built with:
  - a custom `DefaultRenderersFactory` overriding `buildAudioSink` to inject
    `AutoGainAudioProcessor` (real-time software AGC — no loudness metadata
    exists in internet radio streams, so it measures short-term RMS and
    slowly adapts gain toward a target level, smoothed to avoid audible
    pumping; toggled by the "Normalize volume" setting)
  - an `OkHttpDataSource.Factory` with a browser-like User-Agent +
    cross-protocol redirects (fixes some radiojar/icecast streams the
    default Media3 HTTP stack couldn't reach)
  - a `DefaultLoadControl` with a user-configurable min/max buffer
    (Settings → Playback → "Stream buffer", 5–120s, default 10s) — only the
    buffer depth scales with the setting, the playback/rebuffer-start
    thresholds stay at ExoPlayer's low live-stream-friendly defaults so a
    bigger buffer doesn't also mean a slower start
  - HLS support needs the separate `media3-exoplayer-hls` dependency
  - `android:networkSecurityConfig` allows cleartext (`http://`) traffic —
    plenty of legacy radio streams are still HTTP-only
- **Chromecast was added and then fully removed** in this session (see
  "Rejected directions"). If you ever revisit it: the working approach was
  `androidx.media3:media3-cast`'s `CastPlayer` swapped in/out of the
  `MediaSession` via `mediaSession.setPlayer(...)`, gated behind a Settings
  toggle so Play Services is never touched unless the user opts in. It was
  removed because `MediaRouteButton` crashed (`IllegalArgumentException:
  background can not be translucent`) when measured inside a transparent
  Compose `AndroidView` — fixable (give it an explicit opaque background) —
  but the user asked to drop the feature entirely rather than debug further.
- `PlaybackRepository` is the StateFlow bridge between service and UI
  (currentStationId, isPlaying, isBuffering, nowPlayingText, playbackError —
  surfaced in the player bar instead of silently swallowed,
  sleepTimerEndAtMillis).
- Room for local persistence (`AppDatabase`, **version 6**). Two DAOs:
  `StationDao` and `MixDao`.
- DataStore Preferences for app-level settings (`SettingsRepository`) — NOT
  Room, these are singleton app state, not per-station data. Current keys:
  theme mode, accent color, grid image shape, normalizeVolume,
  showBackgroundGrid + gridSpacingDp/gridLineWidthDp/gridOpacity (with a
  reset-to-default), bufferSeconds. (A `castEnabled` key existed briefly for
  Chromecast and was removed along with the feature.)
- No DI framework — manual `by lazy` singletons in `StaticRadioApp`
  (Application class).
- **Navigation** (`ui/nav/StaticApp.kt`): no bottom nav bar. A single shared
  `AppTopBar` composable (`ui/common/AppTopBar.kt`) is the sole navigation
  surface, reused across Home/Map/Mixes with a `TopBarMode` enum. Map and
  Mixes are regular pushed `NavHost` destinations, not tabs. `NavHost` has
  all four transition lambdas set to `EnterTransition.None`/
  `ExitTransition.None` — navigating anywhere is instant, no fade, by
  deliberate choice (matches the "snappy" priority). The Map screen shares
  `HomeViewModel` with Home via
  `navController.getBackStackEntry(Destination.Home.route)` (same
  `NavBackStackEntry`, so it's the literal same ViewModel instance) — this
  is what makes Map's filters/list-vs-grid selection stay in sync with Home
  instead of Map popping back to whatever Home's state happened to be
  before Map was opened.
- Networking: OkHttp + kotlinx.serialization for the Radio Browser API client
  (`data/remote/RadioBrowserApi.kt` — `searchStations(name)` and
  `searchStationsByTag(tag)`, both hitting `/json/stations/search` with
  different query params) and for Saved Mixes' oEmbed metadata fetch
  (`ui/mixes/MixUtils.kt`). Coil for image loading.
- Map: osmdroid, no Google Play Services. Tile source is CARTO Voyager
  (`https://{a,b,c,d}.basemaps.cartocdn.com/rastertiles/voyager/...`) — free,
  no API key, OSM data underneath, chosen because it leans more Latin/
  English in its place labels than stock Mapnik (not a hard guarantee for
  every non-Latin-script country — Wikimedia's old `osm-intl` tile set,
  which *did* guarantee English labels, now 403s on all external requests
  and is confirmed dead, not just flaky). Greyscale color filter in dark
  mode; in light mode it's duotoned between the theme's ink and concrete
  colors instead of plain desaturation, which read as a cold, out-of-place
  grey against the warm light palette (see `lightModeMapFilter()` in
  `MapScreen.kt`). Tile cache lives in `filesDir` (not `cacheDir`, so the OS
  doesn't reclaim it under storage pressure) with a raised size cap — this
  is what lets previously-viewed map areas keep working without a
  connection; areas never scrolled to still need one the first time. No true
  "download this whole region for offline use" flow exists.

## Data model

### Stations
Station fields use a source/override pattern for most fields (Radio Browser
API or the stream's ICY headers, always user-overwritable via Edit Station,
resolved value = override ?? source) — **except genre, which is always
user-defined only now** (see below). `ResolvedStation` (`data/
ResolvedStation.kt`) is the domain model the rest of the app reads —
UI/playback code should never touch the raw `*Source`/`*Override` pairs on
`StationEntity` directly.

| item | source | user overwritable |
|---|---|---|
| streamUrl | Radio Browser API (or user, for manual adds) | yes — direct field, no source/override split |
| name | Radio Browser API | yes |
| image | Radio Browser API, or user upload (copied to app-internal storage as `file://...`) | yes |
| countryCode | Radio Browser API | yes — searchable dropdown (`ui/common/LookupFields.kt`, `CountryCodeField`) backed by ISO 3166-1 |
| latitude/longitude | Radio Browser API, or user-picked on the Map screen | yes — **capped to 5 decimal places** everywhere (load, map-pick, save) |
| clickCountSnapshot | Radio Browser API, snapshotted once at add-time | no |
| popularityTier | derived: percentile-bucketed from clickCountSnapshot (❄️/🧊/😐/🔥/🌋), recomputed on add/delete — user can pick manually in Edit Station if there's no click count | conditionally |
| **genre** | **always user-defined** (no longer pulled from Radio Browser tags or ICY `icy-genre` — see "Rejected directions") | yes — comma-delimited multi-select "smart tags" via `GenreVocabularyField`, backed by `TagEntity`/`StationTagCrossRef`. Written straight into `genreOverride`; `genreSource`/the old ICY-genre DAO method are legacy/unused columns, not removed (would need a schema bump) |
| bitrate | stream URL (ICY `icy-br`), live | yes — DISPLAY-ONLY, never affects playback |
| description | user-entry-only (Media3 doesn't parse non-standard `icy-description`) | yes |
| nowPlayingCache | stream URL (ICY StreamTitle, live) | no |
| websiteUrl | user defined (or Radio Browser `homepage` at import) | yes — direct field |
| language | Radio Browser API | yes — searchable dropdown (`LanguageField`) backed by ISO 639-1 |
| isFavorite | user defined | yes — toggle, shown as a star bubble on the card (see "Features built") |
| **mood** | always user-defined | yes — single value from `TagType.MOOD`, shown as a bubble on the card |
| **style** | always user-defined | yes — single value from `TagType.STYLE`, filterable, **not shown on the card** (only mood is; style briefly was during this session but was reverted per explicit user correction) |
| **liveTimesFrom / liveTimesTo** | always user-defined | yes — `HH:mm` 24-hour strings, validated with a regex requiring leading zeros (`23:59`, not `1:5`). `is24x7` overrides both when true. Shown on list-view cards as part of the subtitle line: `countrycode - bitrate - livetimes`. Edit Station also shows a best-effort "your local time equivalent" derived from `CountryTimeZones.kt` (a small hand-curated ISO-country → representative-IANA-zone map, ~45 countries — not exact for multi-zone countries like the US/Russia/Brazil) |

Genre/Mood/Style vocabularies are managed under Settings → Vocabularies.
**Seeded with defaults on first run** (via `AppDatabase`'s `onCreate`
callback, not a runtime check — see Build environment above): Genre = pop,
electronic, techno, dub, ambient, breakbeat, UK garage, jungle. Mood =
energetic, heavy, crowd safe, calm, educational. Style = DJ set, Radio show,
Talk Show.

### Saved Mixes (`ui/mixes/`)
Bookmarks for SoundCloud/Mixcloud DJ mixes — everything user-defined.
`MixEntity` + child `MixTrackEntity` (tracklist) via `MixDao`.

Fields: url, fullTitle, artist, mixTitle, sourceRadio, **genre** (comma-
delimited multi-select via `GenreVocabularyField`, same as stations — was a
single-select bug earlier this session, fixed, including the genre *filter*
which also only matched exact single values before), mood (shown as a
bubble on the card), style (filterable, not shown on the card), image,
**releasedDate** (now validated `DD/MM/YYYY` format, e.g. `01/01/2020` —
error shown under the release-date field itself, not the URL field),
sourceStreamingSite (`MixSource` enum, auto-detected from URL),
isFavorite (shown as a star bubble, same treatment as stations),
description, dateAddedEpochMillis, tracklist.

- **List screen** (`MixesScreen.kt`): list-only (no grid), vertical-lines-
  only background. Empty state (no mixes yet) sits just under the top bar
  with a readable background card, mentions the SoundCloud/Mixcloud
  share-to-prefill flow.
- **Source logos**: real brand PNGs at `res/drawable/soundcloud_logo.png`
  and `mixcloud_logo.png` via `ui/mixes/SourceLogos.kt` — do NOT revert to
  hand-drawn Canvas icons.
- **Add/Edit**: `MixFormScreen.kt` + `MixFormViewModel` (mixId == null → add
  mode). Genre/Mood/Style fields now have brief helper text explaining what
  each means. Tracklist is a repeatable row editor.
- **Tap to open**: `openMixExternally()` fires a plain `ACTION_VIEW` intent —
  Android already routes to the installed SoundCloud/Mixcloud app if present.
- **Share-intent capture**: `MainActivity` registers an `ACTION_SEND
  text/plain` intent-filter, routes the shared URL into `MixFormScreen` as
  `prefillUrl`.
- **oEmbed prepopulation**: `fetchOEmbed()` hits the official SoundCloud/
  Mixcloud oEmbed endpoints (ToS-compliant). Only prefills title/artist/
  thumbnail.
- **Backup**: `data/backup/MixBackupManager.kt` — own zip format (`mixes.json`
  + `images/<id>.jpg`).

## Backup format (station side rewritten this session)
Station export used to be strict Transistor-zip-compatible only, which meant
genre/coordinates/language/description/mood/style/popularity/live-times
didn't round-trip. It's now **STATIC's own full-fidelity zip format**
(`data/backup/BackupManager.kt`, `stations.json` + `images/<id>.jpg`) that
backs up every field on `StationEntity` plus the full Genre/Mood/Style
vocabularies (including entries not currently attached to any station).
Import still auto-detects and accepts a **Transistor** `collection.json` zip
for one-way interop (Transistor's format simply doesn't have the extra
fields, so those stay empty on that import path) — kept via
`TransistorBackup.kt`'s existing `TransistorStation`/`TransistorCollection`
models. Saved Mixes' backup format is unrelated/unchanged.

## Playback details worth knowing
- `RadioPlaybackService` captures `IcyInfo` (dynamic now-playing StreamTitle)
  and `IcyHeaders` (static bitrate only now — genre is no longer read from
  ICY, see Rejected directions) via the same `onMetadata` callback. `IcyInfo`
  updates also push into the *system notification's* MediaMetadata (artist
  field) via `player.replaceMediaItem` — metadata-only, doesn't interrupt
  playback.
- `StationLookupImpl` adapts `StationDao` to the service's `StationLookup`
  interface — random-station lookup, now-playing cache writes, live bitrate
  writes. (It used to also do live genre writes; removed.)
- Sleep timer: `startSleepTimer`/`cancelSleepTimer` on `RadioController`, a
  cancellable coroutine in the service's `serviceScope`. Settings → Sleep
  timer, live MM:SS countdown. The hour/minute +/- steppers now reserve a
  fixed-width number column so a 1-digit hour value and a 2-digit minute
  value don't make the two rows' button spacing look inconsistent.
- Android Auto: the app registers as Auto-compatible
  (`res/xml/automotive_app_desc.xml` + the manifest meta-data pointing to
  it), so basic play/pause of whatever's already playing should surface
  there via the standard `MediaSessionService` integration. **Full
  browsing/picking a station from the car screen is NOT implemented** — that
  would need converting `RadioPlaybackService` to a `MediaLibraryService`
  with a real browse tree, scoped out as a bigger follow-up, never attempted.

## Features built
- **Home**: top bar is a 3-way segmented button (List/Grid/Map) + Filter +
  Mixes (hand-drawn cassette icon) on the left, Add (accent-colored) +
  Settings on the right. Empty state (no stations yet) sits under the top
  bar with a readable background card explaining how to add one.
- **Station "ID plate" cards** (`ui/home/StationCard.kt`, shared by stations
  *and* mixes via `StationPlate`): keyline border, rivet dots at the top
  corners, and now four kinds of bubble, all straddling the card edge
  half-in/half-out:
  - **favourite** (yellow star) — top-left corner, mirrors click-count
  - **click-count/popularity emoji** — top-right corner (stations only, not
    mixes — mixes have no click-count concept)
  - **mood** — sits left of the click-count bubble in that same top-right
    cluster (⚠️ NOT style — style was tried there briefly this session and
    explicitly reverted back to mood per the user)
  - **genre(s)** — a row along the *bottom* edge, list view only (grid tiles
    stay bubble-free below the top corners to keep the compact grid
    aesthetic — grid tiles only ever show favourite + click-count, no mood,
    no genre row), capped at 3 visible chips with a trailing "…" if there
    are more, rather than wrapping/overflowing past where the card's corner
    radius begins
  - Bubble inward-offset from the card's true edge is a fixed
    `BUBBLE_EDGE_INSET` (currently 16dp), **deliberately decoupled** from
    `horizontalInset` (which only controls the visible card's width) — an
    earlier version coupled them and every width change silently dragged
    the bubble positioning around with it; keep them independent.
  - `horizontalInset` defaults to 6dp for both list/mix rows and grid tiles
    now (list/mix cards used to use a wider 16dp inset for bubble clearance,
    which visibly narrowed them compared to grid tiles — the user asked for
    list/mix card width to match grid's, so the inset was unified and bubble
    clearance handled separately via `BUBBLE_EDGE_INSET` instead).
  - Homepage "globe" button sits on the right side of each **list-view**
    station row (not grid, not the player bar — it used to be in the player
    bar, moved here).
- **Filtering**: Filter dialog with Favourites/Genre/Country/Mood/Style
  (Mixes: same minus Country). Map screen shares Home's filter state (see
  Architecture).
- **Add Station**: Search tab first/default (more commonly used than manual
  entry), with **two** search bars — by station name (original) and by
  Radio Browser community genre tag (new, `searchStationsByTag`) — both
  fire automatically 400ms after you stop typing, plus an explicit
  accent-colored Search button per bar. Manual URL entry is the second tab.
- **Edit Station**: full field table above. Genre/Mood/Style fields have
  brief helper text. Live 24/7 toggle + from/to time fields (hidden when
  24/7 is on) with local-time-equivalent hint. Image upload via Android
  photo picker; "Pick on Map" button. No more "Source: X" / "User Defined"
  subheader text under fields — removed entirely per user request.
- **Map**: CARTO Voyager tiles (see Architecture), theme-aware color filter,
  circular accent-colored zoom buttons, teardrop pins. Filters now actually
  work here (previously a known-broken no-op). Doubles as a coordinate-
  picker (`pickMode`) for Edit Station.
- **Settings**: regrouped into Android-style categories — Appearance
  (theme, accent, artwork shape *dropdown* not 3 buttons, background grid
  toggle + spacing/line-width/opacity sliders hidden behind a Show/Hide
  toggle since users kept accidentally bumping them), Playback (normalize
  volume + explanation text, stream buffer size + explanation, ), Sleep
  timer, Vocabularies (Genre/Mood/Style management links), Backup (station +
  mixes, each with its own title/description). Below all categories: a
  Ko-fi support button (native Compose button styled to match the provided
  Ko-fi widget's label/color — can't embed the actual JS widget in a native
  app — links out to ko-fi.com) and the build number (`Build
  ${BuildConfig.VERSION_NAME}`, so `buildFeatures.buildConfig = true` had to
  be added to `build.gradle.kts`). Dark mode/Follow system switches have
  Material3's default 48dp minimum-touch-target padding suppressed
  (`LocalMinimumInteractiveComponentEnforcement`) since it was making that
  specific pair of rows look more spaced out than the rest of the page.
- **Persistent player bar**: floats as an overlay above scrollable content.
  The "now playing" text line is now **always rendered** (previously only
  while `isPlaying`), so the bar's height stays constant across play/pause
  instead of visibly growing the moment playback starts.
- **Typography**: single-typeface system now — **IBM Plex Mono everywhere**
  (was a 3-typeface system: Big Shoulders display / Inter body / IBM Plex
  Mono metadata). Changed because the user liked the "now playing" text's
  font and wanted it used app-wide; `BigShoulders`/`Inter` FontFamily
  definitions were removed from `Type.kt`, weight/size/letterSpacing per
  role kept for hierarchy.
- **Line weight**: every border/divider/keyline in the app is now a uniform
  **1.0dp**, matching the top-bar icon buttons' original weight (was a mix
  of 1.3dp/1.7dp/2dp depending on element — normalized down in two passes,
  first a proportional -20%, then explicitly unified to exactly match the
  button weight per user follow-up).
- **App icon**: replaced with a user-supplied neon-yellow radio glyph on a
  dark rounded-square background (adaptive icon, `res/drawable/
  ic_launcher_foreground.png` + `ic_launcher_background.xml`; minSdk 26 means
  no legacy per-density mipmaps are needed, adaptive icon alone covers every
  supported OS version). Glyph was scaled down ~25% within its canvas
  partway through per user follow-up (still the same source file, just
  redrawn smaller/centered — see `ic_launcher_foreground.png`'s generation
  if you need to redo it).

## Design direction (validated with user via HTML mockup)
Deliberate tension: Material You STRUCTURE (rounded surfaces, segmented
controls, elevation logic) combined with a POST-BRUTALIST PALETTE (raw
concrete tones, one accent color, hard black keylines instead of soft
shadows) — explicitly NOT default Material You pastel dynamic color.

Token reference (light mode shown, dark mode swaps concrete/ink):
- Background: #E7E2D6 (concrete) / #17171A (dark)
- Surface: #F1EDE2 (light) / **#232326 (dark — changed this session)**. The
  old dark surface (#1D1C18) had a warm/brown undertone that read as an odd
  off-color against the cooler background; it's now a plain neutral lift off
  the background with no tint. This fixed every `MaterialTheme.colorScheme.
  surface` usage app-wide (Settings section boxes, filter dialogs, etc.) in
  one place since they all read the same theme token.
- Accent (user-selectable in Settings): #FF4713 "rebar" (default) /
  #3D6BFF "signal blue" / #CFEE2E "hazard lime"
- Border/keyline: #141412 (light) / #E7E2D6 (dark) — hard border, never soft
  box-shadow blur. **Uniform 1.0dp everywhere** (see "Features built").
- Type: **IBM Plex Mono, single typeface, everywhere** (see "Features
  built" — this replaced the old 3-typeface Big Shoulders/Inter/IBM Plex
  Mono system).
- Background grid: faint square grid (vertical-only for Mixes), now
  user-adjustable (spacing/line-width/opacity, Settings → Appearance,
  behind a Show/Hide toggle) and its first line is inset ~10dp from the
  screen edge instead of sitting flush on it (was previously unadjustable
  and looked like a stray line rather than a grid).

Reference mockup: `mockup.html` in this repo — historical reference only;
where this doc and the live app disagree with the mockup, the app and this
doc win. There have been many deliberate departures since the mockup, and
several more within this session alone (see "Features built").

## Rejected directions (tried, then explicitly undone — don't reintroduce without asking)
- **Chromecast**: fully implemented (Cast SDK, `CastPlayer` swap, Settings
  toggle, `MediaRouteButton` in the player bar) then fully removed after a
  crash (`MediaRouteButton` measured against a transparent background) — the
  user chose to drop the feature rather than have it fixed. No Cast/
  Play-Services/MediaRouter dependencies remain in `build.gradle.kts` or
  `libs.versions.toml`.
- **Genre sourced from Radio Browser / ICY `icy-genre`**: removed:
  Radio Browser tag imports and ICY genre updates were "always very messy"
  per the user. Genre is user-defined-only now (see Data model).
  `genreSource`/the DAO's ICY-genre update method are legacy/unused but not
  deleted (would need a schema bump for no real benefit).
- **Style bubble on cards**: tried as a replacement for the mood bubble in
  the top-right cluster, explicitly reverted — **mood** is the correct
  bubble there, for both stations and mixes. Style stays filterable but
  off-card.
- **"Source: X" / "User Defined" subheader text** under Edit Station/Mix
  fields: removed app-wide per user request — don't reintroduce this pattern.

## Known gaps / next steps
1. No DI framework — fine at current scale.
2. ICY `icy-description` isn't parsed by Media3 — Description is
   user-entry-only.
3. No automated tests exist — every verification pass has been manual build
   + install + logcat crash-check + user-driven on-device testing (the
   agent doing this work cannot drive the on-device UI itself — no
   screenshot/tap tooling for the physical phone — so **visual/layout
   changes always need the user's own eyes** before being called done).
4. **Mix backup image round-trip**: `MixBackupManager` correlates bundled
   images to mixes by original mix ID (embedded in the export JSON) — a
   previous bug there (grabbed an arbitrary image instead of the matching
   one) was fixed but is easy to reintroduce if mix ID generation/export
   ever changes.
5. Android Auto: registers as compatible and basic play/pause should work,
   but there's no real browse tree (see "Playback details worth knowing") —
   a `MediaLibraryService` conversion is a real, not-yet-scoped follow-up if
   the user wants full in-car station browsing.
6. `CountryTimeZones.kt`'s country→timezone map is best-effort (~45
   countries, one representative zone each) — genuinely wrong for the
   sliver of listeners in a non-representative zone of a multi-zone country
   (US, Russia, Brazil, etc.). Fine for "roughly when is this station live"
   context, not suitable for anything that needs to be exact.
7. Grid-view genre bubbles are intentionally not shown (see "Features
   built" bubble section) — if the user ever asks for them back, remember
   the width/space trade-off that drove leaving them out.
