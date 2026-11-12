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
 * Combines and splits Opus packets without re-encoding.
 *
 * Feed packets with [cat], inspect [frameCount], then emit a packet with [out]
 * or [outRange]. Every input packet must have the same TOC configuration
 * (bandwidth, frame duration, channel count), as required by Opus.
 */
class OpusRepacketizer : AutoCloseable {

    private val handle: RepacketizerHandle =
        OpusNative.repacketizerCreate()
            ?: throw OpusException(-7, "Opus could not allocate a repacketizer")

    private var closed = false

    /** Number of frames accumulated so far. */
    val frameCount: Int
        get() {
            checkOpen()
            return OpusNative.repacketizerFrameCount(handle)
        }

    /** Adds every frame of [packet] to the accumulator. */
    fun cat(packet: ByteArray) {
        checkOpen()
        require(packet.isNotEmpty()) { "packet must not be empty" }
        val code = OpusNative.repacketizerCat(handle, packet)
        if (code < 0) Opus.throwFailure(code)
    }

    /** Emits every accumulated frame as one packet. */
    fun out(maxBytes: Int = Opus.MAX_REPACKETIZER_BYTES): ByteArray =
        outRange(0, frameCount, maxBytes)

    /**
     * Emits frames in `[begin, end)` as one packet. `outRange(i, i + 1)`
     * extracts a single frame.
     */
    fun outRange(
        begin: Int,
        end: Int,
        maxBytes: Int = Opus.MAX_REPACKETIZER_BYTES,
    ): ByteArray {
        checkOpen()
        require(begin >= 0 && end > begin && end <= frameCount) {
            "range [$begin, $end) is outside the accumulated $frameCount frame(s)"
        }
        require(maxBytes > 0) { "maxBytes must be positive" }
        val out = ByteArray(maxBytes)
        val size = OpusNative.repacketizerOutRange(handle, begin, end, out)
        if (size < 0) Opus.throwFailure(size)
        return out.copyOf(size)
    }

    /** Clears the accumulated frames. */
    fun reset() {
        checkOpen()
        val code = OpusNative.repacketizerReset(handle)
        if (code < 0) Opus.throwFailure(code)
    }

    override fun close() {
        if (closed) return
        closed = true
        OpusNative.repacketizerFree(handle)
    }

    private fun checkOpen() {
        check(!closed) { "repacketizer is closed" }
    }
}
