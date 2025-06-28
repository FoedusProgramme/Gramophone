package org.akanework.gramophone.ui.fragments.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.plus
import org.akanework.gramophone.logic.utils.data.Contributors
import org.akanework.gramophone.logic.utils.data.GitHubUser

class ContributorsSettingsActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemInDarkTheme())
                        dynamicLightColorScheme(applicationContext)
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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
                        )
                    },
                    content = { paddingValues ->
                        ContributorsSettingsScreen(paddingValues)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    }

    @Composable
    fun ContributorCard(shape: Shape, contributor: GitHubUser) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = shape,
            onClick = {
                val url = "https://github.com/${contributor.login}"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_LONG).show()
                }
            }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = contributor.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contributor.name ?: contributor.login,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (contributor.name != null && contributor.name != contributor.login)
                            Text(
                                text = "@${contributor.login}",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                color = LocalContentColor.current.copy(alpha = 0.8f)
                            )
                    }
                    Text(
                        text = stringResource(contributor.contributed),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = LocalContentColor.current.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    @Composable
    fun ContributorsSettingsScreen(contentPaddingValues: PaddingValues) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp) + contentPaddingValues) {
            itemsIndexed(Contributors.LIST) { i, contributor ->
                val top = if (i == 0) CornerSize(16.dp) else
                    CornerSize(8.dp)
                val bottom = if (i == Contributors.LIST.size - 1) CornerSize(16.dp) else
                    CornerSize(8.dp)
                ContributorCard(RoundedCornerShape(
                    top, top, bottom, bottom
                ), contributor)
            }
        }
    }
}