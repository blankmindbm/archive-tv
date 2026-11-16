package com.archivetv.app

import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * Archive TV: a Spelunkr-style live TV player built from Archive.org.
 * The full site is bundled as app/src/main/assets/archive-tv.html
 * and loaded in a WebView. All videos still stream live from
 * archive.org at play time, nothing is hosted or copied.
 * 15 channels, year chooser, directory, guide, and searchable library.
 * Update check: the page checks GitHub releases and calls
 * downloadUpdate() to download the APK in-app and open the installer,
 * so updates no longer need the browser. openUpdate() remains as the
 * fallback and opens the release page in the system browser.
 *
 * Picture-in-Picture: the TV deck has a PiP button, and pressing Home
 * while a video is playing auto-enters PiP. The page hides everything
 * except the video while in PiP (body.pip, see archive-tv.html).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    @Volatile
    private var isVideoPlaying = false

    private var updateDownloadId: Long = -1
    private var pendingUpdateUrl: String? = null
    private var pendingUpdateFileName: String? = null
    private val installRequestCode = 9001
    private var downloadReceiver: BroadcastReceiver? = null

    inner class WebAppInterface {
        @JavascriptInterface
        fun enterPip() {
            runOnUiThread { enterPipMode() }
        }

        @JavascriptInterface
        fun setPlaying(playing: Boolean) {
            isVideoPlaying = playing
        }

        @JavascriptInterface
        fun openUpdate(url: String) {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (_: Exception) {
                    // No browser available, stay in-app.
                }
            }
        }

        @JavascriptInterface
        fun downloadUpdate(url: String, fileName: String) {
            runOnUiThread { startUpdateDownload(url, fileName) }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // If a fullscreen custom view is showing, hide it first so the
        // normal <video> element is the one that keeps playing in PiP.
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
        try {
            enterPictureInPictureMode(params)
        } catch (_: Exception) {
            // PiP not available on this device / launcher, stay in-app.
        }
    }

    private fun setPipUi(isPip: Boolean) {
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "document.body.classList.toggle('pip', $isPip);",
                null
            )
        }
    }

    /**
     * In-app update: download the release APK with DownloadManager and
     * open the installer when it finishes. On Android 8+ the app needs
     * the per-app "install unknown apps" permission first; if it is not
     * granted yet, Android settings are opened and the download resumes
     * when the user comes back.
     */
    private fun startUpdateDownload(url: String, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingUpdateUrl = url
            pendingUpdateFileName = fileName
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, installRequestCode)
            } catch (_: Exception) {
                // Settings unavailable; the update banner stays and the
                // user can retry, falling back to the browser.
                pendingUpdateUrl = null
                pendingUpdateFileName = null
            }
            return
        }
        enqueueUpdateDownload(url, fileName)
    }

    private fun enqueueUpdateDownload(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Archive TV update")
                .setDescription("Downloading $fileName")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName
                )
                .setMimeType("application/vnd.android.package-archive")
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            updateDownloadId = dm.enqueue(request)
        } catch (_: Exception) {
            // Download could not start; the update banner stays and the
            // user can retry from there.
        }
    }

    private fun installDownloadedApk(downloadId: Long) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
                if (!c.moveToFirst()) return
                val status = c.getInt(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                if (status != DownloadManager.STATUS_SUCCESSFUL) return
                val localUri = Uri.parse(
                    c.getString(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                    )
                )
                val file = File(localUri.path ?: return)
                if (!file.exists()) return
                val contentUri = FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        contentUri, "application/vnd.android.package-archive"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(intent)
            }
        } catch (_: Exception) {
            // Installer could not be opened; the APK stays in Downloads
            // and the user can tap it there.
        }
    }

    @Deprecated("Back handling for older Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == installRequestCode) {
            val url = pendingUpdateUrl
            val name = pendingUpdateFileName
            pendingUpdateUrl = null
            pendingUpdateFileName = null
            if (url != null && name != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    packageManager.canRequestPackageInstalls()
                ) {
                    enqueueUpdateDownload(url, name)
                }
                // If still not granted, the user declined; the update
                // banner stays and they can retry.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1
                    )
                    if (id == updateDownloadId) installDownloadedApk(id)
                }
            }
        }
        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, downloadFilter)
        }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            addJavascriptInterface(WebAppInterface(), "ArchiveTV")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // Keep archive.org links inside the app.
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Track whether the stage <video> is playing, so Home
                    // only auto-enters PiP while something is actually on.
                    // Videos are created dynamically, so watch #stage.
                    view?.evaluateJavascript(
                        """
                        (function(){
                          if (window.__pipWatch) return;
                          window.__pipWatch = true;
                          function watch(v){
                            if (!v || v.__pipWatched) return;
                            v.__pipWatched = true;
                            var report = function(){ if (window.ArchiveTV) window.ArchiveTV.setPlaying(!v.paused && !v.ended); };
                            v.addEventListener('play', report);
                            v.addEventListener('pause', report);
                            v.addEventListener('ended', report);
                            report();
                          }
                          function scan(){
                            var s = document.getElementById('stage');
                            if (!s) return;
                            var v = s.querySelector('video');
                            if (v) watch(v);
                          }
                          scan();
                          var obs = new MutationObserver(scan);
                          var stage = document.getElementById('stage');
                          if (stage) obs.observe(stage, {childList: true, subtree: true});
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    val decor = window.decorView as FrameLayout
                    decor.addView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                override fun onHideCustomView() {
                    customView?.let {
                        (window.decorView as FrameLayout).removeView(it)
                    }
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/archive-tv.html")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Pressing Home while a video is playing drops into PiP.
        // Auto-enter is also enabled on Android 12+, this is the
        // fallback for older versions and for the manual PiP button.
        if (isVideoPlaying) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        setPipUi(isInPictureInPictureMode)
    }

    @Deprecated("Back handling for older Android versions")
    override fun onBackPressed() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
        } else if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            downloadReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {
            // Receiver was never registered, or already unregistered.
        }
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
