package org.akanework.gramophone.logic

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.graphics.TypefaceCompat
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.AnimationUtils
import java.util.Locale

// enableEdgeToEdge() without enforcing contrast, magic based on androidx EdgeToEdge.kt
fun ComponentActivity.enableEdgeToEdgeProperly() {
    if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
    } else {
        val darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, darkScrim))
    }
}

fun ViewPager2.setCurrentItemInterpolated(
    item: Int,
    duration: Long = AnimationUtils.MID_DURATION,
    interpolator: TimeInterpolator = AnimationUtils.easingInterpolator,
    pagePxWidth: Int = width
) {
    val pxToDrag: Int = pagePxWidth * (item - currentItem)
    val animator = ValueAnimator.ofInt(0, pxToDrag)
    var previousValue = 0
    animator.addUpdateListener { valueAnimator ->
        val currentValue = valueAnimator.animatedValue as Int
        val currentPxToDrag = (currentValue - previousValue).toFloat()
        fakeDragBy(-currentPxToDrag *
            if (TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR) 1 else -1)
        previousValue = currentValue
    }
    animator.addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) { beginFakeDrag() }
        override fun onAnimationEnd(animation: Animator) { endFakeDrag() }
        override fun onAnimationCancel(animation: Animator) { /* Ignored */ }
        override fun onAnimationRepeat(animation: Animator) { /* Ignored */ }
    })
    animator.interpolator = interpolator
    animator.duration = duration
    animator.start()
}

@Suppress("NOTHING_TO_INLINE")
inline fun Int.dpToPx(context: Context): Int =
    (this.toFloat() * context.resources.displayMetrics.density).toInt()

@Suppress("NOTHING_TO_INLINE")
inline fun Float.dpToPx(context: Context): Float =
    (this * context.resources.displayMetrics.density)

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
        collapsingToolbarLayout?.let{
            it.setCollapsedTitleTypeface(TypefaceCompat.create(context, resources.getFont(R.font.gsans), 600, false))
            it.setExpandedTitleTypeface(TypefaceCompat.create(context, resources.getFont(R.font.gsans), 600, false))
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
            v.setPadding(
                pl + i.left, pt + (if (top) i.top else 0), pr + i.right,
                pb + i.bottom
            )
            extra?.invoke(i)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(insets)
                .setInsets(mask, Insets.NONE)
                .setInsetsIgnoringVisibility(mask, Insets.NONE)
                .build()
        }
    }
}

fun Context.checkEssentialPermission() =
    checkSelfPermission(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED