/*
 *     Copyright (C) 2026 The Gramophone authors
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

package org.akanework.gramophone.logic.init

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.Log
import org.akanework.gramophone.logic.ui.BugHandlerActivity
import kotlin.system.exitProcess

/**
 * Default uncaught-exception handler: opens [BugHandlerActivity] with the stack trace and kills
 * the process. Installed from the Application's constructor so it covers everything after it.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        // TODO convert to notification that opens BugHandlerActivity on click, and let JVM
        //  go through the normal exception process (to get stats from play). disadvantage: we can't
        //  cheat the statistic that way
        val exceptionMessage = Log.getThrowableString(e)
        val threadName = Thread.currentThread().name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(context, BugHandlerActivity::class.java)
        intent.putExtra("exception_message", exceptionMessage)
        intent.putExtra("thread", threadName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        exitProcess(10)
    }

    private companion object {
        // Kept from when this lived in GramophoneApplication, so crash logs stay greppable.
        const val TAG = "GramophoneApplication"
    }
}
