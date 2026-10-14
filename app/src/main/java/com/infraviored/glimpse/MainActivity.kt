package com.infraviored.glimpse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infraviored.glimpse.data.Api
import com.infraviored.glimpse.ui.AppState
import com.infraviored.glimpse.ui.Home
import com.infraviored.glimpse.ui.LocalAnimated
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.LocalShared
import com.infraviored.glimpse.ui.Routes
import com.infraviored.glimpse.ui.components.NoticeHost
import com.infraviored.glimpse.ui.folders.FolderScreen
import com.infraviored.glimpse.ui.people.PersonScreen
import com.infraviored.glimpse.ui.search.SearchScreen
import com.infraviored.glimpse.ui.theme.GlimpseTheme
import com.infraviored.glimpse.ui.theme.glimpse
import com.infraviored.glimpse.ui.viewer.ViewerScreen
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val api = Session.open(this) ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) { runCatching { api.loadUser() } }
        setContent {
            GlimpseTheme {
                App(api, onLogout = { message -> logout(api, message) })
            }
        }
    }

    /**
     * Back to the login screen. Logging out on purpose also revokes the app password, so it
     * disappears from the server's list of devices.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun logout(api: Api, message: String?) {
        if (message == null) GlobalScope.launch(Dispatchers.IO) { Nextcloud.revoke(api.account) }
        Session.close(this)
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_MESSAGE, message ?: getString(R.string.logged_out))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun App(api: Api, onLogout: (String?) -> Unit) {
    val nav = rememberNavController()
    val sessionGone = stringResource(R.string.error_session)
    val app = remember(api) {
        AppState(
            api = api,
            navigate = { route ->
                Log.d("Glimpse", "open $route")
                nav.navigate(route) { launchSingleTop = true }
            },
            // Never the last screen: a second back while the viewer is still fading out (its
            // back handler is live until it is gone) popped the start screen too, and the app
            // was an empty dark background
            back = {
                Log.d("Glimpse", "back from ${nav.currentDestination?.route}")
                if (nav.previousBackStackEntry != null) nav.popBackStack()
            },
            logout = { onLogout(null) },
        )
    }
    val authLost by api.authLost.collectAsState()
    LaunchedEffect(authLost) { if (authLost) onLogout(sessionGone) }

    CompositionLocalProvider(LocalApp provides app) {
        SharedTransitionLayout(Modifier.fillMaxSize().background(glimpse.background)) {
            CompositionLocalProvider(LocalShared provides this) {
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        enterTransition = { fadeIn() + slideInHorizontally { it / 6 } },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { fadeOut() + slideOutHorizontally { it / 6 } },
                    ) {
                        composable(Routes.HOME) {
                            CompositionLocalProvider(LocalAnimated provides this) { Home() }
                        }
                        composable(
                            Routes.VIEWER,
                            enterTransition = { fadeIn() },
                            exitTransition = { fadeOut() },
                            popExitTransition = { fadeOut() },
                        ) {
                            CompositionLocalProvider(LocalAnimated provides this) { ViewerScreen() }
                        }
                        composable(
                            Routes.SEARCH,
                            arguments = listOf("q", "person", "similar", "label").map { navArgument(it) { type = NavType.StringType; defaultValue = "" } },
                        ) { entry ->
                            val a = entry.arguments
                            CompositionLocalProvider(LocalAnimated provides this) {
                                Box(Modifier.fillMaxSize().background(glimpse.background)) {
                                    SearchScreen(a?.getString("q").orEmpty(), a?.getString("person").orEmpty(), a?.getString("similar").orEmpty(), a?.getString("label").orEmpty())
                                }
                            }
                        }
                        composable(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                            CompositionLocalProvider(LocalAnimated provides this) {
                                Box(Modifier.fillMaxSize().background(glimpse.background)) { PersonScreen(entry.arguments?.getString("id").orEmpty()) }
                            }
                        }
                        composable(Routes.DUPLICATES) {
                            CompositionLocalProvider(LocalAnimated provides this) {
                                Box(Modifier.fillMaxSize().background(glimpse.background)) { com.infraviored.glimpse.ui.duplicates.DuplicatesScreen() }
                            }
                        }
                        composable(Routes.FOLDER, arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "/" })) { entry ->
                            CompositionLocalProvider(LocalAnimated provides this) {
                                Box(Modifier.fillMaxSize().background(glimpse.background)) { FolderScreen(entry.arguments?.getString("path") ?: "/") }
                            }
                        }
                    }
                    NoticeHost(app.notices, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
