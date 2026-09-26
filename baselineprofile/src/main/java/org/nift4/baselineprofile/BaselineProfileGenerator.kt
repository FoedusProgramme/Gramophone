package org.nift4.baselineprofile

import android.annotation.SuppressLint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

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
            // so scroll to them, and skip any that can't be found instead of failing.
            fun clickText(text: String) {
                repeat(8) {
                    device.findObject(By.text(text))?.let { it.click(); return }
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                        device.displayWidth / 2, device.displayHeight / 4, 20)
                    device.waitForIdle(200L)
                }
            }
            clickText("Ending / Credits")
            Thread.sleep(2000)
            clickText("test3")
            Thread.sleep(2000)
            clickText("Level 1")
            Thread.sleep(2000)
            clickText("Level 2")
            Thread.sleep(2000)
            clickText("Level 3")
            Thread.sleep(2000)

            // 4. Open an album and an artist page (the covers and their color schemes), then scroll
            // them and go back. Works in any language as it picks the tabs by position.
            val w = device.displayWidth
            val h = device.displayHeight
            fun openFirstEntryOfTab(tabIndex: Int) {
                val tabs = device.findObjects(By.clazz("android.view.View").clickable(true))
                device.click(w / 2, h / 2) // dismiss anything focused
                device.waitForIdle(200L)
                tabs.getOrNull(tabIndex)?.click()
                Thread.sleep(1000)
                device.click(w / 4, h * 3 / 10)
                Thread.sleep(1500)
                device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
                Thread.sleep(800)
                device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 20)
                Thread.sleep(800)
                device.pressBack()
                Thread.sleep(1000)
            }
            openFirstEntryOfTab(1)
            openFirstEntryOfTab(2)

            // 5. Expand the player, open the lyrics, then the queue, and close it all again.
            device.click(w / 2, h * 935 / 1000)
            Thread.sleep(1500)
            device.click(w * 145 / 1000, h * 944 / 1000)
            Thread.sleep(3000)
            device.pressBack()
            Thread.sleep(1000)
            device.click(w * 852 / 1000, h * 944 / 1000)
            Thread.sleep(1500)
            device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
            Thread.sleep(800)
            device.pressBack()
            Thread.sleep(1000)
            device.pressBack()
            Thread.sleep(1000)


            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator
        }
    }
}