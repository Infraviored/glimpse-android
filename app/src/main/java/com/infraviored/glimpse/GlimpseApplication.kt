package com.infraviored.glimpse

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.infraviored.glimpse.data.Api
import okhttp3.OkHttpClient

/** Development aids, only ever set in debug builds. */
object Debug {
    /** Blur every photo: screenshots for design reviews show the layout, not the user's pictures. */
    @Volatile
    var blurPhotos = false
}

/** The logged-in account's API; null while logged out. */
object Session {
    @Volatile
    var api: Api? = null
        private set

    fun open(context: Context): Api? {
        api?.let { return it }
        if (BuildConfig.DEBUG) adoptDebugAccount(context)
        val account = Account.load(context) ?: return null
        return Api(account).also { api = it }
    }

    /**
     * Debug builds only: an account handed over through files/debug-account
     * (server, user, app password, blur flag, one per line), written with `adb shell run-as`.
     * Lets tests run against a real library without the browser login. The file is deleted.
     */
    private fun adoptDebugAccount(context: Context) {
        val file = context.filesDir.resolve("debug-account")
        if (!file.exists()) return
        val lines = file.readLines()
        file.delete()
        if (lines.size >= 3) Account.save(context, Account(lines[0].trim(), lines[1].trim(), lines[2].trim()))
        Debug.blurPhotos = lines.getOrNull(3)?.trim() == "blur"
        context.getSharedPreferences("debug", Context.MODE_PRIVATE).edit().putBoolean("blur", Debug.blurPhotos).apply()
    }

    /** Forget the account and everything cached for it. */
    fun close(context: Context) {
        api = null
        Account.clear(context)
        SingletonImageLoader.get(context).let { loader ->
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }
}

class GlimpseApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Debug.blurPhotos = getSharedPreferences("debug", MODE_PRIVATE).getBoolean("blur", false)
    }

    private val fallback by lazy { OkHttpClient() }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // The session's client adds the app password; read on every request, so a new login takes effect at once
                add(OkHttpNetworkFetcherFactory(callFactory = { Session.api?.http ?: fallback }))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .build()
}
