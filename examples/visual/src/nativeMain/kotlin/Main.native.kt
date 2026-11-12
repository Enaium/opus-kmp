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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import cn.enaium.opus.examples.visual.runVisualExample
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.system.exitProcess

/**
 * Native entry point. Kotlin/Native looks for `main` in the root package.
 *
 * `OPUS_KMP_FRAMES` bounds the run, which is what the automated (headless)
 * checks use.
 *
 * ```bash
 * ./examples/visual/build/bin/macosArm64/debugExecutable/visual.kexe
 * OPUS_KMP_FRAMES=120 SDL_VIDEO_DRIVER=dummy ./examples/visual/build/bin/linuxX64/releaseExecutable/visual.kexe
 * ```
 */
fun main() {
    val frames = getenv("OPUS_KMP_FRAMES")?.toKString()?.toIntOrNull() ?: Int.MAX_VALUE
    val mute = getenv("OPUS_KMP_MUTE")?.toKString() == "1"
    val channels = getenv("OPUS_KMP_CHANNELS")?.toKString()?.toIntOrNull() ?: 1
    val microphone = getenv("OPUS_KMP_MICROPHONE")?.toKString() == "1"
    if (!runVisualExample(frames, mute, channels, microphone)) {
        exitProcess(1)
    }
}
