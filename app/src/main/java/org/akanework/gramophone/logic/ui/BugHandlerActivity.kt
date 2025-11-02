/*
 *     Copyright (C) 2024 Akane Foundation
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
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.text.Editable
import android.text.TextWatcher
import androidx.media3.common.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.app.DialogCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.text.trimmedLength
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.hasOsClipboardDialog
import org.akanework.gramophone.logic.updateMargin
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * BugHandlerActivity:
 *   An activity makes crash reporting easier.
 */
class BugHandlerActivity : BaseActivity() {

    private var shouldSendEmail = true
    private var triedToSendEmail = false
    private var log = "(null)"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bug_handler)
        findViewById<View>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback { goBack() }

        val bugText = findViewById<TextView>(R.id.error)
        val actionShare = findViewById<ExtendedFloatingActionButton>(R.id.actionShare)
        val exceptionMessage = intent.getStringExtra("exception_message")
        val threadName = intent.getStringExtra("thread")

        val deviceBrand = Build.BRAND
        val deviceModel = Build.MODEL
        val sdkLevel = Build.VERSION.SDK_INT
        val currentDateTime = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDateTime = formatter.format(currentDateTime)
        val gramophoneVersion = BuildConfig.MY_VERSION_NAME

        log = StringBuilder()
            .append(getString(R.string.crash_gramophone_version))
            .append(':')
            .append(' ')
            .append(gramophoneVersion)
            .append(" (")
            .append(packageName)
            .append(')')
            .append('\n')
            .append('\n')
            .append(getString(R.string.crash_rtype))
            .append(':')
            .append(" ")
            .append(BuildConfig.RELEASE_TYPE)
            .append('\n')
            .append(getString(R.string.crash_phone_brand))
            .append(':')
            .append("        ")
            .append(deviceBrand)
            .append('\n')
            .append(getString(R.string.crash_phone_model))
            .append(':')
            .append("        ")
            .append(deviceModel)
            .append('\n')
            .append(getString(R.string.crash_sdk_level))
            .append(':')
            .append("    ")
            .append(sdkLevel)
            .append('\n')
            .append(getString(R.string.crash_thread))
            .append(':')
            .append("       ")
            .append(threadName)
            .append('\n')
            .append('\n')
            .append('\n')
            .append(getString(R.string.crash_time))
            .append(':')
            .append("  ")
            .append(formattedDateTime)
            .append('\n')
            .append("--------- beginning of crash")
            .append('\n')
            .append(exceptionMessage)
            .toString()

        if (BuildConfig.DEBUG && !log.contains("I crashed your app")) {
            shouldSendEmail = false
            findViewById<View>(R.id.emailCard).visibility = View.GONE
        }
        bugText.typeface = Typeface.MONOSPACE
        bugText.text = log
        val baseLeft = actionShare.marginLeft
        val baseRight = actionShare.marginRight
        val baseBottom = actionShare.marginBottom
        findViewById<View>(R.id.container).enableEdgeToEdgePaddingListener {
            actionShare.updateMargin {
                left = baseLeft + it.left
                right = baseRight + it.right
                bottom = baseBottom + it.bottom
            }
        }
        findViewById<Button>(R.id.sendEmail).setOnClickListener { sendEmail() }
        if (!shouldSendEmail)
            copyToClipboard()
        actionShare.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, "Gramophone Logs")
                putExtra(Intent.EXTRA_TEXT, bugText.text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }

    private fun goBack() {
        if (!shouldSendEmail || triedToSendEmail)
            finish()
        else
            sendEmail()
    }

    private fun copyToClipboard() {
        // Make our life easier by copying the log to clipboard
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("error msg", log)
        allowDiskAccessInStrictMode {
            clipboard.setPrimaryClip(clip)
        }
        if (!hasOsClipboardDialog()) {
            Toast.makeText(this, R.string.crash_clipboard, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendEmail() {
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
        val d = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_report)
            .setView(R.layout.crash_dialog_content)
            .setCancelable(false)
            .setPositiveButton(R.string.send_email) { d, _ ->
                val et = DialogCompat.requireViewById(d as AlertDialog, R.id.editText) as TextInputEditText
                val desc = et.editableText.toString().takeIf { it.isNotBlank() }
                val mailText = "Hi Nick,\n\nGramophone crashed!\nI was doing:\n\n" +
                        "${desc ?: "--INSERT DESCRIPTION HERE--"}\n\nIt crashed with this" +
                        " log:\n\n\n$log"
                triedToSendEmail = true
                CoroutineScope(Dispatchers.IO).launch {
                    crashLogDir.mkdirs()
                    f.writeText(mailText)
                    val p = ProcessBuilder()
                        .command("logcat", "-dball")
                        .start()
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
                    } catch (_: TimeoutCancellationException) {}
                    withContext(Dispatchers.Main) {
                        try {
                            startActivity(Intent(Intent.ACTION_SEND).apply {
                                selector = Intent(Intent.ACTION_SENDTO).apply { setData("mailto:nift4dev@gmail.com".toUri()) }
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("nift4dev@gmail.com"))
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Gramophone ${BuildConfig.MY_VERSION_NAME} crashed"
                                )
                                putExtra(Intent.EXTRA_TEXT, mailText)
                                putExtra(Intent.EXTRA_STREAM,
                                    FileProvider.getUriForFile(this@BugHandlerActivity, "${this@BugHandlerActivity.packageName}.fileProvider", f))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(this@BugHandlerActivity, R.string.send_email_manually, Toast.LENGTH_LONG).show()
                            val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("email text",
                                "Send below text to nift4dev@gmail.com:\n\n\n$mailText"
                            )
                            allowDiskAccessInStrictMode {
                                clipboard.setPrimaryClip(clip)
                            }
                            Toast.makeText(this@BugHandlerActivity, R.string.email_clipboard, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.show()
        val til = DialogCompat.requireViewById(d, R.id.textInputLayout) as TextInputLayout
        val et = DialogCompat.requireViewById(d, R.id.editText) as TextInputEditText
        val b = d.getButton(DialogInterface.BUTTON_POSITIVE)
        b.isEnabled = false
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                // do nothing
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                // do nothing
            }

            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty() && Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
                    til.error = getString(R.string.do_not_enter_email_enter_msg)
                    b.isEnabled = false
                } else if (s?.contains(log) == true) {
                    til.error = getString(R.string.log_will_auto_send)
                    b.isEnabled = false
                } else if ((s?.trimmedLength() ?: 0) <= 3) {
                    til.error = null
                    b.isEnabled = false
                } else {
                    til.error = null
                    b.isEnabled = true
                }
            }
        })
        et.requestFocus()
        et.post {
            if (ViewCompat.getRootWindowInsets(d.window!!.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            ) {
                WindowInsetsControllerCompat(d.window!!, et).show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    override fun onStop() {
        if (shouldSendEmail && !triedToSendEmail)
            copyToClipboard()
        super.onStop()
    }
}
