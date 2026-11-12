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

package cn.enaium.opus

/**
 * Stateless inspection and framing helpers for Opus packets.
 *
 * These read only the TOC and framing bytes; they never decode audio and need
 * no encoder/decoder state.
 */
object OpusPacket {

    /** Bandwidth announced by the packet's TOC byte. */
    fun bandwidth(packet: ByteArray): OpusBandwidth {
        requireNotEmpty(packet)
        val value = OpusNative.packetBandwidth(packet)
        if (value < 0) Opus.throwFailure(value)
        return OpusBandwidth.of(value)
    }

    /** Samples per channel in one frame of the packet at [sampleRate]. */
    fun samplesPerFrame(packet: ByteArray, sampleRate: Int): Int {
        requireNotEmpty(packet)
        val value = OpusNative.packetSamplesPerFrame(packet, sampleRate)
        if (value < 0) Opus.throwFailure(value)
        return value
    }

    /** Number of channels coded in the packet (1 or 2). */
    fun channels(packet: ByteArray): Int {
        requireNotEmpty(packet)
        val value = OpusNative.packetChannels(packet)
        if (value < 0) Opus.throwFailure(value)
        return value
    }

    /** Number of frames in the packet. */
    fun frameCount(packet: ByteArray): Int {
        requireNotEmpty(packet)
        val value = OpusNative.packetFrames(packet)
        if (value < 0) Opus.throwFailure(value)
        return value
    }

    /** Total samples per channel in the packet at [sampleRate]. */
    fun sampleCount(packet: ByteArray, sampleRate: Int): Int {
        requireNotEmpty(packet)
        val value = OpusNative.packetSamples(packet, sampleRate)
        if (value < 0) Opus.throwFailure(value)
        return value
    }

    /** Whether the packet contains LBRR (in-band FEC) frames. */
    fun hasLbrr(packet: ByteArray): Boolean {
        requireNotEmpty(packet)
        val value = OpusNative.packetHasLbrr(packet)
        if (value < 0) Opus.throwFailure(value)
        return value != 0
    }

    /** Splits the packet into its individual frames, in order. */
    fun frames(packet: ByteArray): List<ByteArray> {
        requireNotEmpty(packet)
        val sizes = IntArray(Opus.MAX_FRAMES_PER_PACKET)
        val offsets = IntArray(Opus.MAX_FRAMES_PER_PACKET)
        val count = OpusNative.packetParse(packet, sizes, offsets)
        if (count < 0) Opus.throwFailure(count)
        return List(count) { index ->
            packet.copyOfRange(offsets[index], offsets[index] + sizes[index])
        }
    }

    /** Pads the packet to exactly [newLength] bytes and returns the copy. */
    fun pad(packet: ByteArray, newLength: Int): ByteArray {
        require(newLength >= packet.size) {
            "newLength $newLength is shorter than the ${packet.size}-byte packet"
        }
        require(newLength >= 1) { "newLength must be positive" }
        val out = ByteArray(newLength)
        packet.copyInto(out)
        val code = OpusNative.packetPad(out, packet.size, newLength)
        if (code < 0) Opus.throwFailure(code)
        return out
    }

    /** Removes any padding and returns the shorter copy. */
    fun unpad(packet: ByteArray): ByteArray {
        requireNotEmpty(packet)
        val copy = packet.copyOf()
        val size = OpusNative.packetUnpad(copy)
        if (size < 0) Opus.throwFailure(size)
        return copy.copyOf(size)
    }

    private fun requireNotEmpty(packet: ByteArray) {
        require(packet.isNotEmpty()) { "packet must not be empty" }
    }
}
