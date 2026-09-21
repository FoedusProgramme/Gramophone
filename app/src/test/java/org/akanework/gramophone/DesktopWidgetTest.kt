/*
 *     Copyright (C) 2026 SteveZMTstudios
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

package org.akanework.gramophone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import org.akanework.gramophone.ui.CardWidgetProvider
import org.akanework.gramophone.ui.widget.BaseWidgetProvider
import org.akanework.gramophone.ui.widget.CardLayoutVariant
import org.akanework.gramophone.ui.widget.CardWidgetActions
import org.akanework.gramophone.ui.widget.CardWidgetColorResolver
import org.akanework.gramophone.ui.widget.CardWidgetPlaybackState
import org.akanework.gramophone.ui.widget.CardWidgetStore
import org.akanework.gramophone.ui.widget.CardWidgetViewsBuilder
import org.akanework.gramophone.ui.widget.CircleLayoutVariant
import org.akanework.gramophone.ui.widget.DesktopWidgetManager
import org.akanework.gramophone.ui.widget.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DesktopWidgetTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private fun createDummyActions(): CardWidgetActions {
        val dummyIntent = Intent("org.akanework.gramophone.DUMMY")
        val pi = PendingIntent.getBroadcast(
            context, 0, dummyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return CardWidgetActions(
            openAppPi = pi,
            favoritePi = pi,
            prevPi = pi,
            playPausePi = pi,
            nextPi = pi,
            repeatPi = pi,
            shufflePi = pi
        )
    }

    @Test
    fun testCardWidgetPlaybackState() {
        val emptyState = CardWidgetPlaybackState()
        assertFalse(emptyState.hasTrack)
        assertEquals("", emptyState.title)
        assertEquals("", emptyState.artist)
        assertFalse(emptyState.isPlaying)
        assertFalse(emptyState.isFavorite)

        val trackState = CardWidgetPlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            isFavorite = true,
            isShuffle = true,
            repeatMode = Player.REPEAT_MODE_ALL,
            artworkUri = Uri.parse("content://media/external/audio/albumart/1"),
            hdArtworkUri = Uri.parse("https://example.com/artwork.jpg")
        )
        assertTrue(trackState.hasTrack)
        assertEquals(Uri.parse("https://example.com/artwork.jpg"), trackState.bestArtworkUri)
    }

    @Test
    fun testCardWidgetStoreRoundTrip() {
        val original = CardWidgetPlaybackState(
            title = "千千阙歌",
            artist = "陈慧娴",
            isPlaying = false,
            isFavorite = true,
            isShuffle = true,
            repeatMode = Player.REPEAT_MODE_ONE,
            artworkUri = Uri.parse("content://media/1"),
            hdArtworkUri = Uri.parse("content://media/hd1")
        )
        CardWidgetStore.savePlaybackState(context, original)

        val loaded = CardWidgetStore.loadPlaybackState(context)
        assertEquals(original.title, loaded.title)
        assertEquals(original.artist, loaded.artist)
        assertEquals(original.isFavorite, loaded.isFavorite)
        assertEquals(original.isShuffle, loaded.isShuffle)
        assertEquals(original.repeatMode, loaded.repeatMode)
        assertEquals(original.artworkUri, loaded.artworkUri)
        assertEquals(original.hdArtworkUri, loaded.hdArtworkUri)
        assertFalse(loaded.isPlaying)
    }

    @Test
    fun testBuildResponsiveViews() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val state = CardWidgetPlaybackState(
            title = "Sample Song",
            artist = "Sample Artist",
            isPlaying = true,
            isFavorite = true
        )
        val actions = createDummyActions()

        val cardViews = CardWidgetViewsBuilder.buildCardResponsiveRemoteViews(
            context, appWidgetManager, 101, state, actions
        )
        assertNotNull(cardViews)

        val circleViews = CardWidgetViewsBuilder.buildCircleResponsiveRemoteViews(
            context, appWidgetManager, 102, state, actions
        )
        assertNotNull(circleViews)
    }

    @Test
    fun testFavoriteButtonVisibilityOnCardVariants() {
        val state = CardWidgetPlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            isFavorite = true
        )
        val actions = createDummyActions()
        val variantMap = CardWidgetViewsBuilder.getCardVariantViewsMap(context, state, actions)

        // 1. In MEDIUM (2x1 and narrow width where media controls lack space), favorite button must be GONE
        val mediumViews = variantMap.getValue(CardLayoutVariant.MEDIUM)
        val mediumRoot = mediumViews.apply(context, null)
        val mediumFavorite = mediumRoot.findViewById<View>(R.id.widget_favorite)
        assertNotNull(mediumFavorite)
        assertEquals("Favorite button must be GONE in MEDIUM variant (2x1 / narrow)", View.GONE, mediumFavorite.visibility)

        // 2. In MEDIUM_WIDE (>= 220dp, wide width with full media controls), favorite button must be VISIBLE
        val mediumWideViews = variantMap.getValue(CardLayoutVariant.MEDIUM_WIDE)
        val mediumWideRoot = mediumWideViews.apply(context, null)
        val mediumWideFavorite = mediumWideRoot.findViewById<View>(R.id.widget_favorite)
        assertNotNull(mediumWideFavorite)
        assertEquals("Favorite button must be VISIBLE in MEDIUM_WIDE variant", View.VISIBLE, mediumWideFavorite.visibility)

        // 3. In LARGE and LARGE_WIDE variants, favorite button must remain VISIBLE
        val largeViews = variantMap.getValue(CardLayoutVariant.LARGE)
        val largeRoot = largeViews.apply(context, null)
        val largeFavorite = largeRoot.findViewById<View>(R.id.widget_favorite)
        assertNotNull(largeFavorite)
        assertEquals("Favorite button must be VISIBLE in LARGE variant", View.VISIBLE, largeFavorite.visibility)

        val largeWideViews = variantMap.getValue(CardLayoutVariant.LARGE_WIDE)
        val largeWideRoot = largeWideViews.apply(context, null)
        val largeWideFavorite = largeWideRoot.findViewById<View>(R.id.widget_favorite)
        assertNotNull(largeWideFavorite)
        assertEquals("Favorite button must be VISIBLE in LARGE_WIDE variant", View.VISIBLE, largeWideFavorite.visibility)
    }

    @Test
    fun testSelectCardVariantTableDriven() {
        // Measured real-device slots from audit reviews
        assertEquals(CardLayoutVariant.MEDIUM, CardWidgetViewsBuilder.selectCardVariant(191, 88))
        assertEquals(CardLayoutVariant.MEDIUM_WIDE, CardWidgetViewsBuilder.selectCardVariant(260, 88))
        assertEquals(CardLayoutVariant.MEDIUM_WIDE, CardWidgetViewsBuilder.selectCardVariant(329, 88))
        // At 142dp (2 rows), horizontal medium card is selected so cover is not crushed into a thin strip
        assertEquals(CardLayoutVariant.MEDIUM, CardWidgetViewsBuilder.selectCardVariant(195, 142))
        assertEquals(CardLayoutVariant.MEDIUM_WIDE, CardWidgetViewsBuilder.selectCardVariant(244, 142))
        // At >= 180dp (3+ rows), large vertical card is selected with ample height for cover
        assertEquals(CardLayoutVariant.LARGE, CardWidgetViewsBuilder.selectCardVariant(195, 237))
        assertEquals(CardLayoutVariant.LARGE_WIDE, CardWidgetViewsBuilder.selectCardVariant(244, 237))
        assertEquals(CardLayoutVariant.CARD, CardWidgetViewsBuilder.selectCardVariant(180, 50))
        assertEquals(CardLayoutVariant.CARD, CardWidgetViewsBuilder.selectCardVariant(120, 50))
    }

    @Test
    fun testCardLayoutVariantCoverAvailableHeight() {
        val density = context.resources.displayMetrics.density

        // 1. For LARGE and LARGE_WIDE variants (vertical layout), inflate real card_widget_large
        // and assert that at the lower bound minHeight (180dp), cover has positive available height (> 0),
        // and at real-device 3-row slot height (237dp), cover has ample height (>= 48dp).
        for (variant in listOf(CardLayoutVariant.LARGE, CardLayoutVariant.LARGE_WIDE)) {
            val root = LayoutInflater.from(context).inflate(R.layout.card_widget_large, null) as ViewGroup
            root.measure(
                View.MeasureSpec.makeMeasureSpec((variant.minWidth * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((variant.minHeight * density).toInt(), View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)

            val cover = root.findViewById<View>(R.id.widget_cover)
            assertNotNull("Cover view must exist in card_widget_large", cover)
            assertTrue(
                "Cover measured height for $variant at minHeight lower bound must be > 0, was ${cover.measuredHeight} px",
                cover.measuredHeight > 0
            )

            // Typical real-device 3-row slot (237dp)
            root.measure(
                View.MeasureSpec.makeMeasureSpec((variant.minWidth * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((237 * density).toInt(), View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val minTypicalCoverPx = (48 * density).toInt()
            assertTrue(
                "Cover measured height for $variant at 237dp slot must be >= 48dp, was ${cover.measuredHeight} px",
                cover.measuredHeight >= minTypicalCoverPx
            )
        }

        // 2. For MEDIUM and MEDIUM_WIDE variants (horizontal layout), inflate real card_widget_medium
        // and assert that cover has positive available height (> 0) under lower bound constraints (75dp).
        for (variant in listOf(CardLayoutVariant.MEDIUM, CardLayoutVariant.MEDIUM_WIDE)) {
            val root = LayoutInflater.from(context).inflate(R.layout.card_widget_medium, null) as ViewGroup
            root.measure(
                View.MeasureSpec.makeMeasureSpec((variant.minWidth * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((variant.minHeight * density).toInt(), View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)

            val cover = root.findViewById<View>(R.id.widget_cover)
            assertNotNull("Cover view must exist in card_widget_medium", cover)
            assertTrue(
                "Cover measured height for $variant at minHeight lower bound must be > 0, was ${cover.measuredHeight} px",
                cover.measuredHeight > 0
            )
        }
    }

    @Test
    fun testSelectCircleVariantTableDriven() {
        // Real-device slot 138x94 (aWId 143, 2x1) has height < 110dp, so it resolves to PILL
        assertEquals(CircleLayoutVariant.PILL, CardWidgetViewsBuilder.selectCircleVariant(138, 94))
        // 2x2 default drop (122x122) has width >= 110 && height >= 110, so it resolves to CIRCLE
        assertEquals(CircleLayoutVariant.CIRCLE, CardWidgetViewsBuilder.selectCircleVariant(122, 122))
        // 3x2 slot 247x180 (aWId 139) resolves to CIRCLE
        assertEquals(CircleLayoutVariant.CIRCLE, CardWidgetViewsBuilder.selectCircleVariant(247, 180))
        // 1x1 slot 56x56 resolves to PILL_SINGLE
        assertEquals(CircleLayoutVariant.PILL_SINGLE, CardWidgetViewsBuilder.selectCircleVariant(56, 56))
    }

    @Test
    fun testNextRepeatModeCycle() {
        val off = Player.REPEAT_MODE_OFF
        val all = CardWidgetProvider.nextRepeatMode(off)
        assertEquals(Player.REPEAT_MODE_ALL, all)
        val one = CardWidgetProvider.nextRepeatMode(all)
        assertEquals(Player.REPEAT_MODE_ONE, one)
        val backToOff = CardWidgetProvider.nextRepeatMode(one)
        assertEquals(Player.REPEAT_MODE_OFF, backToOff)
    }

    @Test
    fun testDesktopWidgetManagerUpdateAndRefresh() {
        // Calling updateAllWidgets and refreshFromPlayback should run safely
        DesktopWidgetManager.updateAllWidgets(context)
        DesktopWidgetManager.refreshFromPlayback(context)
    }

    @Test
    fun testBuildWidgetActions() {
        val state = CardWidgetPlaybackState(
            title = "Test",
            artist = "Artist",
            isPlaying = false
        )
        val actions = BaseWidgetProvider.buildWidgetActions(context, state)
        assertNotNull(actions.openAppPi)
        assertNotNull(actions.playPausePi)
        assertNotNull(actions.prevPi)
        assertNotNull(actions.nextPi)
        assertNotNull(actions.favoritePi)
        assertNotNull(actions.repeatPi)
        assertNotNull(actions.shufflePi)

        // When GramophonePlaybackService is not alive, prev/next must fall back to openAppPi
        // to prevent silent failure caused by Android background start restrictions
        assertEquals(actions.openAppPi, actions.prevPi)
        assertEquals(actions.openAppPi, actions.nextPi)
    }

    @Test
    fun testCardWidgetProviderCustomActions() {
        val provider = CardWidgetProvider()
        val repeatIntent = Intent(BaseWidgetProvider.ACTION_REPEAT)
        val shuffleIntent = Intent(BaseWidgetProvider.ACTION_SHUFFLE)
        val favoriteIntent = Intent(BaseWidgetProvider.ACTION_FAVORITE)
        // Must execute safely without exceptions
        provider.onReceive(context, repeatIntent)
        provider.onReceive(context, shuffleIntent)
        provider.onReceive(context, favoriteIntent)
    }

    @Test
    fun testMultiInstanceSnapshotPlaybackState() {
        val original = CardWidgetPlaybackState(
            title = "Consistent Track",
            artist = "Consistent Artist",
            isPlaying = true
        )
        CardWidgetStore.savePlaybackState(context, original)

        val loaded1 = CardWidgetStore.loadPlaybackState(context)
        val loaded2 = CardWidgetStore.loadPlaybackState(context)
        assertFalse(loaded1.isPlaying)
        assertFalse(loaded2.isPlaying)
        assertEquals(loaded1.title, loaded2.title)
        assertEquals(loaded1.artist, loaded2.artist)

        val state = BaseWidgetProvider.buildCurrentPlaybackState(context)
        assertFalse(state.isPlaying)
        assertEquals("Consistent Track", state.title)
    }

    @Test
    fun testWidgetThemeEnumMatchesXmlArray() {
        val xmlKeys = context.resources.getStringArray(R.array.widget_theme_val).toList()
        val enumKeys = WidgetTheme.entries.map { it.key }
        assertEquals("WidgetTheme enum entries must strictly match R.array.widget_theme_val", xmlKeys, enumKeys)
    }

    @Test
    fun testWidgetThemeModesResolution() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sampleBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }

        for (theme in WidgetTheme.entries) {
            prefs.edit().putString(CardWidgetColorResolver.PREF_WIDGET_THEME, theme.key).commit()
            CardWidgetColorResolver.clearCache()

            // Test without artwork
            val colorsNoArtwork = CardWidgetColorResolver.resolve(context, null)
            assertNotNull("Colors for mode ${theme.key} without artwork should not be null", colorsNoArtwork)
            assertTrue("Colors for mode ${theme.key} without artwork should have valid background", colorsNoArtwork.background != 0)
            assertTrue("Colors for mode ${theme.key} without artwork should have valid primary", colorsNoArtwork.primary != 0)

            // Test with artwork
            val colorsWithArtwork = CardWidgetColorResolver.resolve(context, sampleBitmap)
            assertNotNull("Colors for mode ${theme.key} with artwork should not be null", colorsWithArtwork)
            assertTrue("Colors for mode ${theme.key} with artwork should have valid background", colorsWithArtwork.background != 0)
            assertTrue("Colors for mode ${theme.key} with artwork should have valid primary", colorsWithArtwork.primary != 0)
        }
    }

    @Test
    fun testWidgetThemeCacheEfficiency() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(
            CardWidgetColorResolver.PREF_WIDGET_THEME,
            WidgetTheme.ALBUM_ART_DARK.key
        ).commit()
        CardWidgetColorResolver.clearCache()

        val bitmap = android.graphics.Bitmap.createBitmap(80, 80, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }

        val first = CardWidgetColorResolver.resolve(context, bitmap)
        val second = CardWidgetColorResolver.resolve(context, bitmap)
        assertSame("Consecutive resolution with same bitmap and theme must hit LRU cache", first, second)
    }
}

