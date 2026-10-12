package com.infraviored.glimpse

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server address, then Nextcloud's own login page in the browser (Login Flow v2): passwords,
 * two-factor and SSO stay with Nextcloud, the app only gets an app password back.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var server: EditText
    private lateinit var connect: Button
    private lateinit var cancel: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private var job: Job? = null

    /** Login done while the browser was still in front: continue once we are back. */
    private var pending: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        applySystemBarPadding(findViewById(R.id.root))

        server = findViewById(R.id.server)
        connect = findViewById(R.id.connect)
        cancel = findViewById(R.id.cancel)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        server.setText(Account.lastServer(this))
        intent.getStringExtra(EXTRA_MESSAGE)?.let { status.text = it }
        connect.setOnClickListener { start() }
        cancel.setOnClickListener { stop(null) }
        server.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) start()
            action == EditorInfo.IME_ACTION_GO
        }
    }

    override fun onResume() {
        super.onResume()
        pending?.let { finishLogin(it) }
    }

    private fun start() {
        val address = Nextcloud.normalizeServer(server.text.toString())
        if (address == null) {
            status.setText(R.string.error_address)
            return
        }
        busy(true)
        job = lifecycleScope.launch {
            try {
                status.setText(R.string.login_checking)
                val flow = withContext(Dispatchers.IO) {
                    Nextcloud.checkServer(address)
                    Nextcloud.startLogin(address)
                }
                try {
                    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this@LoginActivity, flow.loginUrl.toUri())
                } catch (e: ActivityNotFoundException) {
                    throw LoginException(R.string.error_no_browser)
                }
                status.setText(R.string.login_waiting)
                val account = waitFor(flow)
                status.setText(R.string.login_checking_glimpse)
                if (!withContext(Dispatchers.IO) { Nextcloud.hasGlimpse(account) }) {
                    withContext(Dispatchers.IO) { Nextcloud.revoke(account) }
                    throw LoginException(R.string.error_no_glimpse)
                }
                Account.save(this@LoginActivity, account)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    finishLogin(account)
                } else {
                    pending = account
                    status.setText(R.string.login_done_return)
                }
            } catch (e: LoginException) {
                stop(getString(e.messageRes) + (e.detail?.let { "\n($it)" } ?: ""))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                stop(getString(R.string.error_unexpected, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Ask every 2 s; Nextcloud forgets the flow after 20 minutes. */
    private suspend fun waitFor(flow: Nextcloud.Flow): Account {
        val until = System.currentTimeMillis() + 20 * 60 * 1000
        while (kotlin.coroutines.coroutineContext.isActive && System.currentTimeMillis() < until) {
            val account = try {
                withContext(Dispatchers.IO) { Nextcloud.poll(flow) }
            } catch (e: java.io.IOException) {
                null // brief network loss: keep waiting
            }
            if (account != null) return account
            delay(2000)
        }
        throw LoginException(R.string.error_login_timeout)
    }

    private fun finishLogin(account: Account) {
        pending = null
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun stop(message: String?) {
        job?.cancel()
        job = null
        busy(false)
        status.text = message ?: ""
    }

    private fun busy(on: Boolean) {
        connect.isEnabled = !on
        server.isEnabled = !on
        progress.visibility = if (on) View.VISIBLE else View.GONE
        cancel.visibility = if (on) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
