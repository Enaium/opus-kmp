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

import kotlin.math.PI
import kotlin.math.sin

/** Converts 16-bit PCM to the `-1..1` range the plots use. */
internal fun Short.toAmplitude(): Float = this / 32768f

/** Formats [value] with [decimals] digits after the point. */
internal fun fixed(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(value * factor).toLong()
    val magnitude = if (scaled < 0) -scaled else scaled
    val whole = magnitude / factor
    val fraction = (magnitude % factor).toString().padStart(decimals, '0')
    return "${if (scaled < 0) "-" else ""}$whole.$fraction"
}

internal fun report(message: String) {
    println(message)
}

/**
 * A deterministic, speech-like source: a harmonic tone gated by a syllable
 * envelope, with a slow vibrato and a little noise. The output is identical
 * for a given seed, so headless runs are comparable.
 */
internal class Signal(
    private val sampleRate: Int,
    private val frequency: Double,
    private val seed: Int = 0x0F05,
) {
    private var phase = 0.0
    private var vibratoPhase = 0.0
    private var syllablePhase = 0.0
    private var noiseState = seed

    /** Fills and returns one mono frame. Two sources provide stereo channels. */
    fun nextPcm(
        frameSize: Int,
        amplitude: Float,
        muted: Boolean = false,
    ): ShortArray {
        val out = ShortArray(frameSize)
        if (muted) return out
        val step = 2.0 * PI * frequency / sampleRate
        val vibratoStep = 2.0 * PI * 5.5 / sampleRate
        val syllableStep = 2.0 * PI * 3.2 / sampleRate
        for (index in 0 until frameSize) {
            phase += step
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
            vibratoPhase += vibratoStep
            if (vibratoPhase >= 2.0 * PI) vibratoPhase -= 2.0 * PI
            syllablePhase += syllableStep
            if (syllablePhase >= 2.0 * PI) syllablePhase -= 2.0 * PI

            val envelope = 0.45 + 0.55 * (0.5 + 0.5 * sin(syllablePhase))
            val vibrato = 1.0 + 0.01 * sin(vibratoPhase)
            val harmonic = sin(phase * vibrato) +
                0.35 * sin(phase * 2.0 * vibrato) +
                0.15 * sin(phase * 3.0 * vibrato)

            noiseState = noiseState * 1664525 + 1013904223
            val noise = ((noiseState ushr 16) and 0x7FFF) / 32768.0 - 0.5

            val value = (harmonic * envelope * amplitude + noise * 0.004) * 32767.0
            out[index] = value.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
