package org.akanework.gramophone.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import org.akanework.gramophone.logic.GramophoneApplication
import org.akanework.gramophone.logic.MediaPlayerPlaybackService
import org.akanework.gramophone.logic.utils.LifecycleCallbackListImpl

// Compatibility types to replace Media3
typealias Player = MediaPlayerWrapper
typealias SessionCommand = String
typealias SessionResult = Bundle

interface MediaPlayerListener {
    fun onPlaybackStateChanged(state: Int) {}
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    fun onPositionDiscontinuity() {}
    fun onMediaItemTransition() {}
}

class MediaPlayerWrapper(private val service: MediaPlayerPlaybackService) {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1  
        const val REPEAT_MODE_ALL = 2
        const val STATE_IDLE = 1
        const val STATE_READY = 2
        const val STATE_BUFFERING = 3
        const val STATE_ENDED = 4
    }
    
    private val listeners = mutableListOf<MediaPlayerListener>()
    
    val repeatMode: Int get() = service.getRepeatMode()
    val shuffleModeEnabled: Boolean get() = service.getShuffleMode()
    val playbackState: Int get() = service.getPlaybackState()
    val isPlaying: Boolean get() = service.isPlaying()
    val currentPosition: Long get() = service.getCurrentPosition()
    val duration: Long get() = service.getDuration()
    
    fun play() = service.play()
    fun pause() = service.pause()
    fun seekTo(position: Long) = service.seekTo(position)
    fun setRepeatMode(mode: Int) = service.setRepeatMode(mode)
    fun setShuffleModeEnabled(enabled: Boolean) = service.setShuffleMode(enabled)
    
    fun addListener(listener: MediaPlayerListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }
}

class MediaBrowserWrapper(private val service: MediaPlayerPlaybackService) {
    interface Listener {
        fun onConnected() {}
        fun onDisconnected() {}
    }
}

class MediaControllerViewModel(application: Application) : AndroidViewModel(application),
    DefaultLifecycleObserver, ServiceConnection {

    private val context: GramophoneApplication
        get() = getApplication()
    private var controllerLifecycle: LifecycleHost? = null
    private var mediaService: MediaPlayerPlaybackService? = null
    private var mediaBrowser: MediaBrowserWrapper? = null
    private var mediaPlayer: MediaPlayerWrapper? = null
    private val customCommandListenersImpl = LifecycleCallbackListImpl<
                (MediaPlayerWrapper, SessionCommand, Bundle) -> ListenableFuture<SessionResult>>()
    private val connectionListenersImpl =
        LifecycleCallbackListImpl<LifecycleCallbackListImpl.Disposable.(MediaBrowserWrapper, Lifecycle) -> Unit>()
    val customCommandListeners
        get() = customCommandListenersImpl.toBaseInterface()
    val connectionListeners
        get() = connectionListenersImpl.toBaseInterface()

    override fun onStart(owner: LifecycleOwner) {
        val intent = Intent(context, MediaPlayerPlaybackService::class.java)
        context.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val localBinder = binder as? MediaPlayerPlaybackService.LocalBinder
        mediaService = localBinder?.getService()
        mediaService?.let { service ->
            mediaBrowser = MediaBrowserWrapper(service)
            mediaPlayer = MediaPlayerWrapper(service)
            val lc = LifecycleHost()
            controllerLifecycle = lc
            connectionListenersImpl.callListeners { mediaBrowser, lc.lifecycle ->
                this(mediaBrowser!!, lc.lifecycle)
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        mediaService = null
        mediaBrowser = null
        mediaPlayer = null
        controllerLifecycle = null
    }

    fun addControllerCallback(
        lifecycle: Lifecycle?,
        callback: LifecycleCallbackListImpl.Disposable.(MediaBrowserWrapper, Lifecycle) -> Unit
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

    fun addRecreationalPlayerListener(lifecycle: Lifecycle, callback: (MediaPlayerWrapper) -> MediaPlayerListener) {
        addControllerCallback(lifecycle) { controller, controllerLifecycle ->
            val listener = callback(mediaPlayer!!)
            mediaPlayer?.addListener(listener)
            LifecycleIntersection(lifecycle, controllerLifecycle).lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    mediaPlayer?.removeListener(listener)
                }
            })
        }
    }

    fun get(): MediaBrowserWrapper? {
        return mediaBrowser
    }

    override fun onStop(owner: LifecycleOwner) {
        context.unbindService(this)
        mediaService = null
        mediaBrowser = null
        mediaPlayer = null
        controllerLifecycle?.destroy()
        controllerLifecycle = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        customCommandListenersImpl.release()
        connectionListenersImpl.release()
    }

    fun onCustomCommand(
        controller: MediaPlayerWrapper,
        command: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        var future: ListenableFuture<SessionResult>? = null
        val listenerIterator = customCommandListenersImpl.iterator()
        while (listenerIterator.hasNext() && (future == null || (future.isDone &&
                    future.get().getInt("resultCode") == -1))
        ) {
            future = listenerIterator.next()(controller, command, args)
        }
        return future ?: com.google.common.util.concurrent.Futures.immediateFuture(Bundle().apply {
            putInt("resultCode", -1)
        })
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

interface MediaPlayerListener {
    fun onPlaybackStateChanged(state: Int) {}
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    fun onPositionDiscontinuity() {}
    fun onMediaItemTransition() {}
}

class MediaPlayerWrapper(private val service: MediaPlayerPlaybackService) {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1  
        const val REPEAT_MODE_ALL = 2
        const val STATE_IDLE = 1
        const val STATE_READY = 2
        const val STATE_BUFFERING = 3
        const val STATE_ENDED = 4
    }
    
    private val listeners = mutableListOf<MediaPlayerListener>()
    
    val repeatMode: Int get() = service.getRepeatMode()
    val shuffleModeEnabled: Boolean get() = service.getShuffleMode()
    val playbackState: Int get() = service.getPlaybackState()
    val isPlaying: Boolean get() = service.isPlaying()
    val currentPosition: Long get() = service.getCurrentPosition()
    val duration: Long get() = service.getDuration()
    
    fun play() = service.play()
    fun pause() = service.pause()
    fun seekTo(position: Long) = service.seekTo(position)
    fun setRepeatMode(mode: Int) = service.setRepeatMode(mode)
    fun setShuffleModeEnabled(enabled: Boolean) = service.setShuffleMode(enabled)
    
    fun addListener(listener: MediaPlayerListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }

fun MediaPlayerWrapper.registerLifecycleCallback(lifecycle: Lifecycle, callback: MediaPlayerListener) {
    addListener(callback)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            removeListener(callback)
        }
    })
}
