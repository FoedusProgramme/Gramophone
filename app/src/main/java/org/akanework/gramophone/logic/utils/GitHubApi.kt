package org.akanework.gramophone.logic.utils

import android.content.Context
import okhttp3.OkHttpClient
import org.akanework.gramophone.logic.utils.data.GitHubUser
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.jvm.java

fun fetchGitHubUser(username: String, context: Context): GitHubUser? {
    return GitHubUserCache.getUser(context, username)
}

