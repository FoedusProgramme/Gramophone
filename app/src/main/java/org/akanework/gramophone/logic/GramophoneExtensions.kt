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

package org.akanework.gramophone.logic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.StrictMode
import android.os.ext.SdkExtensions
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.net.toFile
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import kotlinx.coroutines.flow.MutableSharedFlow
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_GET_AUDIO_FORMAT
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_GET_LYRICS
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_DEL
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_ENQUEUE
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_GET_INACTIVE
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_GET_QUEUE
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_LOAD_QUEUE
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_PIN_QUEUE
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_REORDER
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_UNPIN_QUEUE
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QUERY_TIMER
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_SET_TIMER
import org.akanework.gramophone.logic.utils.AfFormatInfo
import org.akanework.gramophone.logic.utils.AudioFormatDetector
import org.akanework.gramophone.logic.utils.AudioTrackInfo
import org.akanework.gramophone.logic.utils.BtCodecInfo
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.ReplayGainUtil
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.MainActivity
import org.jetbrains.annotations.Contract
import java.io.File
import java.io.FileInputStream
import java.util.LinkedList
import java.util.Locale
import kotlin.math.max

fun Player.playOrPause() {
    if (playWhenReady) {
        if (playbackState == Player.STATE_ENDED)
            seekToDefaultPosition()
        else
            pause()
    } else {
        play()
    }
}

fun MediaItem.getUri(): Uri? {
    return localConfiguration?.uri
}

fun MediaItem.getFile(): File? {
    return getUri()?.toFile()
}

fun String.toMediaStoreId(): Long? {
    return if (startsWith("MediaStore:"))
        substring("MediaStore:".length).toLongOrNull()
    else null
}

fun MediaItem.requireMediaStoreId(): Long {
    return mediaId.toMediaStoreId()
        ?: throw IllegalArgumentException("Media item with ID $mediaId doesn't appear to be media store item")
}

fun MediaItem.getBitrate(): Int? {
    val retriever = MediaMetadataRetriever()
    val file = getFile() ?: return null
    var fd: FileInputStream? = null
    return try {
        fd = file.inputStream()
        // uses this slightly less straight-forward overload to avoid a resource leak in platform
        retriever.setDataSource(fd.fd)
        fd.close()
        fd = null
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull()
    } catch (e: Exception) {
        Log.w("MediaItem", "getBitrate failed", e)
        null
    } finally {
        fd?.close()
        retriever.release()
    }
}

fun Activity.closeKeyboard(view: View) {
    if (ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    ) {
        WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.ime())
    }
}

fun Activity.showKeyboard(view: View) {
    view.requestFocus()
    if (ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == false
    ) {
        WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.ime())
    }
}

fun Drawable.startAnimation() {
    when (this) {
        is AnimatedVectorDrawable -> start()
        is AnimatedVectorDrawableCompat -> start()
        else -> throw IllegalArgumentException()
    }
}

fun <T> MutableSharedFlow<T>.emitOrDie(value: T) {
    if (!tryEmit(value))
        throw IllegalStateException("tryEmit should have succeeded")
}

fun TextView.setTextAnimation(
    text: CharSequence,
    duration: Long = 300,
    completion: (() -> Unit)? = null,
    skipAnimation: Boolean = false
) {
    val oldTargetText = (getTag(androidx.core.R.id.text) as String?)
    if (oldTargetText == text)
        return // effectively, correct text is/will be set soon.
    // if still fading out, just replace target text. otherwise set target for new anim.
    setTag(androidx.core.R.id.text, if (skipAnimation) null else text)
    if (skipAnimation) {
        (getTag(R.id.fade_in_animation) as ViewPropertyAnimator?)?.cancel()
        (getTag(R.id.fade_out_animation) as ViewPropertyAnimator?)?.cancel()
        this.text = text
        this.alpha = 1f
        this.visibility = View.VISIBLE
        completion?.let { it() }
    } else if (this.text != text) {
        fadOutAnimation(duration) {
            this.text = (getTag(androidx.core.R.id.text) as String?)
            setTag(androidx.core.R.id.text, null)
            fadInAnimation(duration) {
                completion?.let {
                    it()
                }
            }
        }
    } else {
        completion?.let { it() }
    }
}

// ViewExtensions

fun View.fadOutAnimation(
    duration: Long = 300,
    visibility: Int = View.INVISIBLE,
    completion: (() -> Unit)? = null
) {
    if (this.visibility != View.VISIBLE) {
        this.visibility = visibility
        completion?.let {
            it()
        }
        return
    }
    (getTag(R.id.fade_in_animation) as ViewPropertyAnimator?)?.cancel()
    (getTag(R.id.fade_out_animation) as ViewPropertyAnimator?)?.cancel()
    setTag(
        R.id.fade_out_animation, animate()
            .alpha(0f)
            .setDuration(CalculationUtils.lerp(0f, duration.toFloat(), this.alpha).toLong())
            .withEndAction {
                this.visibility = visibility
                setTag(R.id.fade_out_animation, null)
                completion?.let {
                    it()
                }
            })
}

fun View.fadInAnimation(duration: Long = 300, completion: (() -> Unit)? = null) {
    (getTag(R.id.fade_in_animation) as ViewPropertyAnimator?)?.cancel()
    (getTag(R.id.fade_out_animation) as ViewPropertyAnimator?)?.cancel()
    alpha = 0f
    visibility = View.VISIBLE
    setTag(
        R.id.fade_in_animation, animate()
            .alpha(1f)
            .setDuration(CalculationUtils.lerp(duration.toFloat(), 0f, this.alpha).toLong())
            .withEndAction {
                setTag(R.id.fade_in_animation, null)
                completion?.let {
                    it()
                }
            })
}

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toLocaleString() = String.format(Locale.getDefault(), "%d", this)

@Suppress("NOTHING_TO_INLINE")
inline fun Int.dpToPx(context: Context): Int =
    (this.toFloat() * context.resources.displayMetrics.density).toInt()

@Suppress("NOTHING_TO_INLINE")
inline fun Float.dpToPx(context: Context): Float =
    (this * context.resources.displayMetrics.density)

fun MediaController.getTimer(): Pair<Int?, Boolean> =
    sendCustomCommand(
        SessionCommand(SERVICE_QUERY_TIMER, Bundle.EMPTY),
        Bundle.EMPTY
    ).get().extras.run {
        (if (containsKey("duration"))
            getInt("duration")
        else null) to (if (containsKey("pauseOnEnd"))
            getBoolean("pauseOnEnd")
        else throw IllegalArgumentException("expected pauseOnEnd to be set"))
    }

fun MediaController.setTimer(value: Int, waitUntilSongEnd: Boolean) {
    sendCustomCommand(
        SessionCommand(SERVICE_SET_TIMER, Bundle.EMPTY).apply {
            customExtras.putInt("duration", value)
            customExtras.putBoolean("pauseOnEnd", waitUntilSongEnd)
        }, Bundle.EMPTY
    )
}

inline fun <reified T> MutableList<T>.forEachSupport(skipFirst: Int = 0, operator: (T) -> Unit) {
    val li = listIterator()
    var skip = skipFirst
    while (skip-- > 0) {
        li.next()
    }
    while (li.hasNext()) {
        operator(li.next())
    }
}

inline fun <reified T> MutableList<T>.replaceAllSupport(skipFirst: Int = 0, operator: (T) -> T) {
    val li = listIterator()
    var skip = skipFirst
    while (skip-- > 0) {
        li.next()
    }
    while (li.hasNext()) {
        li.set(operator(li.next()))
    }
}

@Suppress("UNCHECKED_CAST")
fun MediaController.getLyrics(): SemanticLyrics? =
    sendCustomCommand(
        SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
        Bundle.EMPTY
    ).get().extras.let {
        BundleCompat.getParcelable(it, "lyrics", SemanticLyrics::class.java)
    }

fun MediaController.getAudioFormat(): AudioFormatDetector.AudioFormats =
    sendCustomCommand(
        SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
        Bundle.EMPTY
    ).get().extras.let {
        AudioFormatDetector.AudioFormats(
            BundleCompat.getParcelableArrayList(
                it, "file_format",
                Bundle::class.java
            )?.let { bundles ->
                bundles.map { bundle ->
                    bundle.getInt("type", C.TRACK_TYPE_UNKNOWN) to
                            (Format.fromBundle(bundle.getBundle("format")!!)
                                    to ReplayGainUtil.ReplayGainInfo.fromBundle(bundle.getBundle("rg")!!))
                }
            },
            it.getBundle("sink_format")?.let { bundle -> Format.fromBundle(bundle) },
            BundleCompat.getParcelable(it, "track_format", AudioTrackInfo::class.java),
            BundleCompat.getParcelable(it, "hal_format", AfFormatInfo::class.java),
            BundleCompat.getParcelable(it, "bt", BtCodecInfo::class.java)
        )
    }

fun MediaController.getInactiveQueues(): List<MultiQueueObject> =
    sendCustomCommand(
        SessionCommand(SERVICE_QB_GET_INACTIVE, Bundle.EMPTY),
        Bundle.EMPTY
    ).get().extras.run {
        val binder = getBinder("allQueues")!!
        BundleListRetriever.getList(binder).map {
            MultiQueueObject.fromBundle(it)
        }
    }

fun MediaController.getQueue(index: Int = C.INDEX_UNSET): MultiQueueObject? =
    sendCustomCommand(
        SessionCommand(SERVICE_QB_GET_QUEUE, Bundle.EMPTY).apply {
            customExtras.putInt("index", index)
        }, Bundle.EMPTY
    ).get().extras.run {
        val binder = getBinder("allQueues")!!
        BundleListRetriever.getList(binder).map {
            MultiQueueObject.fromBundle(it)
        }.firstOrNull()
    }


fun shuffledItems(
    items: List<MediaItem>,
    order: ShuffleOrder
): List<MediaItem> {
    val result = mutableListOf<MediaItem>()

    var i = order.firstIndex
    while (i != C.INDEX_UNSET) {
        result.add(items[i])
        i = order.getNextIndex(i)
    }

    return result
}

fun shuffledIndices(order: ShuffleOrder): MutableList<Int> {
    val result = mutableListOf<Int>()

    var i = order.firstIndex
    while (i != C.INDEX_UNSET) {
        result.add(i)
        i = order.getNextIndex(i)
    }

    return result
}

fun MediaController.getQueueForUi(index: Int = C.INDEX_UNSET): Pair<MutableList<Int>, MutableList<MediaItem>>? {
    if (index == -1) {
        return null
    }
    return sendCustomCommand(
        SessionCommand(SERVICE_QB_GET_QUEUE, Bundle.EMPTY).apply {
            customExtras.putInt("index", index)
        }, Bundle.EMPTY
    ).get().extras.run {
        val binder = getBinder("allQueues")!!
        BundleListRetriever.getList(binder).map {
            val mq = MultiQueueObject.fromBundle(it)
            val items = mq.queue
            val indexes: MutableList<Int> = if (mq.shuffleOrder == null) {
                (0 until mq.getSize()).toMutableList()
            } else {
                shuffledIndices(mq.shuffleOrder!!)
            }

            Pair(indexes, items)
        }.firstOrNull()
    }
}

fun MediaController.loadQueue(index: Int) {
    sendCustomCommand(
        SessionCommand(SERVICE_QB_LOAD_QUEUE, Bundle.EMPTY).apply {
            customExtras.putInt("index", index)
        }, Bundle.EMPTY
    )
}

fun MediaController.pinQueue(index: Int) {
    sendCustomCommand(
        SessionCommand(SERVICE_QB_PIN_QUEUE, Bundle.EMPTY).apply {
            customExtras.putInt("index", index)
        }, Bundle.EMPTY
    )
}


fun MediaController.unQueue(index: Int) {
    sendCustomCommand(
        SessionCommand(SERVICE_QB_UNPIN_QUEUE, Bundle.EMPTY).apply {
            customExtras.putInt("index", index)
        }, Bundle.EMPTY
    )
}


fun MediaController.deleteQueue(index: Int): Boolean =
    sendCustomCommand(
        SessionCommand(SERVICE_QB_DEL, Bundle.EMPTY).apply {
            customExtras.putInt("index", index)
        }, Bundle.EMPTY
    ).get().extras.run {
        if (containsKey("status"))
            getBoolean("status")
        else throw IllegalArgumentException("expected status to be set")
    }

fun MediaController.reorderQueue(from: Int, to: Int): Boolean =
    sendCustomCommand(
        SessionCommand(SERVICE_QB_REORDER, Bundle.EMPTY).apply {
            customExtras.putInt("from", from)
            customExtras.putInt("to", to)
        }, Bundle.EMPTY
    ).get().extras.run {
        if (containsKey("status"))
            getBoolean("status")
        else throw IllegalArgumentException("expected status to be set")
    }

// TODO: shuffle and repeat mode
fun MediaController.playQueue(
    title: String?,
    mediaList: List<MediaItem>,
    mediaItemIndex: Int,
    isOriginal: Boolean
) {
    sendCustomCommand(
        SessionCommand(SERVICE_QB_ENQUEUE, Bundle.EMPTY).apply {
            customExtras.putString("title", title)
            customExtras.putInt("mediaItemIndex", mediaItemIndex)
            customExtras.putBoolean("isOriginal", isOriginal)
            val binder = BundleListRetriever(mediaList.map { it.toBundleIncludeLocalConfiguration() })
            customExtras.putBinder("mediaList", binder)
        }, Bundle.EMPTY
    )
}

fun Tracks.getFirstSelectedTrackFormatByType(type: @C.TrackType Int): Format? {
    for (i in groups) {
        if (i.type == type) {
            for (j in 0..<i.length) {
                if (i.isTrackSelected(j)) {
                    return i.getTrackFormat(j)
                }
            }
        }
    }
    return null
}

// https://twitter.com/Piwai/status/1529510076196630528
fun Handler.postAtFrontOfQueueAsync(callback: Runnable) {
    sendMessageAtFrontOfQueue(Message.obtain(this, callback).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            isAsynchronous = true
        }
    })
}

fun View.enableEdgeToEdgePaddingListener(
    ime: Boolean = false, top: Boolean = false,
    extra: ((Insets) -> Unit)? = null
) {
    if (fitsSystemWindows) throw IllegalArgumentException("must have fitsSystemWindows disabled")
    if (this is AppBarLayout) {
        if (ime) throw IllegalArgumentException("AppBarLayout must have ime flag disabled")
        // AppBarLayout fitsSystemWindows does not handle left/right for a good reason, it has
        // to be applied to children to look good; we rewrite fitsSystemWindows in a way mostly specific
        // to Gramophone to support shortEdges displayCutout
        val collapsingToolbarLayout =
            children.find { it is CollapsingToolbarLayout } as CollapsingToolbarLayout?
        collapsingToolbarLayout?.let {
            // The CollapsingToolbarLayout mustn't consume insets, we handle padding here anyway
            ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets -> insets }
        }
        val expandedTitleMarginStart = collapsingToolbarLayout?.expandedTitleMarginStart
        val expandedTitleMarginEnd = collapsingToolbarLayout?.expandedTitleMarginEnd
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val cutoutAndBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            (v as AppBarLayout).children.forEach {
                if (it is CollapsingToolbarLayout) {
                    val es = expandedTitleMarginStart!! + if (it.layoutDirection
                        == View.LAYOUT_DIRECTION_LTR
                    ) cutoutAndBars.left else cutoutAndBars.right
                    if (es != it.expandedTitleMarginStart) it.expandedTitleMarginStart = es
                    val ee = expandedTitleMarginEnd!! + if (it.layoutDirection
                        == View.LAYOUT_DIRECTION_RTL
                    ) cutoutAndBars.left else cutoutAndBars.right
                    if (ee != it.expandedTitleMarginEnd) it.expandedTitleMarginEnd = ee
                }
                it.setPadding(cutoutAndBars.left, 0, cutoutAndBars.right, 0)
            }
            v.setPadding(0, cutoutAndBars.top, 0, 0)
            val i = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            extra?.invoke(cutoutAndBars)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(cutoutAndBars.left, 0, cutoutAndBars.right, cutoutAndBars.bottom)
                )
                .setInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(i.left, 0, i.right, i.bottom)
                )
                .build()
        }
    } else {
        val pl = paddingLeft
        val pt = paddingTop
        val pr = paddingRight
        val pb = paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val mask = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    if (ime) WindowInsetsCompat.Type.ime() else 0
            val i = insets.getInsets(mask)
            val pbsp = (context as? MainActivity)?.playerBottomSheet?.getBottomPadding() ?: 0
            v.setPadding(
                pl + i.left, pt + (if (top) i.top else 0), pr + i.right,
                pb + max(i.bottom, pbsp)
            )
            extra?.invoke(i)
            return@setOnApplyWindowInsetsListener insets
        }
    }
}

data class Margin(var left: Int, var top: Int, var right: Int, var bottom: Int) {
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        internal inline fun fromLayoutParams(marginLayoutParams: MarginLayoutParams): Margin {
            return Margin(
                marginLayoutParams.leftMargin, marginLayoutParams.topMargin,
                marginLayoutParams.rightMargin, marginLayoutParams.bottomMargin
            )
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun apply(marginLayoutParams: MarginLayoutParams) {
        marginLayoutParams.updateMargins(left, top, right, bottom)
    }
}

fun View.updateMargin(
    block: Margin.() -> Unit
) {
    val oldMargin = Margin.fromLayoutParams(layoutParams as MarginLayoutParams)
    val newMargin = oldMargin.copy().also { it.block() }
    if (oldMargin != newMargin) {
        updateLayoutParams<MarginLayoutParams> {
            newMargin.apply(this)
        }
    }
}

// enableEdgeToEdge() without enforcing contrast, magic based on androidx EdgeToEdge.kt
fun ComponentActivity.enableEdgeToEdgeProperly() {
    if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    ) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
    } else {
        val darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, darkScrim))
    }
}

@SuppressLint("DiscouragedPrivateApi")
private fun WindowInsets.unconsumeIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        // Api21Impl of getRootWindowInsets returns already-consumed WindowInsets with correct data
        // Said consumed insets cannot be dispatched again because well, they are already consumed
        // Workaround this using some reflection (Api23Impl+ are not affected so this is safe)
        val mSystemWindowInsetsConsumed = WindowInsets::class.java
            .getDeclaredField("mSystemWindowInsetsConsumed")
            .apply { isAccessible = true }
        val mWindowDecorInsetsConsumed = WindowInsets::class.java
            .getDeclaredField("mWindowDecorInsetsConsumed")
            .apply { isAccessible = true }
        val mStableInsetsConsumed = WindowInsets::class.java
            .getDeclaredField("mStableInsetsConsumed")
            .apply { isAccessible = true }
        mSystemWindowInsetsConsumed.set(this, false)
        mWindowDecorInsetsConsumed.set(this, false)
        mStableInsetsConsumed.set(this, false)
    }
}

// Pitfall: WindowInsetsCompat.Builder(insets) mutates the platform insets
fun WindowInsetsCompat.clone(): WindowInsetsCompat =
    WindowInsetsCompat.toWindowInsetsCompat(WindowInsets(toWindowInsets()).also {
        it.unconsumeIfNeeded()
    })

val Context.gramophoneApplication
    get() = this.applicationContext as GramophoneApplication

/*
fun AppWidgetManager.createWidgetInSizes(appWidgetId: Int, creator: (SizeF?) -> RemoteViews): RemoteViews {
    val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BundleCompat.getParcelableArrayList<SizeF>(
            getAppWidgetOptions(appWidgetId),
            AppWidgetManager.OPTION_APPWIDGET_SIZES,
            SizeF::class.java
        ).let { if (it.isNullOrEmpty()) null else it }
    } else {
        null
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !sizes.isNullOrEmpty()) {
        RemoteViews(sizes.associateWith(creator))
    } else creator(null)
}
*/

// the whole point of this function is to do literally nothing at all (but without impacting
// performance) in release builds and ignore StrictMode violations in debug builds
inline fun <reified T> allowDiskAccessInStrictMode(doIt: () -> T): T {
    return if (BuildConfig.DEBUG) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw IllegalStateException("allowDiskAccessInStrictMode() on wrong thread")
        } else {
            val policy = StrictMode.allowThreadDiskReads()
            try {
                StrictMode.allowThreadDiskWrites()
                doIt()
            } finally {
                StrictMode.setThreadPolicy(policy)
            }
        }
    } else doIt()
}

inline fun <reified T> SharedPreferences.use(
    doIt: SharedPreferences.() -> T
): T {
    return allowDiskAccessInStrictMode { doIt() }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Context.hasAudioPermission() =
    hasScopedStorageWithMediaTypes() && ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_MEDIA_AUDIO
    ) == PackageManager.PERMISSION_GRANTED ||
            (!hasScopedStorageV2() && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED) ||
            (!hasScopedStorageWithMediaTypes() && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED)

// use below functions if accessing from UI thread only
@Suppress("NOTHING_TO_INLINE")
@Contract(value = "_,!null->!null")
inline fun SharedPreferences.getStringStrict(key: String, defValue: String?): String? {
    return use { getString(key, defValue) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getIntStrict(key: String, defValue: Int): Int {
    return use { getInt(key, defValue) }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getBooleanStrict(key: String, defValue: Boolean): Boolean {
    return use { getBoolean(key, defValue) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.hasImagePermission() =
    checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.hasNotificationPermission() =
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

@Suppress("NOTHING_TO_INLINE")
inline fun needsMissingOnDestroyCallWorkarounds(): Boolean =
    Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE

@Suppress("NOTHING_TO_INLINE")
inline fun needsManualSnackBarInset(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

@Suppress("NOTHING_TO_INLINE")
inline fun hasOsClipboardDialog(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Suppress("NOTHING_TO_INLINE")
inline fun supportsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Suppress("NOTHING_TO_INLINE")
inline fun hasImprovedMediaStore(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

@Suppress("NOTHING_TO_INLINE")
inline fun hasMarkIsFavouriteStatus(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 16)

@Suppress("NOTHING_TO_INLINE")
inline fun hasScopedStorageV2(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

@Suppress("NOTHING_TO_INLINE")
inline fun hasScopedStorageV1(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

@Suppress("NOTHING_TO_INLINE")
inline fun hasScopedStorageWithMediaTypes(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Suppress("NOTHING_TO_INLINE")
inline fun mayThrowForegroundServiceStartNotAllowed(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2

@Suppress("NOTHING_TO_INLINE")
inline fun mayThrowForegroundServiceStartNotAllowedMiui(): Boolean =
    Build.MANUFACTURER.lowercase() == "xiaomi" &&
            Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU

operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = this.calculateStartPadding(LayoutDirection.Ltr) +
            other.calculateStartPadding(LayoutDirection.Ltr),
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    end = this.calculateEndPadding(LayoutDirection.Ltr) +
            other.calculateEndPadding(LayoutDirection.Ltr),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding(),
)