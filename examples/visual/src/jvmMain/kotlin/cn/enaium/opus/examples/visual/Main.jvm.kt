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

package cn.enaium.opus.examples.visual

import kotlin.system.exitProcess

/** Parses `--frames N`, `--channels N`, `--mute` and `--microphone` (all optional). */
private fun parseArgs(args: Array<String>): Args {
    var frames = Int.MAX_VALUE
    var mute = false
    var channels = 1
    var microphone = false
    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--frames" -> if (index + 1 < args.size) {
                frames = args[index + 1].toIntOrNull() ?: Int.MAX_VALUE
                index++
            }

            "--channels" -> if (index + 1 < args.size) {
                channels = args[index + 1].toIntOrNull() ?: 1
                index++
            }

            "--mute" -> mute = true
            "--microphone" -> microphone = true
        }
        index++
    }
    return Args(frames, mute, channels, microphone)
}

private class Args(val frames: Int, val mute: Boolean, val channels: Int, val microphone: Boolean)

/** Set on the relaunched JVM so a failure there cannot start another one. */
private const val RELAUNCH_MARKER = "OPUS_KMP_VISUAL_RELAUNCHED"

/** JVM entry point: `./gradlew :examples:visual:jvmRun`. */
fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (runVisualExample(parsed.frames, parsed.mute, parsed.channels, parsed.microphone)) return
    // macOS: a plain JVM does not own the first thread, which SDL needs to
    // create its window. Relaunch once with the flag rather than failing with
    // an instruction.
    if (isMacOs && System.getenv(RELAUNCH_MARKER) == null) {
        val status = relaunchOnFirstThread(args)
        if (status != null) exitProcess(status)
    }
    exitProcess(1)
}

private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

/**
 * Starts this entry point again in a child JVM launched with
 * `-XstartOnFirstThread`, forwarding stdio and the exit status.
 */
private fun relaunchOnFirstThread(args: Array<String>): Int? = try {
    val java = ProcessHandle.current().info().command().orElse(null)
        ?: "${System.getProperty("java.home")}/bin/java"
    val command = listOf(java, "-XstartOnFirstThread", "-cp", System.getProperty("java.class.path"), MAIN_CLASS) + args
    report("rerunning on the first thread: ${command.joinToString(" ")}")
    val child = ProcessBuilder(command)
        .inheritIO()
        .apply { environment()[RELAUNCH_MARKER] = "1" }
        .start()
    Runtime.getRuntime().addShutdownHook(Thread { child.destroy() })
    child.waitFor()
} catch (e: Exception) {
    report("could not rerun the example: $e")
    null
}

private const val MAIN_CLASS = "cn.enaium.opus.examples.visual.Main_jvmKt"
