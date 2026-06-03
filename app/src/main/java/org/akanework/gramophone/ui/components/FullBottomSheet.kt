package org.akanework.gramophone.ui.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.core.graphics.TypefaceCompat
import androidx.core.os.BundleCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.postOnAnimationDelayed
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowConversionToBitmap
import coil3.request.allowHardware
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.fadInAnimation
import org.akanework.gramophone.logic.fadOutAnimation
import org.akanework.gramophone.logic.getAudioFormat
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getIntStrict
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTextAnimation
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.AudioFormatDetector
import org.akanework.gramophone.logic.utils.AudioFormatDetector.AudioFormatInfo
import org.akanework.gramophone.logic.utils.AudioFormatDetector.AudioQuality
import org.akanework.gramophone.logic.utils.AudioFormatDetector.SpatialFormat
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.logic.utils.ColorUtils
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class FullBottomSheet
    (context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener, MaterialButton.OnCheckedChangeListener {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val activity
        get() = context as MainActivity
    private val instance: MediaBrowser?
        get() = activity.getPlayer()
    var minimize: (() -> Unit)? = null

    private val viewModel by activity.viewModels<MyViewModel>()
    private var wrappedContext: Context? = null
    private var currentJob: CoroutineScope? = null
    private var currentDisposable: Disposable? = null
    private var isUserTracking = false
    private var runnableRunning = false
    private var firstTime = false
    private var enableQualityInfo = false

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private var currentFormat: AudioFormatDetector.AudioFormats? = null

    companion object {
        const val SLIDER_UPDATE_INTERVAL: Long = 100
        const val BACKGROUND_COLOR_TRANSITION_SEC: Long = 300
        const val FOREGROUND_COLOR_TRANSITION_SEC: Long = 150
        const val LYRIC_FADE_TRANSITION_SEC: Long = 125
        private const val TAG = "FullBottomSheet"
    }

    private val touchListener =
        object : SeekBar.OnSeekBarChangeListener, Slider.OnSliderTouchListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dest = instance?.mediaMetadata?.durationMs
                    if (dest != null) {
                        bottomSheetFullPosition.text =
                            CalculationUtils.convertDurationToTimeStamp((progress.toLong()))
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = true
                progressDrawable.animate = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val mediaId = instance?.currentMediaItem
                if (mediaId != null) {
                    if (seekBar != null) {
                        instance?.seekTo((seekBar.progress.toLong()))
                        bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
                    }
                }
                isUserTracking = false
                progressDrawable.animate =
                    instance?.isPlaying == true || instance?.playWhenReady == true
            }

            override fun onStartTrackingTouch(slider: Slider) {
                isUserTracking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val mediaId = instance?.currentMediaItem
                if (mediaId != null) {
                    instance?.seekTo((slider.value.toLong()))
                    bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
                }
                isUserTracking = false
            }
        }
    private val formatUpdateRunnable = Runnable {
        updateQualityIndicators(
            if (enableQualityInfo)
                AudioFormatDetector.detectAudioFormat(currentFormat) else null
        )
    }
    private val bottomSheetFullCover: TransformableImageView
    private val bottomSheetFullTitle: TextView
    private val bottomSheetFullSubtitle: TextView
    private val bottomSheetFullControllerButton: MaterialButton
    private val bottomSheetFullNextButton: MaterialButton
    private val bottomSheetFullPreviousButton: MaterialButton
    private val bottomSheetFullDuration: TextView
    private val bottomSheetFullPosition: TextView
    private var bottomSheetFullQualityDetails: TextView
    private val bottomSheetFullSlideUpButton: MaterialButton
    private val bottomSheetShuffleButton: MaterialButton
    private val bottomSheetLoopButton: MaterialButton
    private val bottomSheetPlaylistButton: MaterialButton
    private val bottomSheetTimerButton: MaterialButton
    private val bottomSheetPlaybackSpeedButton: MaterialButton
    private val bottomSheetFavoriteButton: MaterialButton
    val bottomSheetLyricButton: MaterialButton
    private val bottomSheetFullSeekBar: SeekBar
    private val bottomSheetFullSlider: Slider
    private val bottomSheetFullCoverFrame: MaterialCardView
    val bottomSheetFullLyricView: LyricsView
    private val progressDrawable: SquigglyProgress
    private var lastDisposable: Disposable? = null
    private var pqs: PlaylistQueueSheet? = null

    init {
        inflate(context, R.layout.full_player, this)
        bottomSheetFullCoverFrame = findViewById(R.id.album_cover_frame)
        bottomSheetFullCover = findViewById(R.id.full_sheet_cover)
        bottomSheetFullTitle = findViewById(R.id.full_song_name)
        bottomSheetFullSubtitle = findViewById(R.id.full_song_artist)
        bottomSheetFullPreviousButton = findViewById(R.id.sheet_previous_song)
        bottomSheetFullControllerButton = findViewById(R.id.sheet_mid_button)
        bottomSheetFullNextButton = findViewById(R.id.sheet_next_song)
        bottomSheetFullPosition = findViewById(R.id.position)
        bottomSheetFullDuration = findViewById(R.id.duration)
        bottomSheetFullSeekBar = findViewById(R.id.slider_squiggly)
        bottomSheetFullSlider = findViewById(R.id.slider_vert)
        bottomSheetFullSlideUpButton = findViewById(R.id.slide_down)
        bottomSheetShuffleButton = findViewById(R.id.sheet_random)
        bottomSheetLoopButton = findViewById(R.id.sheet_loop)
        bottomSheetTimerButton = findViewById(R.id.timer)
        bottomSheetPlaybackSpeedButton = findViewById(R.id.playback_speed)
        bottomSheetFavoriteButton = findViewById(R.id.favor)
        bottomSheetPlaylistButton = findViewById(R.id.playlist)
        bottomSheetLyricButton = findViewById(R.id.lyrics)
        bottomSheetFullLyricView = findViewById(R.id.lyric_frame)
        bottomSheetFullQualityDetails = findViewById(R.id.quality_details)
        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetFullLyricView) { v, insets ->
            val myInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updateMargin {
                left = -myInsets.left
                top = -myInsets.top
                right = -myInsets.right
                bottom = -myInsets.bottom
            }
            v.setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
                )
                .setInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
                )
                .build()
        }
        refreshSettings(null)
        prefs.registerOnSharedPreferenceChangeListener(this)
        activity.controllerViewModel.customCommandListeners.addCallback(activity.lifecycle) { _, command, _ ->
            when (command.customAction) {
                GramophonePlaybackService.SERVICE_TIMER_CHANGED -> updateTimer()

                GramophonePlaybackService.SERVICE_GET_LYRICS -> {
                    val parsedLyrics = instance?.getLyrics()
                    bottomSheetFullLyricView.updateLyrics(parsedLyrics)
                }

                GramophonePlaybackService.SERVICE_GET_AUDIO_FORMAT -> {
                    val format = instance?.getAudioFormat()
                    this.currentFormat = format
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                        !handler.hasCallbacks(formatUpdateRunnable)
                    ) {
                        // TODO: is 300ms long enough wait for stuff like bitrate? 100ms isn't.
                        handler.postDelayed(formatUpdateRunnable, 300)
                    }
                }

                else -> {
                    return@addCallback Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            }
            return@addCallback Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        val seekBarProgressWavelength =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_wavelength)
                .toFloat()
        val seekBarProgressAmplitude =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_amplitude)
                .toFloat()
        val seekBarProgressPhase =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_phase)
                .toFloat()
        val seekBarProgressStrokeWidth =
            context.resources
                .getDimensionPixelSize(R.dimen.media_seekbar_progress_stroke_width)
                .toFloat()

        bottomSheetFullSeekBar.progressDrawable = SquigglyProgress().also {
            progressDrawable = it
            it.waveLength = seekBarProgressWavelength
            it.lineAmplitude = seekBarProgressAmplitude
            it.phaseSpeed = seekBarProgressPhase
            it.strokeWidth = seekBarProgressStrokeWidth
            it.transitionEnabled = true
            it.animate = false
            it.setTint(
                MaterialColors.getColor(
                    bottomSheetFullSeekBar,
                    androidx.appcompat.R.attr.colorPrimary,
                )
            )
        }

        bottomSheetFullCover.setOnClickListener {
            activity.startFragment(DetailDialogFragment()) {
                putString("Id", instance?.currentMediaItem?.mediaId)
            }
        }

        bottomSheetFullTitle.setOnClickListener {
            minimize?.invoke()
            activity.startFragment(GeneralSubFragment()) {
                putString("Id", instance?.currentMediaItem?.mediaMetadata?.albumId?.toString())
                putInt("Item", R.id.album)
            }
        }

        if (Flags.FORMAT_INFO_DIALOG) {
            bottomSheetFullQualityDetails.setOnClickListener {
                MaterialAlertDialogBuilder(wrappedContext ?: context)
                    .setTitle(R.string.audio_signal_chain)
                    .setMessage(
                        currentFormat?.prettyToString(context)
                            ?: context.getString(R.string.audio_not_initialized)
                    )
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
        }

        bottomSheetFullSubtitle.setOnClickListener {
            minimize?.invoke()
            activity.startFragment(ArtistSubFragment()) {
                putString("Id", instance?.currentMediaItem?.mediaMetadata?.artistId?.toString())
                putInt("Item", R.id.artist)
            }
        }

        bottomSheetTimerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            val t = instance?.getTimer()
            val currentText = if (t?.first != null) context.getString(R.string.timer_expiry,
                DateFormat.getTimeFormat(context).format(System.currentTimeMillis() + t.first!!)
            ) else if (t?.second == true) context.getString(R.string.timer_expiry_end_of_this_song)
            else null
            if (currentText != null) {
                val dialog = MaterialAlertDialogBuilder(wrappedContext ?: context)
                    .setTitle(R.string.timer)
                    .setView(R.layout.dialog_sleep_timer_active)
                    .setNeutralButton(R.string.unset)  { _, _ ->
                        instance?.setTimer(0, false)
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
                dialog.findViewById<TextView>(R.id.textView)!!.text = currentText
                dialog.findViewById<CheckBox>(R.id.checkBox)!!.let {
                    if (t!!.first == null) {
                        it.visibility = GONE
                    } else {
                        it.isChecked = t.second
                        it.setOnCheckedChangeListener { _, value ->
                            val newTime = instance?.getTimer()
                            instance?.setTimer(newTime?.first ?: 0, value)
                        }
                    }
                }
            } else {
                val dialog = MaterialAlertDialogBuilder(wrappedContext ?: context)
                    .setTitle(R.string.timer)
                    .setView(R.layout.dialog_sleep_timer)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
                val lv = dialog.findViewById<ListView>(R.id.listView)!!
                val checkbox = dialog.findViewById<CheckBox>(R.id.checkBox)!!
                checkbox.isChecked = prefs.getBooleanStrict("lastTimerEos", false)
                val minutes = listOf(0, 1, 3, 5, 10, 15, 20, 30, 45, 60, 90)
                val items = minutes.map {
                    if (it > 0)
                        context.resources.getQuantityString(
                            R.plurals.minutes, it,
                            it
                        )
                    else
                        context.resources.getString(R.string.timer_end_of_this_song)
                } + context.resources.getString(R.string.other)
                lv.adapter = ArrayAdapter(
                    context, android.R.layout.simple_list_item_1,
                    items
                )
                lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    dialog.dismiss()
                    if (position == minutes.size) {
                        lateinit var et: EditText
                        lateinit var cb: CheckBox
                        // TODO find out why wrapped context does not work
                        val dialog2 = MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.timer)
                            .setView(R.layout.dialog_sleep_timer_custom)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                instance?.setTimer(((et.editableText?.toString()?.toFloat()
                                    ?: 0f) * 60f * 1000f).toInt(), cb.isChecked)
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show()
                        et = dialog2.findViewById(R.id.editText)!!
                        cb = dialog2.findViewById(R.id.checkBox)!!
                        cb.isChecked = checkbox.isChecked
                    } else {
                        val duration = minutes[position] * 60 * 1000
                        val eos = duration == 0 || checkbox.isChecked
                        instance?.setTimer(duration, eos)
                    }
                }
            }
        }

        bottomSheetLoopButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.repeatMode = when (instance?.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> throw IllegalStateException()
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            bottomSheetPlaybackSpeedButton.visibility = GONE
        }
        bottomSheetPlaybackSpeedButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            if (instance != null)
                showPlaybackSpeedDialog()
        }

        bottomSheetFavoriteButton.addOnCheckedChangeListener(this)

        bottomSheetPlaylistButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            if (instance != null)
                pqs = PlaylistQueueSheet(wrappedContext ?: context, activity).also { it.show() }
        }
        bottomSheetFullControllerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.playOrPause()
        }
        bottomSheetFullPreviousButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.seekToPrevious()
        }
        bottomSheetFullPreviousButton.setOnLongClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.LONG_PRESS)
            instance?.seekBack()
            true
        }
        bottomSheetFullNextButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.seekToNext()
        }
        bottomSheetFullNextButton.setOnLongClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.LONG_PRESS)
            instance?.seekForward()
            true
        }
        bottomSheetShuffleButton.addOnCheckedChangeListener { _, isChecked ->
            instance?.shuffleModeEnabled = isChecked
        }

        bottomSheetFullSlider.addOnChangeListener { _, value, isUser ->
            if (isUser) {
                val dest = instance?.mediaMetadata?.durationMs
                if (dest != null) {
                    bottomSheetFullPosition.text =
                        CalculationUtils.convertDurationToTimeStamp((value).toLong())
                }
            }
        }

        bottomSheetFullSeekBar.setOnSeekBarChangeListener(touchListener)
        bottomSheetFullSlider.addOnSliderTouchListener(touchListener)

        bottomSheetFullSlideUpButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            minimize?.invoke()
        }

        bottomSheetLyricButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            bottomSheetFullLyricView.fadInAnimation(LYRIC_FADE_TRANSITION_SEC)
        }

        bottomSheetShuffleButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
        }

        val colorPrimary =
            MaterialColors.getColor(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                -1
            )
        val colorSecondaryContainer =
            MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSecondaryContainer,
                -1
            )
        val colorSurface = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            -1
        )
        val backgroundProcessedColor = ColorUtils.getColor(
            colorSurface,
            ColorUtils.ColorType.COLOR_BACKGROUND_ELEVATED,
            context
        )
        val colorContrastFainted = ColorUtils.getColor(
            colorSecondaryContainer,
            ColorUtils.ColorType.COLOR_CONTRAST_FAINTED,
            context
        )
        setBackgroundColor(backgroundProcessedColor)
        bottomSheetFullSlider.trackInactiveTintList = ColorStateList.valueOf(colorContrastFainted)
        bottomSheetFullLyricView.updateTextColor(
            androidx.core.graphics.ColorUtils.compositeColors(
                androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 77),
                backgroundProcessedColor
            ),
            colorPrimary,
            androidx.core.graphics.ColorUtils.compositeColors(
                androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 200),
                backgroundProcessedColor
            ),
        )

        activity.controllerViewModel.addRecreationalPlayerListener(activity.lifecycle, this) {
            firstTime = true
            updateTimer()
            onRepeatModeChanged(instance?.repeatMode ?: Player.REPEAT_MODE_OFF)
            onShuffleModeEnabledChanged(instance?.shuffleModeEnabled == true)
            onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            onMediaMetadataChanged(instance?.mediaMetadata ?: MediaMetadata.EMPTY)
            firstTime = false
        }
        bottomSheetFullCover.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (oldRight - oldLeft != right - left || oldBottom - oldTop != bottom - top) {
                loadCoverForImageView()
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("Super", super.onSaveInstanceState())
            putBoolean("Lyrics", bottomSheetFullLyricView.isVisible)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        state as Bundle?
        if (state != null) {
            bottomSheetFullLyricView.isVisible = state.getBoolean("Lyrics")
            super.onRestoreInstanceState(BundleCompat.getParcelable(state, "Super", BaseSavedState::class.java))
        } else {
            super.onRestoreInstanceState(null)
        }
    }

    private fun updateTimer() {
        val t = instance?.getTimer()
        bottomSheetTimerButton.isChecked = t?.first != null || t?.second == true
        TooltipCompat.setTooltipText(
            bottomSheetTimerButton,
            if (t?.first != null) context.getString(
                if (t.second) R.string.timer_expiry_eos else R.string.timer_expiry,
                DateFormat.getTimeFormat(context).format(System.currentTimeMillis() + t.first!!)
            ) else if (t?.second == true) context.getString(R.string.timer_expiry_end_of_this_song)
            else context.getString(R.string.timer)
        )
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "color_accuracy" || key == "content_based_color") {
            if (DynamicColors.isDynamicColorAvailable() &&
                prefs.getBooleanStrict("content_based_color", true)
            ) {
                addColorScheme()
            } else {
                removeColorScheme()
            }
        } else {
            refreshSettings(key)
        }
    }

    private fun refreshSettings(key: String?) {
        if (key == null || key == "default_progress_bar") {
            if (prefs.getBooleanStrict("default_progress_bar", false)) {
                bottomSheetFullSlider.visibility = VISIBLE
                bottomSheetFullSeekBar.visibility = GONE
            } else {
                bottomSheetFullSlider.visibility = GONE
                bottomSheetFullSeekBar.visibility = VISIBLE
            }
        }
        if (key == null || key == "audio_quality_info") {
            enableQualityInfo = prefs.getBooleanStrict("audio_quality_info", false)
            updateQualityIndicators(
                if (enableQualityInfo)
                    AudioFormatDetector.detectAudioFormat(currentFormat) else null
            )
        }
        if (key == null || key == "centered_title") {
            if (prefs.getBooleanStrict("centered_title", false)) {
                bottomSheetFullTitle.gravity = Gravity.CENTER
                bottomSheetFullSubtitle.gravity = Gravity.CENTER
            } else {
                bottomSheetFullTitle.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
                bottomSheetFullSubtitle.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
            }
        }
        if (key == null || key == "bold_title") {
            if (prefs.getBooleanStrict("bold_title", true)) {
                bottomSheetFullTitle.typeface = TypefaceCompat.create(context, null, 600, false)
            } else {
                bottomSheetFullTitle.typeface = TypefaceCompat.create(context, null, 400, false)
            }
        }
        if (key == null || key == "album_round_corner") {
            bottomSheetFullCoverFrame.radius = prefs.getIntStrict(
                "album_round_corner",
                context.resources.getInteger(R.integer.round_corner_radius)
            ).dpToPx(context).toFloat()
        }
        if (key == null || key == "cookie_cover") {
            bottomSheetFullCover.setClip(prefs.getBooleanStrict("cookie_cover", false))
        }
    }

    private fun showPlaybackSpeedDialog() {
        val context = wrappedContext ?: context
        val initialPlaybackParameters = instance!!.playbackParameters
        val wantsToBeLocked = prefs.getBoolean("playback_tempo_pitch_locked", true)
        val isLocked =
            initialPlaybackParameters.pitch == initialPlaybackParameters.speed && wantsToBeLocked

        val tempoSlider = Slider(context).apply {
            valueFrom = 0.25f
            valueTo = 4.0f
            stepSize = 0.01f
            value = initialPlaybackParameters.speed.coerceIn(0.25f, 4.0f)
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { layoutParams = it }
        }

        val tempoText = TextView(context).apply {
            text = context.getString(
                R.string.tempo_pitch_value,
                context.getString(R.string.tempo),
                tempoSlider.value
            )
            gravity = Gravity.CENTER
            textSize = 16f
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { layoutParams = it }
        }

        val pitchSlider = Slider(context).apply {
            valueFrom = 0.25f
            valueTo = 4.0f
            stepSize = 0.01f
            value = initialPlaybackParameters.pitch.coerceIn(0.25f, 4.0f)
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { layoutParams = it }
        }

        val pitchText = TextView(context).apply {
            text = context.getString(
                R.string.tempo_pitch_value,
                context.getString(R.string.pitch),
                pitchSlider.value
            )
            gravity = Gravity.CENTER
            textSize = 16f
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { layoutParams = it }
        }

        val lockCheckbox = MaterialCheckBox(context).apply {
            text = context.getString(R.string.lock_tempo_pitch)
            isChecked = isLocked
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { layoutParams = it }
        }

        pitchSlider.isEnabled = !isLocked
        pitchText.isEnabled = !isLocked

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                48.dpToPx(context), 16.dpToPx(context),
                48.dpToPx(context), 0
            )
            addView(tempoText)
            addView(tempoSlider)
            addView(pitchText)
            addView(pitchSlider)
            addView(lockCheckbox)
        }

        tempoSlider.addOnChangeListener { _, value, fromUser ->
            tempoText.text = context.getString(
                R.string.tempo_pitch_value,
                context.getString(R.string.tempo),
                value
            )
            if (fromUser) {
                if (lockCheckbox.isChecked) {
                    pitchSlider.value = value
                }
                instance?.playbackParameters =
                    PlaybackParameters(tempoSlider.value, pitchSlider.value)
            }
        }

        pitchSlider.addOnChangeListener { _, value, fromUser ->
            pitchText.text = context.getString(
                R.string.tempo_pitch_value,
                context.getString(R.string.pitch),
                value
            )
            if (fromUser) {
                instance?.playbackParameters =
                    PlaybackParameters(tempoSlider.value, pitchSlider.value)
            }
        }

        lockCheckbox.setOnCheckedChangeListener { _, isChecked ->
            pitchSlider.isEnabled = !isChecked
            pitchText.isEnabled = !isChecked
            if (isChecked) {
                pitchSlider.value = tempoSlider.value
                instance?.playbackParameters =
                    PlaybackParameters(tempoSlider.value, pitchSlider.value)
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.playback_speed)
            .setView(NestedScrollView(context).apply { addView(container) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit {
                    putBoolean("playback_tempo_pitch_locked", lockCheckbox.isChecked)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                instance?.playbackParameters = initialPlaybackParameters
                if (wantsToBeLocked != isLocked) { // if external app changed speed/pitch.
                    prefs.edit {
                        putBoolean("playback_tempo_pitch_locked", false)
                    }
                }
            }
            .setNeutralButton(R.string.reset) { _, _ ->
                prefs.edit {
                    putBoolean("playback_tempo_pitch_locked", true)
                }
                instance?.playbackParameters = PlaybackParameters(1f, 1f)
            }
            .show()
    }

    fun onStop() {
        pqs?.dismiss()
        runnableRunning = false
    }

    override fun dispatchApplyWindowInsets(platformInsets: WindowInsets): WindowInsets {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(platformInsets)
        val myInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        setPadding(myInsets.left, myInsets.top, myInsets.right, myInsets.bottom)
        ViewCompat.dispatchApplyWindowInsets(bottomSheetFullLyricView, insets.clone())
        return WindowInsetsCompat.Builder(insets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
            )
            .setInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(), Insets.NONE
            )
            .build()
            .toWindowInsets()!!
    }

    private fun removeColorScheme() {
        currentJob?.cancel()
        currentDisposable?.dispose()
        currentDisposable = null
        wrappedContext = null
        currentJob = CoroutineScope(Dispatchers.Default)
        currentJob!!.launch {
            applyColorScheme()
        }
    }

    private fun addColorScheme() {
        currentJob?.cancel()
        currentDisposable?.dispose()
        currentDisposable = null
        val job = CoroutineScope(Dispatchers.Default)
        currentJob = job
        val mediaItem = instance?.currentMediaItem
        job.launch {
            if (viewModel.lastBitmapUri?.equals(mediaItem?.mediaMetadata?.artworkUri) == true) {
                wrappedContext = makeWrappedContext(context,
                    viewModel.lastDynamicColorsOptions!!)
                applyColorScheme(false)
                return@launch
            }
            currentDisposable = context.imageLoader.enqueue(
                ImageRequest.Builder(context).apply {
                    data(mediaItem?.mediaMetadata?.artworkUri)
                    val colorAccuracy = prefs.getBoolean("color_accuracy", false)
                    if (colorAccuracy) {
                        size(256, 256)
                    } else {
                        size(16, 16)
                    }
                    allowConversionToBitmap(true)
                    scale(Scale.FILL)
                    target(onSuccess = {
                        val drawable = it.asDrawable(context.resources)
                        job.launch {
                            val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else {
                                removeColorScheme()
                                return@launch
                            }
                            val options = DynamicColorsOptions.Builder()
                                    .setContentBasedSource(bitmap)
                                    .build() // <-- this is computationally expensive!
                            viewModel.lastBitmapUri = mediaItem?.mediaMetadata?.artworkUri
                            viewModel.lastDynamicColorsOptions = options

                            wrappedContext = makeWrappedContext(context, options)
                            applyColorScheme()
                        }
                    }, onError = {
                        removeColorScheme()
                    })
                    error(R.drawable.ic_default_cover)
                    allowHardware(false)
                }.build()
            )
        }
    }

    private fun makeWrappedContext(context: Context, options: DynamicColorsOptions) =
        DynamicColors.wrapContextIfAvailable(
            context,
            options
        ).apply {
            // TODO does https://stackoverflow.com/a/58004553 describe this or another bug? will google ever fix anything?
            resources.configuration.uiMode =
                context.resources.configuration.uiMode
        }.let { themeContext ->
            if (prefs.getBoolean("pureDark", false) &&
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            ) {
                ContextThemeWrapper(themeContext, R.style.ThemeOverlay_PureDark)
            } else themeContext
        }

    private fun updateQualityIndicators(info: AudioFormatInfo?) {
        val oldInfo =
            (bottomSheetFullQualityDetails.getTag(R.id.quality_details) as AudioFormatInfo?)
        if (oldInfo == info) return
        (bottomSheetFullQualityDetails.getTag(R.id.fade_in_animation) as ViewPropertyAnimator?)?.cancel()
        (bottomSheetFullQualityDetails.getTag(R.id.fade_out_animation) as ViewPropertyAnimator?)?.cancel()
        if (info == null && bottomSheetFullQualityDetails.isInvisible) return
        if (oldInfo != null)
            applyQualityInfo(oldInfo)
        bottomSheetFullQualityDetails.setTag(R.id.quality_details, info)
        bottomSheetFullQualityDetails.fadOutAnimation(300) {
            if (info == null)
                return@fadOutAnimation
            applyQualityInfo(info)
            bottomSheetFullQualityDetails.fadInAnimation(300)
        }
    }

    private fun applyQualityInfo(info: AudioFormatInfo) {
        val icon = when (info.spatialFormat) {
            SpatialFormat.SURROUND_5_0,
            SpatialFormat.SURROUND_5_1,
            SpatialFormat.SURROUND_6_1,
            SpatialFormat.SURROUND_7_1 -> R.drawable.ic_surround_sound

            SpatialFormat.DOLBY_AC3,
            SpatialFormat.DOLBY_EAC3,
            SpatialFormat.DOLBY_EAC3_JOC,
            SpatialFormat.DOLBY_AC4,
            SpatialFormat.DOLBY_TRUEHD -> R.drawable.ic_dolby

            SpatialFormat.DTS,
            SpatialFormat.DTS_EXPRESS,
            SpatialFormat.DTS_HD,
            SpatialFormat.DTS_UHD -> R.drawable.ic_dts

            else -> when (info.quality) {
                AudioQuality.HIRES -> R.drawable.ic_high_res
                AudioQuality.HD -> R.drawable.ic_hd
                AudioQuality.CD -> R.drawable.ic_cd
                AudioQuality.HQ -> R.drawable.ic_hq
                AudioQuality.LOSSY -> R.drawable.ic_lossy
                else -> null
            }
        }

        val drawable = icon?.let { iconRes ->
            AppCompatResources.getDrawable(context, iconRes)?.apply {
                setBounds(0, 0, 18.dpToPx(context), 18.dpToPx(context))
            }
        }
        bottomSheetFullQualityDetails.setCompoundDrawablesRelative(drawable, null, null, null)

        bottomSheetFullQualityDetails.text = buildString {
            var hadFirst = false
            info.bitDepth?.let {
                hadFirst = true
                append("${it}bit")
            }
            if (info.sampleRate != null) {
                if (hadFirst)
                    append(" / ")
                else
                    hadFirst = true
                append("${info.sampleRate / 1000f}kHz")
            }
            if (info.sourceChannels != null) {
                if (hadFirst)
                    append(" / ")
                else
                    hadFirst = true
                append("${info.sourceChannels}ch")
            }
            info.bitrate?.let {
                if (hadFirst)
                    append(" / ")
                append("${it / 1000}kbps")
            }
        }
    }

    private suspend fun applyColorScheme(animate: Boolean = true) {
        val ctx = wrappedContext ?: context

        val colorSurface = MaterialColors.getColor(
            ctx,
            com.google.android.material.R.attr.colorSurface,
            -1
        )

        val colorOnSurface = MaterialColors.getColor(
            ctx,
            com.google.android.material.R.attr.colorOnSurface,
            -1
        )

        val colorOnSurfaceVariant = MaterialColors.getColor(
            ctx,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            -1
        )

        val colorPrimary =
            MaterialColors.getColor(
                ctx,
                androidx.appcompat.R.attr.colorPrimary,
                -1
            )

        val colorSecondary =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSecondary,
                -1
            )

        val colorSecondaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSecondaryContainer,
                -1
            )

        val colorOnSecondaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                -1
            )

        val selectorBackground =
            AppCompatResources.getColorStateList(
                ctx,
                R.color.sl_check_button
            )

        val selectorFavBackground =
            AppCompatResources.getColorStateList(
                ctx,
                R.color.sl_fav_button
            )

        val backgroundProcessedColor = ColorUtils.getColor(
            colorSurface,
            ColorUtils.ColorType.COLOR_BACKGROUND_ELEVATED,
            ctx
        )

        val colorContrastFainted = ColorUtils.getColor(
            colorSecondaryContainer,
            ColorUtils.ColorType.COLOR_CONTRAST_FAINTED,
            ctx
        )

        val lyricsTextColor = androidx.core.graphics.ColorUtils.compositeColors(
            androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 77),
            backgroundProcessedColor
        )

        val lyricsHighlightTlColor = androidx.core.graphics.ColorUtils.compositeColors(
            androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 200),
            backgroundProcessedColor
        )

        if (animate) {

            val surfaceTransition = ValueAnimator.ofArgb(
                (background as ColorDrawable).color,
                backgroundProcessedColor
            )

            val primaryTransition = ValueAnimator.ofArgb(
                bottomSheetFullTitle.textColors.defaultColor,
                colorPrimary
            )

            val secondaryContainerTransition = ValueAnimator.ofArgb(
                bottomSheetFullControllerButton.backgroundTintList!!.defaultColor,
                colorSecondaryContainer
            )

            val onSecondaryContainerTransition = ValueAnimator.ofArgb(
                bottomSheetFullControllerButton.iconTint.defaultColor,
                colorOnSecondaryContainer
            )

            val colorContrastFaintedTransition = ValueAnimator.ofArgb(
                bottomSheetFullSlider.trackInactiveTintList.defaultColor,
                colorContrastFainted
            )

            val colorOnSurfaceTransition = ValueAnimator.ofArgb(
                bottomSheetLyricButton.iconTint.defaultColor,
                colorOnSurface
            )

            val lyricTextColorTransition = ValueAnimator.ofArgb(
                bottomSheetFullLyricView.defaultTextColor,
                lyricsTextColor
            )

            val lyricHighlightTlColorTransition = ValueAnimator.ofArgb(
                bottomSheetFullLyricView.highlightTlTextColor,
                lyricsHighlightTlColor
            )

            val loopTransition = ValueAnimator.ofArgb(
                bottomSheetLoopButton.iconTint.getColorForState(
                    bottomSheetLoopButton.drawableState, Color.RED
                ),
                selectorBackground.getColorForState(
                    bottomSheetLoopButton.drawableState, Color.RED
                )
            )

            val shuffleTransition = ValueAnimator.ofArgb(
                bottomSheetShuffleButton.iconTint.getColorForState(
                    bottomSheetShuffleButton.drawableState, Color.RED
                ),
                selectorBackground.getColorForState(
                    bottomSheetShuffleButton.drawableState, Color.RED
                )
            )

            val favoriteTransition = ValueAnimator.ofArgb(
                bottomSheetFavoriteButton.iconTint.getColorForState(
                    bottomSheetFavoriteButton.drawableState, Color.RED
                ),
                selectorFavBackground.getColorForState(
                    bottomSheetFavoriteButton.drawableState, Color.RED
                )
            )

            surfaceTransition.apply {
                addUpdateListener { animation ->
                    setBackgroundColor(
                        animation.animatedValue as Int
                    )
                    bottomSheetFullLyricView.setBackgroundColor(
                        animation.animatedValue as Int
                    )
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            primaryTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFullSlider.thumbTintList =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullSlider.trackActiveTintList =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullSeekBar.progressTintList =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullSeekBar.thumbTintList =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullLyricView.updateHighlightColor(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            secondaryContainerTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFullControllerButton.backgroundTintList =
                        ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            onSecondaryContainerTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFullControllerButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            colorContrastFaintedTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFullSlider.trackInactiveTintList =
                        ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            colorOnSurfaceTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetTimerButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetPlaybackSpeedButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetPlaylistButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetLyricButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullNextButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullPreviousButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                    bottomSheetFullSlideUpButton.iconTint =
                        ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            lyricTextColorTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFullLyricView.updateTextColor(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            lyricHighlightTlColorTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFullLyricView.updateHighlightTlColor(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            loopTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetLoopButton.iconTint = ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            shuffleTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetShuffleButton.iconTint = ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            favoriteTransition.apply {
                addUpdateListener { animation ->
                    val progressColor = animation.animatedValue as Int
                    bottomSheetFavoriteButton.iconTint = ColorStateList.valueOf(progressColor)
                }
                duration = BACKGROUND_COLOR_TRANSITION_SEC
            }

            withContext(Dispatchers.Main) {
                surfaceTransition.start()
                primaryTransition.start()
                secondaryContainerTransition.start()
                onSecondaryContainerTransition.start()
                colorContrastFaintedTransition.start()
                colorOnSurfaceTransition.start()
                lyricTextColorTransition.start()
                lyricHighlightTlColorTransition.start()
                loopTransition.start()
                shuffleTransition.start()
                favoriteTransition.start()
            }

            delay(max(BACKGROUND_COLOR_TRANSITION_SEC,
                FOREGROUND_COLOR_TRANSITION_SEC))
        }

        currentJob = null
        postOnAnimation {
            setBackgroundColor(backgroundProcessedColor)
            bottomSheetFullLyricView.setBackgroundColor(backgroundProcessedColor)
            bottomSheetFullTitle.setTextColor(
                colorPrimary
            )
            bottomSheetFullSubtitle.setTextColor(
                colorSecondary
            )
            bottomSheetFullControllerButton.backgroundTintList =
                ColorStateList.valueOf(colorSecondaryContainer)
            bottomSheetFullControllerButton.iconTint =
                ColorStateList.valueOf(colorOnSecondaryContainer)

            bottomSheetFullSlider.thumbTintList =
                ColorStateList.valueOf(colorPrimary)
            bottomSheetFullSlider.trackActiveTintList =
                ColorStateList.valueOf(colorPrimary)
            bottomSheetFullSeekBar.progressTintList =
                ColorStateList.valueOf(colorPrimary)
            bottomSheetFullSeekBar.thumbTintList =
                ColorStateList.valueOf(colorPrimary)
            bottomSheetFullSlider.trackInactiveTintList =
                ColorStateList.valueOf(colorContrastFainted)
            TextViewCompat.setCompoundDrawableTintList(
                bottomSheetFullQualityDetails,
                ColorStateList.valueOf(colorOnSurfaceVariant)
            )
            bottomSheetFullQualityDetails.setTextColor(
                colorOnSurfaceVariant
            )
            bottomSheetFullLyricView.updateTextColor(
                lyricsTextColor,
                colorPrimary,
                lyricsHighlightTlColor,
            )

            bottomSheetTimerButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetPlaybackSpeedButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetPlaylistButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetShuffleButton.iconTint =
                selectorBackground
            bottomSheetLoopButton.iconTint =
                selectorBackground
            bottomSheetLyricButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetFavoriteButton.iconTint =
                selectorFavBackground

            bottomSheetFullNextButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetFullPreviousButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)
            bottomSheetFullSlideUpButton.iconTint =
                ColorStateList.valueOf(colorOnSurface)

            bottomSheetFullPosition.setTextColor(
                colorOnSurfaceVariant
            )
            bottomSheetFullDuration.setTextColor(
                colorOnSurfaceVariant
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        if (instance?.mediaItemCount != 0) {
            lastDisposable?.dispose()
            lastDisposable = null
            loadCoverForImageView()
            if (DynamicColors.isDynamicColorAvailable() &&
                prefs.getBooleanStrict("content_based_color", true)
            ) {
                addColorScheme()
            }
            bottomSheetFullTitle.setTextAnimation(
                mediaItem?.mediaMetadata?.title ?: "",
                skipAnimation = firstTime
            )
            bottomSheetFullSubtitle.setTextAnimation(
                mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist),
                skipAnimation = firstTime
            )
            updateDuration()
        } else {
            lastDisposable?.dispose()
            lastDisposable = null
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        val isHeart = (mediaMetadata.userRating as? HeartRating)?.isHeart == true
        if (bottomSheetFavoriteButton.isChecked != isHeart) {
            bottomSheetFavoriteButton.removeOnCheckedChangeListener(this)
            bottomSheetFavoriteButton.isChecked =
                (mediaMetadata.userRating as? HeartRating)?.isHeart == true
        }
        bottomSheetFavoriteButton.addOnCheckedChangeListener(this) // see onCheckedChanged
    }

    private fun updateDuration() {
        val duration = instance?.contentDuration?.let { if (it == C.TIME_UNSET) null else it }
            ?: instance?.currentMediaItem?.mediaMetadata?.durationMs
        if (duration != null && duration.toInt() != bottomSheetFullSeekBar.max) {
            bottomSheetFullDuration.setTextAnimation(
                CalculationUtils.convertDurationToTimeStamp(duration)
            )
            val position =
                CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
            if (!isUserTracking) {
                bottomSheetFullSeekBar.max = duration.toInt()
                bottomSheetFullSeekBar.progress = instance?.currentPosition?.toInt() ?: 0
                bottomSheetFullSlider.valueTo = duration.toFloat().coerceAtLeast(1f)
                bottomSheetFullSlider.value =
                    min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
                bottomSheetFullPosition.text = position
            }
            bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
        }
    }

    private fun loadCoverForImageView() {
        if (lastDisposable != null) {
            lastDisposable?.dispose()
            lastDisposable = null
            Log.e(TAG, "raced while loading cover in onMediaItemTransition?")
        }
        val mediaItem = instance?.currentMediaItem
        Log.d(TAG, "load cover for " + mediaItem?.mediaMetadata?.title + " considered")
        if (bottomSheetFullCover.width != 0 && bottomSheetFullCover.height != 0) {
            Log.d(
                TAG,
                "load cover for " + mediaItem?.mediaMetadata?.title + " at " + bottomSheetFullCover.width + " " + bottomSheetFullCover.height
            )
            lastDisposable = context.imageLoader.enqueue(
                ImageRequest.Builder(context).apply {
                    data(mediaItem?.mediaMetadata?.artworkUri)
                    size(bottomSheetFullCover.width, bottomSheetFullCover.height)
                    scale(Scale.FILL)
                    target(onSuccess = {
                        bottomSheetFullCover.setImageDrawable(it.asDrawable(context.resources))
                    }, onError = {
                        bottomSheetFullCover.setImageDrawable(it?.asDrawable(context.resources))
                    }) // do not react to onStart() which sets placeholder
                    error(R.drawable.ic_default_cover)
                    allowHardware(bottomSheetFullCover.isHardwareAccelerated)
                }.build()
            )
        }
    }

    override fun onCheckedChanged(button: MaterialButton?, isChecked: Boolean) {
        instance?.currentMediaItem?.let { song ->
            val entry = Entry.ofMediaItem(song)
            if (entry != null)
                activity.markIsFavoriteStatus(listOf(entry), isChecked)
        }
        bottomSheetFavoriteButton.removeOnCheckedChangeListener(this) // see onMediaMetadataChanged
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        positionRunnable.run()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        bottomSheetShuffleButton.isChecked = shuffleModeEnabled
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        when (repeatMode) {
            Player.REPEAT_MODE_ALL -> {
                bottomSheetLoopButton.isChecked = true
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
            }

            Player.REPEAT_MODE_ONE -> {
                bottomSheetLoopButton.isChecked = true
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat_one)
            }

            Player.REPEAT_MODE_OFF -> {
                bottomSheetLoopButton.isChecked = false
                bottomSheetLoopButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_repeat)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (instance?.isPlaying == true) {
            if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 1) {
                bottomSheetFullControllerButton.icon =
                    AppCompatResources.getDrawable(
                        wrappedContext ?: context,
                        R.drawable.play_anim
                    )
                bottomSheetFullControllerButton.background =
                    AppCompatResources.getDrawable(context, R.drawable.bg_play_anim)
                bottomSheetFullControllerButton.icon.startAnimation()
                bottomSheetFullControllerButton.background.startAnimation()
                bottomSheetFullControllerButton.setTag(R.id.play_next, 1)
            }
            if (!isUserTracking) {
                progressDrawable.animate = true
            }
            if (!runnableRunning) {
                runnableRunning = true
                handler.postDelayed(positionRunnable, SLIDER_UPDATE_INTERVAL)
            }
            bottomSheetFullCover.startRotation()
        } else if (playbackState != Player.STATE_BUFFERING) {
            if (bottomSheetFullControllerButton.getTag(R.id.play_next) as Int? != 2) {
                bottomSheetFullControllerButton.icon =
                    AppCompatResources.getDrawable(
                        wrappedContext ?: context,
                        R.drawable.pause_anim
                    )
                bottomSheetFullControllerButton.background =
                    AppCompatResources.getDrawable(context, R.drawable.bg_pause_anim)
                bottomSheetFullControllerButton.icon.startAnimation()
                bottomSheetFullControllerButton.background.startAnimation()
                bottomSheetFullControllerButton.setTag(R.id.play_next, 2)
                bottomSheetFullCover.stopRotation()
            }
            if (!isUserTracking) {
                progressDrawable.animate = false
            }
        }
    }

    // https://developer.android.com/media/implement/surfaces/pause-and-resume-media-playback-with-spacebar
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        //androidx.media3.common.util.Log.e("hi","$keyCode") TODO this method is no-op, but why?
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                instance?.playOrPause(); true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                instance?.seekToPrevious(); true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                instance?.seekToNext(); true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }

    private val positionRunnable = object : Runnable {
        override fun run() {
            updateDuration() // TODO: figure out which callback this can be put in.
            val position =
                CalculationUtils.convertDurationToTimeStamp(instance?.currentPosition ?: 0)
            if (!isUserTracking) {
                bottomSheetFullSeekBar.progress = instance?.currentPosition?.toInt() ?: 0
                bottomSheetFullSlider.value =
                    min(instance?.currentPosition?.toFloat() ?: 0f, bottomSheetFullSlider.valueTo)
                bottomSheetFullPosition.text = position
            }
            bottomSheetFullLyricView.updateLyricPositionFromPlaybackPos()
            if (instance?.isPlaying == true && runnableRunning) {
                handler.postDelayed(this, SLIDER_UPDATE_INTERVAL)
            } else {
                runnableRunning = false
            }
        }
    }

    class MyViewModel : ViewModel() {
        var lastBitmapUri: Uri? = null
        var lastDynamicColorsOptions: DynamicColorsOptions? = null
    }

}
