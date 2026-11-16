package com.archivetv.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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

/**
 * Archive TV: a Spelunkr-style live TV player built from Archive.org.
 * The full site is bundled as app/src/main/assets/archive-tv.html
 * and loaded in a WebView. All videos still stream live from
 * archive.org at play time, nothing is hosted or copied.
 * 15 channels, year chooser, directory, guide, and searchable library.
 * Update check: the page checks GitHub releases and calls openUpdate()
 * to open the release page in the system browser for download.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        webView.destroy()
        super.onDestroy()
    }
}
