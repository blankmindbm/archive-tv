# Archive TV

A Spelunkr-style live TV site built from Archive.org, as an Android app and as a website.

13 live channels on a shared half-hour clock, so you tune in mid-show like real TV:
02 Saturday Morning, 03 Feature Films, 04 Noir, 05 Sci-Fi Theater, 06 Horror After Dark, 07 Prelinger, 08 Newsreel Room, 09 Computer Chronicles, 10 Silent Era, 11 NASA and Space, 12 Commercial Break, 13 VHS Vault, 14 Fresh and Recent.

Plus a searchable library over about 14,022,379 Archive.org titles through 2026, a year chooser in the TV deck (All years, or a specific year from 1900 to today), a Directory listing every channel in dial order with what is playing now, a guide, and a retro TV control deck under the video with play/pause, CH minus/plus, and small channel buttons.

Nothing is hosted or copied. Every title and video streams live from archive.org at play time.

## Website

The website is `index.html` at the repo root, the same file as the app's bundled copy. Enable GitHub Pages on this repo, Settings, Pages, Deploy from a branch, branch `main`, folder `/ (root)`. The site will then be live at `https://blankmindbm.github.io/archive-tv/`.

## Android app

The app is a WebView wrapper around the same `index.html`, bundled as `app/src/main/assets/archive-tv.html`. It needs internet to fetch Archive.org listings and stream video.

Build it with GitHub Actions, no Android Studio needed. See `GITHUB_BUILD.md` for the exact steps. The workflow builds a debug APK named `archive-tv-debug-apk`.

Package: `com.jeremy.archivetv`, version 1.0 (versionCode 1).
Build: Gradle 8.7, JDK 17 Temurin, Android platform 34, build-tools 34.0.0.
