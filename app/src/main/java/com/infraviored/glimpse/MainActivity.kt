package com.infraviored.glimpse

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glimpse itself: the Nextcloud page in a full-screen WebView. The app password logs the
 * WebView in (Nextcloud answers a Basic-auth request with a session cookie); links to
 * other sites open in the browser.
 */
class MainActivity : ComponentActivity() {

    private lateinit var account: Account
    private lateinit var root: FrameLayout
    /** Behind the status bar, in the page's header color. */
    private lateinit var statusBar: View
    private lateinit var web: WebView
    private lateinit var errorView: LinearLayout
    private lateinit var errorText: TextView
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** When the app last logged the WebView in again; a second login page right after means the password is gone. */
    private var reloginAt = 0L

    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        account = Account.load(this) ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        root = FrameLayout(this)
        web = WebView(this)
        val content = FrameLayout(this)
        content.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(buildErrorView())
        applySystemBarPadding(content)
        statusBar = View(this)
        // First: before Android 11 a child that consumes the insets hides them from later siblings
        root.addView(statusBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(statusBar) { v, insets ->
            v.layoutParams = v.layoutParams.apply { height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top }
            insets
        }
        setContentView(root)

        setUpWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreen != null -> fullscreenCallback?.onCustomViewHidden()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            openGlimpse()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }

    private fun openGlimpse() {
        errorView.visibility = View.GONE
        reloginAt = SystemClock.elapsedRealtime()
        web.loadUrl(account.glimpseUrl, mapOf("Authorization" to account.basicAuth))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            // Glimpse can tell it runs in the app (and e.g. hand downloads over)
            userAgentString = "$userAgentString GlimpseAndroid/${BuildConfig.VERSION_NAME}"
        }

        val bridge = AppBridge(this, account.server)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "GlimpseApp", setOf(bridge.origin)) { _, message, _, _, _ ->
                bridge.onMessage(message.data)
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (!isOwnServer(url)) {
                    openExternally(url)
                    return true
                }
                if (url.path.orEmpty().endsWith("/logout")) {
                    logout(null)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (isLoginPage(url.toUri())) {
                    sessionLost()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                adoptThemeColor()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showError(getString(R.string.error_page, error.description))
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode == 503) {
                    showError(getString(R.string.error_maintenance))
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The page's renderer crashed or was killed for memory: start over instead of crashing too
                recreate()
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickFiles.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreen = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                WindowCompat.getInsetsController(window, root).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            override fun onHideCustomView() {
                fullscreen?.let { root.removeView(it) }
                fullscreen = null
                fullscreenCallback = null
                WindowCompat.getInsetsController(window, root).show(WindowInsetsCompat.Type.systemBars())
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun isOwnServer(url: Uri): Boolean {
        val own = account.server.toUri()
        return url.scheme == own.scheme && url.host == own.host && url.port == own.port &&
            url.path.orEmpty().startsWith(own.path.orEmpty())
    }

    /** Nextcloud's login form (not the Login Flow pages the user may open on purpose). */
    private fun isLoginPage(url: Uri): Boolean {
        if (!isOwnServer(url)) return false
        val path = url.path.orEmpty().removePrefix(account.server.toUri().path.orEmpty())
        return path == "/login" || path == "/index.php/login"
    }

    /**
     * Nextcloud sent the login form: the session cookie expired. Log in again with the app
     * password; if that leads straight back here, the password was revoked.
     */
    private fun sessionLost() {
        web.stopLoading()
        if (SystemClock.elapsedRealtime() - reloginAt < 15000) {
            logout(getString(R.string.error_session))
        } else {
            openGlimpse()
        }
    }

    private fun openExternally(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun download(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val uri = url.toUri()
        if (uri.scheme != "https" && uri.scheme != "http") {
            Toast.makeText(this, R.string.download_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(uri)
            .setTitle(name)
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
            .addRequestHeader("User-Agent", userAgent)
        if (isOwnServer(uri)) {
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            request.addRequestHeader("Authorization", account.basicAuth)
        }
        getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
    }

    /** Status bar in the page's theme color, with icons that stay readable on it. */
    private fun adoptThemeColor() {
        web.evaluateJavascript(
            "(function(){var m=document.querySelector('meta[name=theme-color]');return m?m.content:''})()",
        ) { value ->
            val color = try {
                value.trim('"').takeIf { it.startsWith("#") }?.toColorInt()
            } catch (e: IllegalArgumentException) {
                null
            } ?: return@evaluateJavascript
            statusBar.setBackgroundColor(color)
            WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars =
                ColorUtils.calculateLuminance(color) > 0.5
        }
    }

    private fun buildErrorView(): LinearLayout {
        val pad = (24 * resources.displayMetrics.density).toInt()
        errorText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val retry = Button(this).apply {
            setText(R.string.retry)
            setOnClickListener { if (web.url.isNullOrEmpty()) openGlimpse() else web.reload().also { errorView.visibility = View.GONE } }
        }
        errorView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(if (isDark()) Color.rgb(23, 23, 23) else Color.WHITE)
            isClickable = true
            visibility = View.GONE
            addView(errorText)
            addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = pad
            })
        }
        errorText.setTextColor(if (isDark()) Color.WHITE else Color.BLACK)
        return errorView
    }

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun showError(message: String) {
        errorText.text = message
        errorView.visibility = View.VISIBLE
    }

    /**
     * Back to the login screen. A logout the user asked for also revokes the app password,
     * so it disappears from the server's device list.
     */
    private fun logout(message: String?) {
        val old = account
        Account.clear(this)
        CookieManager.getInstance().removeAllCookies(null)
        web.clearHistory()
        lifecycleScope.launch {
            if (message == null) withContext(Dispatchers.IO) { Nextcloud.revoke(old) }
            startActivity(
                Intent(this@MainActivity, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_MESSAGE, message ?: getString(R.string.logged_out)),
            )
            finish()
        }
    }
}
