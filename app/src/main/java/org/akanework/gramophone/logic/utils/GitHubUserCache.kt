package org.akanework.gramophone.logic.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.utils.data.CachedGitHubUser
import org.akanework.gramophone.logic.utils.data.GitHubUser
import java.io.File
import java.io.IOException
import kotlin.collections.set
import kotlin.io.readText
import kotlin.io.writeText
import kotlin.let
import kotlin.takeIf

object GitHubUserCache {
    private const val CACHE_FILE_NAME = "github_user_cache.json"

    private val memoryCache = mutableMapOf<String, CachedGitHubUser>()

    // Getting cached GitHub user data from memory
    fun getUser(context: Context, username: String): GitHubUser? {
        // 1. Check the memory cache first
        memoryCache[username]?.let { return it.user }

        // 2. If the memory cache is invalid, check the local file cache
        loadFromFile(context)

        // 3. Checking the local file cache
        memoryCache[username]?.let { return it.user }

        return null
    }

    // Load cache from file
    private fun loadFromFile(context: Context) {
        val assetManager = context.assets
        val assetFileName = "github_cache_output.json"
        val file = File(context.filesDir, CACHE_FILE_NAME)

        try {
            assetManager.open(assetFileName).use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d("CopyAsset", "Copied $assetFileName to ${file.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("CopyAsset", "Failed to copy asset file: $assetFileName")
        }

        if (!file.exists()) return
        try {
            val mapType = object : TypeToken<Map<String, CachedGitHubUser>>() {}.type
            val jsonMap: Map<String, CachedGitHubUser> =
                Gson().fromJson(file.readText(), mapType)
            memoryCache.clear()  // Clear memory cache before loading
            memoryCache.putAll(jsonMap)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("GitHubUserCache", "Error loading cache from file", e)
            memoryCache.clear()  // Empty cache on load failure
        }
    }

    fun readAssetFile(context: Context, filename: String): String {
        context.assets.open(filename).use { inputStream ->
            return inputStream.bufferedReader().readText()
        }
    }


    // Clear all caches (manually triggered) (unused)
    fun clearAll(context: Context) {
        memoryCache.clear()
        File(context.cacheDir, CACHE_FILE_NAME).delete()
    }
}
