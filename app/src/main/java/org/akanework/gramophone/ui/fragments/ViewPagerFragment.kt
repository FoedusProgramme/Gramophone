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

package org.akanework.gramophone.ui.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.getSessionId
import org.akanework.gramophone.logic.needsManualSnackBarInset
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.SdScanner
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter
import org.akanework.gramophone.ui.components.PlayerBottomSheet
import org.akanework.gramophone.ui.fragments.settings.MainSettingsActivity

/**
 * ViewPagerFragment:
 *   A fragment that's in charge of displaying tabs
 * and is connected to the drawer.
 *
 * @author AkaneTan
 */
class ViewPagerFragment : BaseFragment(true) {
    lateinit var appBarLayout: AppBarLayout
        private set
    val recycledViewPool = RecyclerView.RecycledViewPool()
    private lateinit var viewPager2: ViewPager2
    private lateinit var adapter: ViewPager2Adapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_viewpager, container, false)
        val tabLayout = rootView.findViewById<TabLayout>(R.id.tab_layout)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        viewPager2 = rootView.findViewById<ViewPager2>(R.id.fragment_viewpager)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_more_vert_alt_topappbar)

        topAppBar.setOnMenuItemClickListener {
            val activity = requireActivity() as MainActivity
            when (it.itemId) {
                R.id.search -> {
                    activity.startFragment(SearchFragment())
                }

                R.id.equalizer -> {
                    val intent =
                        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            // EXTRA_PACKAGE_NAME is probably not needed but might as well add for good measure
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, requireContext().packageName)
                            putExtra(
                                AudioEffect.EXTRA_AUDIO_SESSION,
                                activity.getPlayer()?.getSessionId()
                            )
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                    try {
                        if (Settings.System.getString(
                                requireContext().contentResolver,
                                "firebase.test.lab"
                            ) != "true"
                        ) {
                            activity.startingActivity.launch(intent)
                        }
                    } catch (_: ActivityNotFoundException) {
                        // Let's show a toast here if no system inbuilt EQ was found.
                        Toast.makeText(
                            requireContext(),
                            R.string.equalizer_not_found,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                R.id.quick_refresh -> {
                    val playerLayout = activity.playerBottomSheet
                    activity.updateLibrary {
                        showRefreshDoneSnackBar(playerLayout, runBlocking { activity.reader.songListFlow.first().size })
                    }
                }

                R.id.refresh -> {
                    val context = requireContext()
                    val playerLayout = activity.playerBottomSheet
                    MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_refresh)
                        .setTitle(R.string.did_you_know)
                        .setMessage(R.string.refresh_did_you_know)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    Toast.makeText(context, R.string.refreshing_wait, Toast.LENGTH_LONG).show()
                    CoroutineScope(Dispatchers.Default).launch {
                        SdScanner.scanEverything(context, 5000) { progress ->
                            if (progress.step != SdScanner.SimpleProgress.Step.DONE) {
                                val str = if (progress.percentage == null)
                                    context.getString(R.string.refreshing_wait)
                                else context.getString(R.string.still_refreshing, progress.step.ordinal,
                                    SdScanner.SimpleProgress.Step.DONE.ordinal - 1, "${progress.percentage}%")
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, str, Toast.LENGTH_SHORT).show()
                                }
                                return@scanEverything
                            }
                            activity.updateLibrary {
                                showRefreshDoneSnackBar(playerLayout, runBlocking { activity.reader.songListFlow.first().size })
                            }
                        }
                    }
                }

                R.id.settings -> {
                    activity.startActivity(Intent(activity, MainSettingsActivity::class.java))
                }

                R.id.shuffle -> {
                    val controller = activity.getPlayer()
                    runBlocking { activity.reader.songListFlow.first() }.takeIf { it.isNotEmpty() }?.also {
                        controller?.shuffleModeEnabled = true
                        controller?.setMediaItems(it)
                        controller?.prepare()
                        controller?.play()
                    } ?: controller?.setMediaItems(listOf())
                }

                else -> throw IllegalStateException()
            }
            true
        }

        // Connect ViewPager2.

        viewPager2.offscreenPageLimit = 99999 // TODO is 99999 a good value?
        adapter =
            ViewPager2Adapter(
                childFragmentManager,
                viewLifecycleOwner.lifecycle,
                requireContext(),
                viewPager2
            )
        viewPager2.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            tab.text = getString(adapter.getLabelResId(position))
            tab.view.post {
                try {
                    /*
                     * Add margin to last and first tab.
                     * There's no attribute to let you set margin
                     * to the last tab.
                     */
                    val lp = tab.view.layoutParams as ViewGroup.MarginLayoutParams
                    lp.marginStart = if (position == 0)
                        resources.getDimension(R.dimen.tab_layout_content_padding).toInt() else 0
                    lp.marginEnd = if (position == tabLayout.tabCount - 1)
                        resources.getDimension(R.dimen.tab_layout_content_padding).toInt() else 0
                    tab.view.layoutParams = lp
                } catch (_: IllegalStateException) {
                }
            }
        }.attach()

        if (adapter.itemCount < 2) {
            tabLayout.visibility = View.GONE
        } else {
            tabLayout.visibility = View.VISIBLE
        }

        return rootView
    }

    fun maybeReportFullyDrawn(itemId: Int) {
        if (view != null && adapter.getItemId(viewPager2.currentItem).toInt() == itemId)
            mainActivity.maybeReportFullyDrawn()
    }

    private fun showRefreshDoneSnackBar(playerLayout: PlayerBottomSheet, count: Int) {
        val view = view
        if (view == null) return
        val snackBar =
            Snackbar.make(
                view,
                getString(
                    R.string.refreshed_songs,
                    count,
                ),
                Snackbar.LENGTH_LONG,
            )
        snackBar.setAction(R.string.dismiss) {
            snackBar.dismiss()
        }

        /*
		 * Let's override snack bar's color here so it would
		 * adapt dark mode.
		 */
        snackBar.setBackgroundTint(
            MaterialColors.getColor(
                snackBar.view,
                com.google.android.material.R.attr.colorSurface,
            ),
        )
        snackBar.setActionTextColor(
            MaterialColors.getColor(
                snackBar.view,
                androidx.appcompat.R.attr.colorPrimary,
            ),
        )
        snackBar.setTextColor(
            MaterialColors.getColor(
                snackBar.view,
                com.google.android.material.R.attr.colorOnSurface,
            ),
        )

        // Set an anchor for snack bar.
        if (playerLayout.visible && playerLayout.actuallyVisible)
            snackBar.anchorView = playerLayout
        else if (needsManualSnackBarInset()) {
            val activity = requireActivity() as MainActivity
            // snack bar only implements proper insets handling for Q+
            snackBar.view.updateMargin {
                val i = ViewCompat.getRootWindowInsets(activity.window.decorView)
                if (i != null) {
                    bottom += i.clone()
                        .getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                }
            }
        }
        snackBar.show()
    }
}
