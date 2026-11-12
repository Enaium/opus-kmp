/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.opus.examples.visual.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import cn.enaium.opus.examples.visual.runVisualExample
import cn.enaium.sdl.SDL
import org.libsdl.app.SDLActivity

/**
 * Android entry point.
 *
 * SDLActivity starts a dedicated SDL thread and calls [main] on it between
 * `nativeInitMainThread` and `nativeCleanupMainThread`, which is the same
 * contract the stock native `SDL_main` path uses. The shared commonMain UI
 * creates the window, runs the ImGui/ImPlot frame loop and exits when the
 * activity is finished.
 *
 * `sdl_jni` is the SDL3 + JNI bridge library from sdl-kmp-android-jvm.
 * libimgui_jni.so and libopus_jni.so are loaded by their own NativeLoaders
 * from the AAR jniLibs.
 */
class MainActivity : SDLActivity() {

    override fun getLibraries(): Array<String> = arrayOf("sdl_jni")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMicrophonePermission()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun main() {
        try {
            // Intent extras make automated runs deterministic:
            //   adb shell am start ... --ei channels 2 --ez microphone true
            runVisualExample(
                channels = intent?.getIntExtra("channels", 1) ?: 1,
                microphone = intent?.getBooleanExtra("microphone", false) ?: false,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "opus-kmp visual example failed", t)
        } finally {
            SDL.quit()
        }
    }

    /** The microphone source needs RECORD_AUDIO; the system dialog is shown once. */
    private fun requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST)
        }
    }

    /**
     * The SDL surface is edge-to-edge; hide the system bars so the ImGui
     * toolbar is not drawn underneath the status bar.
     */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private companion object {
        const val TAG = "OpusVisualExample"
        const val RECORD_AUDIO_REQUEST = 1
    }
}
