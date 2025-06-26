package org.akanework.gramophone.ui.fragments.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.data.GitHubUser
import org.akanework.gramophone.logic.utils.fetchGitHubUser

class ContributorsSettingsActivity :  AppCompatActivity(){
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemInDarkTheme())
                        dynamicDarkColorScheme(applicationContext)
                    else
                        dynamicLightColorScheme(applicationContext)
                } else {
                    if (isSystemInDarkTheme()) {
                        darkColorScheme()
                    } else {
                        lightColorScheme()
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_contributors)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            }
                        )
                    },
                    content = { paddingValues ->
                        ContributorsSettingsScreen(modifier = Modifier.padding(paddingValues))
                    }
                )
            }
        }
    }

    @Composable
    fun ContributorCard(contributor: GitHubUser) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = MaterialTheme.shapes.medium,
            onClick = {
                val url = "https://github.com/${contributor.login}"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent) }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = contributor.avatar_url.toUri(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row {
                        Text(
                            text = contributor.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "@${contributor.login}",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = contributor.contribute,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    @Composable
    fun ContributorsSettingsScreen(modifier: Modifier) {
        val contributors = remember { mutableStateOf<List<GitHubUser>>(emptyList()) }

        LaunchedEffect(Unit) {
            contributors.value = getContributorsList(applicationContext)
        }

        LazyColumn(modifier = modifier) {
            items(contributors.value) { contributor ->
                ContributorCard(contributor)
            }
        }

    }

    // Get the username in the contributors_user array and get the GitHubUser information
    suspend fun getContributorsList(context: Context): List<GitHubUser> {
        val usernames = context.resources.getStringArray(R.array.contributors_user) // Get the username array
        val contributorsList = mutableListOf<GitHubUser>()

        val contribute = context.resources.getStringArray(R.array.contributors_contribute)

        // Iterate through the usernames to get the GitHub information for each user
        for (username in usernames) {
            try {
                val user =
                    fetchGitHubUser(context = context, username = username) // Fetch GitHub user information
                if (user != null) {
                    contributorsList.add(user.copy(contribute = contribute[usernames.indexOf(username)]))
                } else {
                    // Error handling: you can log or add empty users
                    contributorsList.add(GitHubUser(username, "$username", "", "${contribute[usernames.indexOf(username)]}"))
                }
            } catch (e: Exception) {
                // Error handling: you can log or add empty users
                contributorsList.add(GitHubUser(username, "$username", "", "${contribute[usernames.indexOf(username)]}"))
            }
        }

        return contributorsList
    }

}