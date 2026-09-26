/*
 *     Copyright (C) 2025 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.akanework.gramophone.logic.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.allowDiskAccessInStrictMode
import org.akanework.gramophone.logic.hasOsClipboardDialog
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.LocalCardSurface
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * Where the app lands after an uncaught exception: the crash log, a way to mail it to the
 * developer with a description, and a way to share it.
 */

private const val CRASH_MAIL = "nift4@posteo.net"

class BugHandlerActivity : BaseActivity() {
    private var shouldSendEmail = true
    private var triedToSendEmail = false
    private var log = "(null)"
    private val reportOpen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val exceptionMessage = intent.getStringExtra("exception_message")
        val threadName = intent.getStringExtra("thread")
        val formattedDateTime =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().time)
        log = StringBuilder()
            .append(getString(R.string.crash_gramophone_version)).append(": ")
            .append(BuildConfig.MY_VERSION_NAME).append(" (").append(packageName).append(")\n\n")
            .append(getString(R.string.crash_rtype)).append(": ").append(BuildConfig.RELEASE_TYPE).append('\n')
            .append(getString(R.string.crash_phone_brand)).append(":        ").append(Build.BRAND).append('\n')
            .append(getString(R.string.crash_phone_model)).append(":        ").append(Build.MODEL).append('\n')
            .append(getString(R.string.crash_sdk_level)).append(":    ").append(Build.VERSION.SDK_INT).append('\n')
            .append(getString(R.string.crash_thread)).append(":       ").append(threadName).append("\n\n\n")
            .append(getString(R.string.crash_time)).append(":  ").append(formattedDateTime).append('\n')
            .append("--------- beginning of crash").append('\n')
            .append(exceptionMessage)
            .toString()
        if (BuildConfig.DEBUG && !log.contains("I crashed your app")) {
            shouldSendEmail = false
        }
        if (!shouldSendEmail) copyToClipboard()

        setContent {
            GramophoneTheme {
                BackHandler { goBack() }
                CrashScreen(
                    log = log,
                    showEmailCard = shouldSendEmail,
                    onBack = { goBack() },
                    onSendEmail = { reportOpen.value = true },
                    onShare = { share() },
                )
                if (reportOpen.value) {
                    CrashReportDialog(
                        log = log,
                        onDismiss = { reportOpen.value = false },
                        onSend = { description ->
                            reportOpen.value = false
                            sendEmail(description)
                        },
                    )
                }
            }
        }
    }

    private fun goBack() {
        if (!shouldSendEmail || triedToSendEmail) finish()
        else reportOpen.value = true
    }

    private fun share() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, "Gramophone Logs")
            putExtra(Intent.EXTRA_TEXT, log)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("error msg", log)
        allowDiskAccessInStrictMode { clipboard.setPrimaryClip(clip) }
        if (!hasOsClipboardDialog()) {
            Toast.makeText(this, R.string.crash_clipboard, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendEmail(description: String?) {
        Log.w("Gramophone", "Exporting logs due to crash...")
        val policy = StrictMode.allowThreadDiskWrites()
        val crashLogDir: File
        val f: File
        try {
            crashLogDir = File(cacheDir, "CrashLog")
            f = File(crashLogDir, "GramophoneLog${System.currentTimeMillis()}.txt")
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
        val mailText = "Hi Nick,\n\nGramophone crashed!\nI was doing:\n\n" +
                "${description ?: "--INSERT DESCRIPTION HERE--"}\n\nIt crashed with this" +
                " log:\n\n\n$log"
        triedToSendEmail = true
        CoroutineScope(Dispatchers.IO).launch {
            crashLogDir.mkdirs()
            f.writeText(mailText)
            val p = ProcessBuilder().command("logcat", "-dball").start()
            try {
                withTimeout(1500) {
                    val stdout = p.inputStream.readBytes().toString(Charset.defaultCharset())
                    val stderr = p.errorStream.readBytes().toString(Charset.defaultCharset())
                    runInterruptible {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            p.waitFor(1, TimeUnit.SECONDS)
                        } else {
                            p.waitFor()
                        }
                    }
                    f.writeText("$stdout\n$stderr\n\n\n==MAIL TEXT==\n\n\n$mailText")
                }
            } catch (_: TimeoutCancellationException) {
            }
            withContext(Dispatchers.Main) {
                try {
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        selector = Intent(Intent.ACTION_SENDTO).apply { setData("mailto:$CRASH_MAIL".toUri()) }
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(CRASH_MAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "Gramophone ${BuildConfig.MY_VERSION_NAME} crashed")
                        putExtra(Intent.EXTRA_TEXT, mailText)
                        putExtra(
                            Intent.EXTRA_STREAM,
                            FileProvider.getUriForFile(
                                this@BugHandlerActivity, "${packageName}.fileProvider", f
                            )
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@BugHandlerActivity, R.string.send_email_manually, Toast.LENGTH_LONG).show()
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("email text", "$CRASH_MAIL\n\n\n$mailText")
                    allowDiskAccessInStrictMode { clipboard.setPrimaryClip(clip) }
                    Toast.makeText(this@BugHandlerActivity, R.string.email_clipboard, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStop() {
        if (shouldSendEmail && !triedToSendEmail) copyToClipboard()
        super.onStop()
    }
}

@Composable
private fun CrashScreen(
    log: String,
    showEmailCard: Boolean,
    onBack: () -> Unit,
    onSendEmail: () -> Unit,
    onShare: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(background)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = insets.calculateTopPadding() + GLASS_BAR_HEIGHT,
                    bottom = insets.calculateBottomPadding() + 96.dp,
                )
                .padding(horizontal = 16.dp),
        ) {
            if (showEmailCard) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(LocalCardSurface.current, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                ) {
                    Icon(
                        Icons.Outlined.BugReport, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.oh_no), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gramophone_crashed_explainer2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onSendEmail) { Text(stringResource(R.string.send_email)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = log,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        GlassTitleBar(
            hazeState = hazeState,
            title = stringResource(R.string.crash_report),
            scrolled = { Float.MAX_VALUE },
            toolbarPaddingStart = 4.dp,
            titlePaddingStart = 4.dp,
            navigationIcon = {
                LibraryIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
            },
        )
        ExtendedFloatingActionButton(
            onClick = onShare,
            icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            text = { Text(stringResource(R.string.share)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = insets.calculateBottomPadding() + 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun CrashReportDialog(log: String, onDismiss: () -> Unit, onSend: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val error = when {
        text.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(text).matches() ->
            stringResource(R.string.do_not_enter_email_enter_msg)
        text.contains(log) -> stringResource(R.string.log_will_auto_send)
        else -> null
    }
    val canSend = error == null && text.trim().length > 3
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_report)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.what_were_you_doing))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.log_attachment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(text.takeIf { it.isNotBlank() }) }, enabled = canSend) {
                Text(stringResource(R.string.send_email))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

