package com.infraviored.glimpse

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.infraviored.glimpse.data.errorText
import com.infraviored.glimpse.ui.theme.GlimpseTheme
import com.infraviored.glimpse.ui.theme.Palette
import com.infraviored.glimpse.ui.theme.glimpse
import kotlinx.coroutines.CancellationException
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

    private var server by mutableStateOf("")
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var job: Job? = null

    /** Login done while the browser was still in front: continue once we are back. */
    private var pending: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        server = Account.lastServer(this).removePrefix("https://")
        status = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        setContent { GlimpseTheme { LoginScreen() } }
    }

    override fun onResume() {
        super.onResume()
        pending?.let { finishLogin() }
    }

    @Composable
    private fun LoginScreen() {
        Column(
            Modifier
                .fillMaxSize()
                .background(glimpse.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(72.dp))
            // The adaptive launcher icon, from its two vector layers (painterResource cannot draw adaptive icons)
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(28.dp))
            Text("Glimpse", style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp), color = glimpse.text)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.login_intro), style = MaterialTheme.typography.bodyLarge, color = glimpse.muted)
            Spacer(Modifier.height(40.dp))

            Text(stringResource(R.string.login_server_label), style = MaterialTheme.typography.titleMedium, color = glimpse.text)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(glimpse.raised)
                    .border(1.dp, glimpse.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (server.isEmpty()) Text(stringResource(R.string.login_server_hint), color = glimpse.muted, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = server,
                    onValueChange = { server = it },
                    enabled = !busy,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = glimpse.text),
                    cursorBrush = SolidColor(glimpse.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { start() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (busy) SolidColor(glimpse.raised) else Palette.Lens)
                    .clickable(enabled = !busy) { start() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = glimpse.text)
                } else {
                    Text(stringResource(R.string.login_connect), style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(status, style = MaterialTheme.typography.bodyLarge, color = glimpse.text)
            }
            if (busy) {
                TextButton(onClick = { stop(null) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.login_cancel), color = glimpse.accentText)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.login_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = glimpse.muted,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }

    private fun start() {
        val address = Nextcloud.normalizeServer(server)
        if (address == null) {
            status = getString(R.string.error_address)
            return
        }
        busy = true
        job = lifecycleScope.launch {
            try {
                status = getString(R.string.login_checking)
                val flow = withContext(Dispatchers.IO) {
                    Nextcloud.checkServer(address)
                    Nextcloud.startLogin(address)
                }
                try {
                    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this@LoginActivity, flow.loginUrl.toUri())
                } catch (e: ActivityNotFoundException) {
                    throw LoginException(R.string.error_no_browser)
                }
                status = getString(R.string.login_waiting)
                val account = waitFor(flow)
                status = getString(R.string.login_checking_glimpse)
                if (!withContext(Dispatchers.IO) { Nextcloud.hasGlimpse(account) }) {
                    withContext(Dispatchers.IO) { Nextcloud.revoke(account) }
                    throw LoginException(R.string.error_no_glimpse)
                }
                Account.save(this@LoginActivity, account)
                pending = account
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    finishLogin()
                } else {
                    status = getString(R.string.login_done_return)
                }
            } catch (e: LoginException) {
                stop(getString(e.messageRes) + (e.detail?.let { "\n($it)" } ?: ""))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stop(getString(R.string.error_unexpected, errorText(e)))
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

    private fun finishLogin() {
        pending = null
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun stop(message: String?) {
        job?.cancel()
        job = null
        busy = false
        status = message ?: ""
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}

