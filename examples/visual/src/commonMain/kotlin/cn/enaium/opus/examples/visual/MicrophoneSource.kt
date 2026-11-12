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

import cn.enaium.audio.AudioBuffer
import cn.enaium.audio.AudioFormat
import cn.enaium.audio.AudioInput
import cn.enaium.audio.AudioRingBuffer
import cn.enaium.audio.AudioSystem
import cn.enaium.audio.SampleFormat
import cn.enaium.audio.audioSystem

/**
 * Microphone capture through audio-io-kmp.
 *
 * The device is opened once at the requested sample rate as mono 16-bit PCM.
 * A ring buffer absorbs the difference between the callback/backend pace and
 * the render loop, which polls all available complete frames each UI frame.
 * On every platform the same code path is used: Core Audio on macOS, ALSA on
 * Linux, WASAPI on Windows, AudioRecord on Android.
 */
internal class MicrophoneSource {

    private var system: AudioSystem? = null
    private var input: AudioInput? = null
    private var buffer: AudioBuffer? = null
    private var ring: AudioRingBuffer? = null

    /** Chosen device, or null before the stream is opened. */
    var deviceName: String? = null
        private set

    /** Why the last [open] failed, or null after a successful open. */
    var error: String? = null
        private set

    val isOpen: Boolean get() = input != null

    /** Opens the default capture device as mono PCM_S16. Returns false on failure. */
    fun open(sampleRate: Int, frameSize: Int): Boolean {
        close()
        return try {
            val format = AudioFormat(sampleRate, 1, SampleFormat.PCM_S16)
            val openedSystem = audioSystem()
            val openedInput = openedSystem.openInput(
                format,
                bufferFrames = maxOf(frameSize * 2, 1024),
            )
            if (openedInput.format.channelCount != 1 || openedInput.format.sampleRate != sampleRate) {
                error = "device opened as ${openedInput.format}, expected $sampleRate Hz mono PCM_S16"
                openedInput.close()
                openedSystem.close()
                return false
            }
            openedInput.start()
            system = openedSystem
            input = openedInput
            buffer = AudioBuffer(openedInput.format, maxOf(4096, frameSize * 2))
            ring = AudioRingBuffer(openedInput.format, sampleRate)
            deviceName = openedInput.device?.name ?: "system default"
            error = null
            true
        } catch (t: Throwable) {
            close()
            error = t.message ?: t::class.simpleName ?: "microphone open failed"
            false
        }
    }

    /**
     * Drains the device and returns up to eight complete mono frames of
     * [frameSize] samples. More than that would make one UI frame process an
     * unbounded backlog; excess audio stays in the ring and is dropped by the
     * bounded writer if the UI cannot keep up.
     */
    fun poll(frameSize: Int): List<ShortArray> {
        val input = input ?: return emptyList()
        val buffer = buffer ?: return emptyList()
        val ring = ring ?: return emptyList()

        while (true) {
            val frames = input.readNonBlocking(buffer)
            if (frames <= 0) break
            ring.write(buffer.shorts(), frames)
        }

        val available = ring.availableToRead / frameSize
        if (available <= 0) return emptyList()
        return List(minOf(available, 8)) {
            val frame = ShortArray(frameSize)
            if (ring.read(frame, frameSize) != frameSize) return emptyList()
            frame
        }
    }

    /** Number of frames currently waiting in the ring, for the UI status line. */
    fun backlogFrames(): Int = ring?.availableToRead ?: 0

    fun close() {
        runCatching { input?.close() }
        runCatching { system?.close() }
        input = null
        system = null
        buffer = null
        ring = null
        deviceName = null
    }
}
