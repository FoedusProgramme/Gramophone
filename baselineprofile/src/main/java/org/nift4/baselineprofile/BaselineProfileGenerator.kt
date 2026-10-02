package org.nift4.baselineprofile

import android.annotation.SuppressLint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val TAG = "BaselineProfileGenerator"

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @SuppressLint("SdCardPath")
    @Test
    fun generate() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val testDir = File(musicDir, "gp_baseline")
        val files = listOf("test1.mp3", "test2.flac", "test3.wav", "test4.ogg", "test5.opus")
        if (testDir.exists())
            testDir.deleteRecursively()
        testDir.mkdir()
        for (i in files) {
            ctx.assets.open(i).use {
                try {
                    FileOutputStream(File(testDir, i)).use { o ->
                        it.copyTo(o)
                    }
                } catch (e: FileNotFoundException) {
                    // scoped storage, files are there, ignore it
                }
            }
        }
        MediaScannerConnection.scanFile(
            ctx,
            files.map { File(testDir, it).absolutePath }.toTypedArray(),
            null
        ) { _: String, _: Uri -> }
        val ae = InstrumentationRegistry.getInstrumentation().uiAutomation
        ae.executeShellCommand("pm clear org.akanework.gramophone")
        Thread.sleep(1000) // let device settle a bit
        // The application id for the running build variant is read from the instrumentation arguments.
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll through your most important UI.

            // Start default activity for your app
            pressHome()
            ae.executeShellCommand("pm grant org.akanework.gramophone android.permission.READ_MEDIA_AUDIO")
            startActivityAndWait()

            // More interactions to optimize advanced journeys of your app.
            // 1. Wait until the content is asynchronously loaded
            device.wait(Until.findObject(By.text("5 Songs")), 30L)
            // 2. Scroll the tabs
            device.swipe(800, 1000, 200, 1000, 20)
            device.waitForIdle(20L)
            device.swipe(200, 1000, 800, 1000, 20)
            device.waitForIdle(20L)
            // 3. Play a song. With a real library on the device the test songs may be off screen,
            // so scroll to them, and log any that can't be found instead of failing.
            var nowPlaying: String? = null
            for (song in listOf("Ending / Credits", "test3", "Level 1", "Level 2", "Level 3")) {
                if (clickText(song)) nowPlaying = song
                Thread.sleep(2000)
            }

            // 4. Open the test album and artist pages (the covers and their color schemes), then
            // scroll them and go back. The tabs are found by their English labels.
            openEntryOfTab("Albums", "Retro Game Music Pack")
            openEntryOfTab("Artists", "Juhani Junkala")

            // 5. Open the settings from the home bar's overflow menu, then a few of their pages
            // and back, so the settings and their glass bar are compiled ahead too.
            openSettings()

            // 6. Expand the player, open the lyrics, then the queue, and close it all again.
            // The mini player's label starts with the playing song's title and sits lowest.
            val miniPlayer = nowPlaying?.let { title ->
                device.findObjects(By.textStartsWith(title)).maxByOrNull { it.visibleBounds.bottom }
            }
            if (miniPlayer == null) {
                Log.w(TAG, "Mini player not found, skipping the full player")
                return@collect
            }
            miniPlayer.click()
            Thread.sleep(1500)
            // The full player's lyrics and queue buttons, by their content descriptions.
            val lyricsButton = device.wait(Until.findObject(By.desc("Lyrics")), 3000L)
            if (lyricsButton == null) {
                Log.w(TAG, "Full player lyrics button not found, skipping lyrics and queue")
            } else {
                lyricsButton.click()
                Thread.sleep(3000)
                device.pressBack()
                Thread.sleep(1000)
                device.findObject(By.desc("Current playlist"))?.click()
                    ?: Log.w(TAG, "Queue button not found, skipping it")
                Thread.sleep(1500)
                scrollDown()
                Thread.sleep(800)
                device.pressBack()
                Thread.sleep(1000)
            }
            device.pressBack()
            Thread.sleep(1000)

            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator
        }
    }
}

/** Swipes up across the middle of the screen, scrolling a list down. */
private fun MacrobenchmarkScope.scrollDown() {
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
}

/** Swipes down across the middle of the screen, scrolling a list up. */
private fun MacrobenchmarkScope.scrollUp() {
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 20)
}

/** The settings pages the journey opens, by their titles on the settings' top page. */
private val SETTINGS_PAGES = listOf("Appearance", "Player UI", "Behavior", "Audio")

/** Opens the settings, each of [SETTINGS_PAGES] in turn, and goes back to the home. */
private fun MacrobenchmarkScope.openSettings() {
    val more = device.wait(Until.findObject(By.desc("More")), 3000L)
    if (more == null) {
        Log.w(TAG, "Home overflow button not found, skipping the settings")
        return
    }
    more.click()
    device.waitForIdle(500L)
    // The menu item is on screen, so this finds it without scrolling.
    if (!clickText("Settings")) return
    Thread.sleep(1500)
    for (page in SETTINGS_PAGES) {
        if (!clickText(page)) continue
        Thread.sleep(1500)
        scrollDown()
        Thread.sleep(500)
        device.pressBack()
        Thread.sleep(1000)
    }
    device.pressBack()
    Thread.sleep(1000)
}

/** Clicks the view showing [text], scrolling down to find it. False if it isn't found. */
private fun MacrobenchmarkScope.clickText(text: String): Boolean {
    repeat(8) {
        device.findObject(By.text(text))?.let { it.click(); return true }
        scrollDown()
        device.waitForIdle(200L)
    }
    Log.w(TAG, "\"$text\" not found, skipping it")
    return false
}

/** The home tab labeled [tab], scrolling up to bring back a collapsed app bar. */
private fun MacrobenchmarkScope.findTab(tab: String): UiObject2? {
    repeat(3) {
        device.findObject(By.text(tab))?.let { return it }
        // Scrolling a list up brings back a collapsed app bar with its tabs.
        scrollUp()
        device.waitForIdle(200L)
    }
    return null
}

/** Opens [entry] of the home tab [tab], scrolls its page down and up, and goes back. */
private fun MacrobenchmarkScope.openEntryOfTab(tab: String, entry: String) {
    val tabObject = findTab(tab)
    if (tabObject == null) {
        Log.w(TAG, "Tab \"$tab\" not found, skipping it")
        return
    }
    tabObject.click()
    Thread.sleep(1000)
    if (!clickText(entry)) return
    Thread.sleep(1500)
    scrollDown()
    Thread.sleep(800)
    scrollUp()
    Thread.sleep(800)
    device.pressBack()
    Thread.sleep(1000)
}
