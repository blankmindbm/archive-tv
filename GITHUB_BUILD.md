# Build the APK with GitHub, no Android Studio

Important: the Muse GitHub App cannot create a repo, and it cannot
create or edit a file under .github/workflows. So those two steps
are yours, same as with Print Search Hub and Our Cozy Farm. Once you
have done them, I can push the rest of the files for you if you want.

## Your steps on github.com

1. Go to github.com/new, sign in, name the repo archive-tv,
   leave it public (so GitHub Pages works), and create it empty.
2. Make sure the Muse GitHub App installation covers that repo
   (same as you did for print-search-hub and cozy-farm, allow all
   repos is easiest).
3. Tell me you made the repo and I will push all files except the
   workflow file.
4. Add .github/workflows/build-apk.yml yourself through the
   github.com web editor: Add file, Create new file, paste the
   workflow from the attached file, commit directly to main.
   The workflow runs on push.
5. Open the Actions tab, open the Build APK run, and wait for it to
   finish. A first build can take a few minutes while Gradle and
   the Android SDK download.
6. At the bottom of the finished run, download the
   archive-tv-debug-apk artifact. It is a zip, unzip it, and you
   will have app-debug.apk inside.
7. Send app-debug.apk to your phone, tap it, allow install from
   that source when Android asks, and install it. It is a debug
   APK, not a Play Store app, so Android will warn you about an
   unknown source. That warning is normal for a self built debug APK.

## Website

After the repo is created, turn on GitHub Pages: Settings, Pages,
Deploy from a branch, branch main, folder / (root). The website will
be live at https://blankmindbm.github.io/archive-tv/ . The file
served is index.html at the repo root, the same Archive TV page
bundled in the app.

## If the workflow fails

Open the failed run, open the failed step, and copy the last error
lines back to me. The most common causes are a missing
.github/workflows/build-apk.yml file, or a file uploaded with the
wrong folder structure, for example everything nested under an extra
archive-tv folder inside the repo. The repo root should contain
settings.gradle.kts, build.gradle.kts, index.html, and the app
folder directly.

## Workflow notes

The workflow uses Gradle 8.7, JDK 17 Temurin, and installs platform
34 and build-tools 34.0.0 from the runner's preinstalled SDK. Do not
use android-actions/setup-android, version 3 fails because it tries
to install the obsolete sdkmanager tools package.
