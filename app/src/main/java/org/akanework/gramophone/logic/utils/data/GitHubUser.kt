package org.akanework.gramophone.logic.utils.data


data class CachedGitHubUser(
    val user: GitHubUser,
    val timestamp: Long = System.currentTimeMillis()
)

data class GitHubUser(
    val login: String = "",
    val name: String = "",
    val avatar_url: String = "",
    val contribute : String = "",
    val bio: String = ""
)