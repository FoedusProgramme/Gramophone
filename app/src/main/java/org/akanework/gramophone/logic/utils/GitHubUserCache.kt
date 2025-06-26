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
import kotlin.collections.set
import kotlin.io.readText
import kotlin.io.writeText
import kotlin.let
import kotlin.takeIf

object GitHubUserCache {
    private const val CACHE_FILE_NAME = "github_user_cache.json"
    private const val MAX_AGE = 1 * 24 * 60 * 60 * 1000L // Maximum validity of the cache, 1 day

    private val memoryCache = mutableMapOf<String, CachedGitHubUser>()

    // Getting cached GitHub user data from memory
    suspend fun getUser(context: Context, username: String): GitHubUser? {
        // 1. Check the memory cache first
        memoryCache[username]?.takeIf { !it.isExpired() }?.let { return it.user }

        // 2. If the memory cache is invalid, check the local file cache
        loadFromFile(context)

        // 3. Checking the local file cache
        memoryCache[username]?.takeIf { !it.isExpired() }?.let { return it.user }

        // 4. If none are cached, requesting data from the web
        return try {
            val user = githubApi.getUser(username)
            memoryCache[username] = CachedGitHubUser(user)  // Storing data into the memory cache
            saveToFile(context)  // Also save to local cache
            user
        } catch (e: Exception) {
            if (BuildConfig.DEBUG){
                Log.e("GitHubUserCache", "getUser", e)
            }
            null
        }
    }

    // Determine if the cache is out of date
    private fun CachedGitHubUser.isExpired(): Boolean {
        return System.currentTimeMillis() - this.timestamp > MAX_AGE
    }

    // Clear expired cache
    private fun cleanExpired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            memoryCache.entries.removeIf { it.value.isExpired()}
        } else {
            val iterator = memoryCache.entries.iterator()
            while (iterator.hasNext()) {
                val exp = iterator.next().value
                if (exp.isExpired()) {
                    iterator.remove()
                }
            }

        }
    }

    // Load cache from file
    private fun loadFromFile(context: Context) {
        val file = File(context.cacheDir, CACHE_FILE_NAME)
        if (!file.exists()) return

        try {
            val mapType = object : TypeToken<Map<String, CachedGitHubUser>>() {}.type
            val jsonMap: Map<String, CachedGitHubUser> =
                Gson().fromJson(file.readText(), mapType)

            memoryCache.clear()  // Clear memory cache before loading
            memoryCache.putAll(jsonMap)
            cleanExpired()  // Clear expired cache
        } catch (e: Exception) {
            memoryCache.clear()  // Empty cache on load failure
        }
    }

    // Save cached data to a file
    private fun saveToFile(context: Context) {
        cleanExpired()  // Clear expired cache before saving
        val file = File(context.cacheDir, CACHE_FILE_NAME)
        file.writeText(Gson().toJson(memoryCache))
    }

    // Clear all caches (manually triggered) (unused)
    fun clearAll(context: Context) {
        memoryCache.clear()
        File(context.cacheDir, CACHE_FILE_NAME).delete()
    }
}
