/*
 *     Copyright (C) 2024 nift4
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

package org.akanework.gramophone.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.LifecycleCallbackListImpl
import java.util.concurrent.ExecutionException

class MediaControllerViewModel(application: Application) : AndroidViewModel(application),
    DefaultLifecycleObserver, MediaBrowser.Listener {

    private val context: Application
        get() = getApplication()
    private val mainExecutor = ContextCompat.getMainExecutor(application)
    private var controllerLifecycle: LifecycleHost? = null
    private var controllerFuture: ListenableFuture<MediaBrowser>? = null
    private val customCommandListenersImpl = LifecycleCallbackListImpl<
                (MediaController, SessionCommand, Bundle) -> ListenableFuture<SessionResult>>()
    private val connectionListenersImpl =
        LifecycleCallbackListImpl<LifecycleCallbackListImpl.Disposable.(MediaBrowser, Lifecycle) -> Unit>()
    val customCommandListeners
        get() = customCommandListenersImpl.toBaseInterface()
    val connectionListeners
        get() = connectionListenersImpl.toBaseInterface()
    // Unlike connectionListeners, not tied to (and released with) the activity lifecycle.
    private val connected = MutableStateFlow<MediaBrowser?>(null)

    override fun onStart(owner: LifecycleOwner) {
        val sessionToken =
            SessionToken(context, ComponentName(context, GramophonePlaybackService::class.java))
        val lc = LifecycleHost()
        controllerLifecycle = lc
        controllerFuture =
            MediaBrowser
                .Builder(context, sessionToken)
                .setConnectionHints(Bundle().apply {
                    putBoolean("PrepareWhenReady", true)
                })
                .setListener(this)
                .buildAsync()
                .apply {
                    addListener(
                        {
                            if (isCancelled) return@addListener
                            val instance = try {
                                get()
                            } catch (e: ExecutionException) {
                                if (e.cause !is SecurityException)
                                    throw e
                                if (e.cause!!.message != "Session rejected the connection request.")
                                    throw e
                                Log.w(
                                    "MediaControllerViewMdel", "Session rejected the connection" +
                                            " request. Maybe controller.release() was called before" +
                                            " connecting was done?"
                                )
                                null
                            }
                            if (this == controllerFuture && instance == null) {
                                controllerFuture = null
                                controllerLifecycle = null
                            }
                            if (this != controllerFuture || instance == null) {
                                // If there is a race condition that would cause this controller
                                // to leak, which can happen, just make sure we don't leak.
                                lc.destroy()
                                instance?.release()
                            } else {
                                lc.lifecycleRegistry.currentState = Lifecycle.State.CREATED
                                connected.value = instance
                                connectionListenersImpl.dispatch { it(instance, lc.lifecycle) }
                            }
                        }, mainExecutor
                    )
                }
    }

    fun addControllerCallback(
        lifecycle: Lifecycle?,
        callback: LifecycleCallbackListImpl.Disposable.(MediaBrowser, Lifecycle) -> Unit
    ) {
        // TODO migrate this to kt flows or LiveData?
        val instance = get()
        var skip = false
        if (instance != null) {
            val ds = LifecycleCallbackListImpl.DisposableImpl()
            ds.callback(instance, controllerLifecycle!!.lifecycle)
            skip = ds.disposed
        }
        if (instance == null || !skip) {
            connectionListeners.addCallback(lifecycle, callback)
        }
    }

    fun addRecreationalPlayerListener(
        lifecycle: Lifecycle, listener: Player.Listener,
        then: (MediaBrowser) -> Unit
    ) {
        addControllerCallback(lifecycle) { controller, controllerLifecycle ->
            controller.registerLifecycleCallback(
                LifecycleIntersection(lifecycle, controllerLifecycle).lifecycle,
                listener
            )
            then(controller)
        }
    }

    /**
     * Suspends until the controller is connected (possibly after the activity is recreated or
     * started again) and returns it. Use it right away: it goes away when the activity stops.
     */
    suspend fun awaitController(): MediaBrowser = connected.filterNotNull().first()

    fun get(): MediaBrowser? {
        if (controllerFuture?.isDone == true && controllerFuture?.isCancelled == false) {
            return controllerFuture!!.get()
        }
        return null
    }

    override fun onDisconnected(controller: MediaController) {
        connected.value = null
        controllerLifecycle?.destroy()
        controllerLifecycle = null
        controllerFuture = null
    }

    // TODO reconsider whether onStop is a good place, as getting stopped is quite easy and
    //  predictive back makes it obvious that we are reconnecting
    override fun onStop(owner: LifecycleOwner) {
        connected.value = null
        if (controllerFuture?.isDone == true) {
            if (controllerFuture?.isCancelled == false) {
                controllerFuture?.get()?.release()
            } else {
                throw IllegalStateException("controllerFuture?.isCancelled != false")
            }
        } else {
            controllerFuture?.cancel(true)
            controllerLifecycle?.destroy()
            controllerLifecycle = null
            controllerFuture = null
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        customCommandListenersImpl.release()
        connectionListenersImpl.release()
    }

    override fun onCustomCommand(
        controller: MediaController,
        command: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        var future: ListenableFuture<SessionResult>? = null
        val listenerIterator = customCommandListenersImpl.iterator()
        while (listenerIterator.hasNext() && (future == null || (future.isDone &&
                    future.get().resultCode == SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        ) {
            future = listenerIterator.next()(controller, command, args)
        }
        return future ?: super.onCustomCommand(controller, command, args)
    }

    private class LifecycleHost : LifecycleOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle
            get() = lifecycleRegistry

        fun destroy() {
            // you cannot set DESTROYED before setting CREATED
            // this would leak observers if the LifecycleHost is exposed to clients before ON_CREATE
            // but it's not and that is apparently what google wanted to achieve with this check
            if (lifecycle.currentState != Lifecycle.State.INITIALIZED)
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    class LifecycleIntersection(
        private val lifecycleOne: Lifecycle,
        private val lifecycleTwo: Lifecycle
    ) : LifecycleOwner, LifecycleEventObserver {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle
            get() = lifecycleRegistry

        init {
            lifecycleRegistry.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    lifecycleOne.removeObserver(this@LifecycleIntersection)
                    lifecycleTwo.removeObserver(this@LifecycleIntersection)
                }
            })
            lifecycleOne.addObserver(this)
            lifecycleTwo.addObserver(this)
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (lifecycleOne.currentState == Lifecycle.State.DESTROYED ||
                lifecycleTwo.currentState == Lifecycle.State.DESTROYED
            ) {
                // you cannot set DESTROYED before setting CREATED
                if (lifecycle.currentState == Lifecycle.State.INITIALIZED)
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                return
            }
            val target = lifecycleOne.currentState.coerceAtMost(lifecycleTwo.currentState)
            if (target == lifecycleRegistry.currentState) return
            lifecycleRegistry.currentState = target
        }
    }
}

fun Player.registerLifecycleCallback(lifecycle: Lifecycle, callback: Player.Listener) {
    addListener(callback)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            removeListener(callback)
        }
    })
}
