package org.akanework.gramophone.ui.components

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.TypefaceCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.ui.CustomSmoothScroller
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import org.akanework.gramophone.ui.MainActivity

class LegacyLyricsAdapter(
    private val context: Context,
) : RecyclerView.Adapter<LegacyLyricsAdapter.ViewHolder>() {
    companion object {
        const val LYRIC_REMOVE_HIGHLIGHT = 0
        const val LYRIC_SET_HIGHLIGHT = 1
        const val LYRIC_SCROLL_DURATION: Long = 650
    }

    private val activity
        get() = context as MainActivity
    var callback: NewLyricsView.Callbacks? = null
    private val speed
        get() = callback?.speed() ?: 1f
    private var recyclerView: RecyclerView? = null
    private val prefs = context.defaultPrefs
    private var defaultTextColor = 0
    private var highlightTextColor = 0
    private val interpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
    private val lyricList = mutableListOf<Lyric>()
    var currentFocusPos = -1
        private set
    private var currentTranslationPos = -1
    private var isBoldEnabled = false
    private var isLyricCentered = false
    private var forceNoAnimation = false
    private val sizeFactor = 1f
    private val defaultSizeFactor = .97f

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder =
        ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.lyrics, parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw IllegalStateException()
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val lyric = lyricList[position]
        val isHighlightPayload =
            payloads.isNotEmpty() && (payloads[0] == LYRIC_SET_HIGHLIGHT || payloads[0] == LYRIC_REMOVE_HIGHLIGHT)

        with(holder.lyricCard) {
            setOnClickListener {
                lyric.timeStamp?.let { it1 ->
                    ViewCompat.performHapticFeedback(
                        it,
                        HapticFeedbackConstantsCompat.CONTEXT_CLICK
                    )
                    callback?.setPlayWhenReady(true)
                    callback?.seekTo(it1.toULong())
                }
            }
        }

        with(holder.lyricTextView) {
            visibility = if (lyric.content.isNotEmpty()) VISIBLE else GONE
            text = lyric.content
            gravity = if (isLyricCentered) Gravity.CENTER else Gravity.START
            translationY = 0f

            val textSize = if (lyric.isTranslation) 20f else 34.25f
            val paddingTop = if (lyric.isTranslation) 2 else 18
            val paddingBottom =
                if (position + 1 < lyricList.size && lyricList[position + 1].isTranslation) 2 else 18

            if (isBoldEnabled) {
                this.typeface = TypefaceCompat.create(context, null, 700, false)
            } else {
                this.typeface = TypefaceCompat.create(context, null, 500, false)
            }

            if (isLyricCentered) {
                this.gravity = Gravity.CENTER
            } else {
                this.gravity = Gravity.START
            }

            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            setPadding(
                (12.5f).dpToPx(context).toInt(),
                paddingTop.dpToPx(context),
                (12.5f).dpToPx(context).toInt(),
                paddingBottom.dpToPx(context)
            )

            doOnLayout {
                pivotX = 0f
                pivotY = height / 2f
            }

            when {
                isHighlightPayload -> {
                    val targetScale =
                        if (payloads[0] == LYRIC_SET_HIGHLIGHT) sizeFactor else defaultSizeFactor
                    val targetColor =
                        if (payloads[0] == LYRIC_SET_HIGHLIGHT)
                            highlightTextColor
                        else
                            defaultTextColor
                    animateText(targetScale, targetColor)
                }

                position == currentFocusPos || position == currentTranslationPos -> {
                    scaleText(sizeFactor)
                    setTextColor(highlightTextColor)
                }

                else -> {
                    scaleText(defaultSizeFactor)
                    setTextColor(defaultTextColor)
                }
            }
        }
    }

    private fun TextView.animateText(targetScale: Float, targetColor: Int) {
        val animator = ValueAnimator.ofFloat(scaleX, targetScale)
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            scaleX = animatedValue
            scaleY = animatedValue
        }
        animator.duration = (LYRIC_SCROLL_DURATION * speed).toLong()
        animator.interpolator = interpolator
        animator.start()

        val colorAnimator = ValueAnimator.ofArgb(textColors.defaultColor, targetColor)
        colorAnimator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            setTextColor(animatedValue)
        }
        colorAnimator.duration = (LYRIC_SCROLL_DURATION * speed).toLong()
        colorAnimator.interpolator = interpolator
        colorAnimator.start()
    }

    private fun TextView.scaleText(scale: Float) {
        scaleX = scale
        scaleY = scale
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        updateLyricStatus()
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    fun updateLyricStatus() {
        isBoldEnabled = prefs.getBooleanStrict("lyric_bold", false)
        isLyricCentered = prefs.getBooleanStrict("lyric_center", false)
        forceNoAnimation = prefs.getBooleanStrict("lyric_no_animation", false)
    }

    override fun getItemCount(): Int = lyricList.size

    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {
        val lyricTextView: TextView = view.findViewById(R.id.lyric)
        val lyricCard: View = view.findViewById(R.id.cardview)
    }

    fun updateHighlight(position: Int) {
        if (currentFocusPos == position) return
        if (position >= 0) {
            currentFocusPos.let {
                notifyItemChanged(it, LYRIC_REMOVE_HIGHLIGHT)
                currentFocusPos = position
                notifyItemChanged(currentFocusPos, LYRIC_SET_HIGHLIGHT)
            }

            if (position + 1 < lyricList.size &&
                lyricList[position + 1].isTranslation
            ) {
                currentTranslationPos.let {
                    notifyItemChanged(it, LYRIC_REMOVE_HIGHLIGHT)
                    currentTranslationPos = position + 1
                    notifyItemChanged(currentTranslationPos, LYRIC_SET_HIGHLIGHT)
                }
            } else if (currentTranslationPos != -1) {
                notifyItemChanged(currentTranslationPos, LYRIC_REMOVE_HIGHLIGHT)
                currentTranslationPos = -1
            }
        } else {
            currentFocusPos = -1
            currentTranslationPos = -1
        }
    }

    private fun createSmoothScroller(noAnimation: Boolean): RecyclerView.SmoothScroller {
        return object : CustomSmoothScroller(context) {

            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return super.calculateDtToFit(
                    viewStart,
                    viewEnd,
                    boxStart,
                    boxEnd,
                    snapPreference
                ) + 72.dpToPx(context)
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }

            override fun calculateTimeForDeceleration(dx: Int): Int {
                return (LYRIC_SCROLL_DURATION * speed).toInt()
            }

            override fun afterTargetFound() {
                if (targetPosition > 1) {
                    val firstVisibleItemPosition = targetPosition + 1
                    val lastVisibleItemPosition =
                        (layoutManager as LinearLayoutManager).findLastVisibleItemPosition() + 2
                    for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
                        val view: View? = layoutManager!!.findViewByPosition(i)
                        if (view != null) {
                            if (i == targetPosition + 1 && lyricList[i].isTranslation) {
                                continue
                            }
                            if (!noAnimation) {
                                val ii = i - firstVisibleItemPosition -
                                        if (lyricList[i].isTranslation) 1 else 0
                                val depth = 15.dpToPx(context).toFloat()
                                val duration = (LYRIC_SCROLL_DURATION * 0.278 * speed).toLong()
                                val durationReturn =
                                    (LYRIC_SCROLL_DURATION * 0.722 * speed).toLong()
                                val durationStep = (LYRIC_SCROLL_DURATION * 0.1 * speed).toLong()
                                val animator = ObjectAnimator.ofFloat(
                                    view,
                                    "translationY",
                                    0f,
                                    depth,
                                )
                                animator.setDuration(duration)
                                animator.interpolator = PathInterpolator(0.96f, 0.43f, 0.72f, 1f)
                                animator.doOnEnd {
                                    val animator1 = ObjectAnimator.ofFloat(
                                        view,
                                        "translationY",
                                        depth,
                                        0f
                                    )
                                    animator1.setDuration(durationReturn + ii * durationStep)
                                    animator1.interpolator = PathInterpolator(0.17f, 0f, -0.15f, 1f)
                                    animator1.start()
                                }
                                animator.start()
                            }
                        }
                    }
                }
            }
        }
    }

    fun smoothScrollTo(position: Int, noAnimation: Boolean = false) {
        val smoothScroller = createSmoothScroller(noAnimation || forceNoAnimation)
        smoothScroller.targetPosition = position
        recyclerView?.layoutManager?.startSmoothScroll(
            smoothScroller
        )
    }

    fun onPrefsChanged() {
        updateLyricStatus()
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun updateLyricPositionFromPlaybackPos() {
        if (lyricList.isNotEmpty()) {
            val newIndex = updateNewIndex()

            if (newIndex != -1 &&
                newIndex != currentFocusPos
            ) {
                if (lyricList[newIndex].content.isNotEmpty() || newIndex == 0) {
                    smoothScrollTo(newIndex, newIndex == 0)
                }

                updateHighlight(newIndex)
            }
        }
    }

    private fun updateNewIndex(): Int {
        val filteredList = lyricList.filterIndexed { _, lyric ->
            (lyric.timeStamp ?: 0) <= (callback?.getCurrentPosition()?.toLong() ?: 0L)
        }

        return if (filteredList.isNotEmpty()) {
            filteredList.indices.maxBy {
                filteredList[it].timeStamp ?: 0
            }
        } else {
            -1
        }
    }

    fun updateLyrics(newLyric: SemanticLyrics?) {
        val parsedLyrics = newLyric.convertForLegacy()
        if (lyricList != parsedLyrics) {
            lyricList.clear()
            if (parsedLyrics?.isEmpty() != false) {
                lyricList.add(
                    Lyric(
                        null,
                        context.getString(R.string.no_lyric_found)
                    )
                )
            } else {
                lyricList.addAll(parsedLyrics)
            }
            @SuppressLint("NotifyDataSetChanged")
            notifyDataSetChanged()
            smoothScrollTo(0)
            updateHighlight(0)
        }
    }

    fun updateTextColor(newColor: Int, newHighlightColor: Int) {
        defaultTextColor = newColor
        highlightTextColor = newHighlightColor
        @SuppressLint("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    private data class Lyric(
        val timeStamp: Long? = null,
        val content: String = "",
        var isTranslation: Boolean = false
    )

    private fun SemanticLyrics?.convertForLegacy(): MutableList<Lyric>? {
        if (this == null) return null
        if (this is SyncedLyrics) {
            return this.text.map {
                Lyric(it.start.toLong(), it.text, it.isTranslated)
            }.toMutableList()
        }
        return mutableListOf(
            Lyric(
                null,
            this.unsyncedText.joinToString("\n") { it.first }, false
        )
        )
    }
}
