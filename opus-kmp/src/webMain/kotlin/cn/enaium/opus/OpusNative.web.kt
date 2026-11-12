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

internal actual class EncoderHandle actual constructor() {
    /** WebAssembly pointer, always 32-bit. */
    internal var pointer: Int = 0
}

internal actual class DecoderHandle actual constructor() {
    internal var pointer: Int = 0
}

internal actual class RepacketizerHandle actual constructor() {
    internal var pointer: Int = 0
}

internal actual object OpusNative {
    private var initialised = false

    private fun ensureInitialised() {
        if (!initialised) {
            webInit()
            initialised = true
        }
    }

    actual fun versionString(): String {
        ensureInitialised()
        return webVersionString()
    }

    actual fun strerror(error: Int): String {
        ensureInitialised()
        return webStrerror(error)
    }

    actual fun encoderCreate(sampleRate: Int, channels: Int, application: Int): EncoderHandle? {
        ensureInitialised()
        val pointer = webEncoderCreate(sampleRate, channels, application)
        if (pointer == 0) return null
        return EncoderHandle().also { it.pointer = pointer }
    }

    actual fun encoderFree(handle: EncoderHandle) {
        ensureInitialised()
        webEncoderFree(handle.pointer)
        handle.pointer = 0
    }

    actual fun encoderReset(handle: EncoderHandle): Int {
        ensureInitialised()
        return webEncoderReset(handle.pointer)
    }

    actual fun encoderSampleRate(handle: EncoderHandle): Int {
        ensureInitialised()
        return webEncoderSampleRate(handle.pointer)
    }

    actual fun encoderChannels(handle: EncoderHandle): Int {
        ensureInitialised()
        return webEncoderChannels(handle.pointer)
    }

    actual fun encoderEncode(
        handle: EncoderHandle,
        samples: ShortArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int {
        ensureInitialised()
        val channels = webEncoderChannels(handle.pointer)
        if (channels <= 0 || frameSize <= 0) return OK_INVALID
        val payload = OpusWebPacking.shortsToBase64(samples, offset, frameSize * channels)
        val (code, result) = OpusWebPacking.splitResult(
            webEncoderEncode(handle.pointer, payload, frameSize, out.size),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            OpusWebPacking.bytesFromBase64(result).copyInto(out)
        }
        return code
    }

    actual fun encoderEncodeFloat(
        handle: EncoderHandle,
        samples: FloatArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int {
        ensureInitialised()
        val channels = webEncoderChannels(handle.pointer)
        if (channels <= 0 || frameSize <= 0) return OK_INVALID
        val payload = OpusWebPacking.floatsToBase64(samples, offset, frameSize * channels)
        val (code, result) = OpusWebPacking.splitResult(
            webEncoderEncodeFloat(handle.pointer, payload, frameSize, out.size),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            OpusWebPacking.bytesFromBase64(result).copyInto(out)
        }
        return code
    }

    actual fun encoderCtlInt(handle: EncoderHandle, request: Int, value: Int): Int {
        ensureInitialised()
        return webEncoderCtlInt(handle.pointer, request, value)
    }

    actual fun encoderGetInt(handle: EncoderHandle, request: Int): Int {
        ensureInitialised()
        return webEncoderGetInt(handle.pointer, request)
    }

    actual fun encoderFinalRange(handle: EncoderHandle): Long {
        ensureInitialised()
        return webEncoderFinalRange(handle.pointer).toLongOrNull() ?: 0L
    }

    actual fun decoderCreate(sampleRate: Int, channels: Int): DecoderHandle? {
        ensureInitialised()
        val pointer = webDecoderCreate(sampleRate, channels)
        if (pointer == 0) return null
        return DecoderHandle().also { it.pointer = pointer }
    }

    actual fun decoderFree(handle: DecoderHandle) {
        ensureInitialised()
        webDecoderFree(handle.pointer)
        handle.pointer = 0
    }

    actual fun decoderReset(handle: DecoderHandle): Int {
        ensureInitialised()
        return webDecoderReset(handle.pointer)
    }

    actual fun decoderSampleRate(handle: DecoderHandle): Int {
        ensureInitialised()
        return webDecoderSampleRate(handle.pointer)
    }

    actual fun decoderChannels(handle: DecoderHandle): Int {
        ensureInitialised()
        return webDecoderChannels(handle.pointer)
    }

    actual fun decoderDecode(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: ShortArray,
    ): Int {
        ensureInitialised()
        val channels = webDecoderChannels(handle.pointer)
        if (channels <= 0 || frameSize <= 0) return OK_INVALID
        val encoded = if (packet == null || packet.isEmpty()) "" else OpusWebPacking.bytesToBase64(packet)
        val (code, result) = OpusWebPacking.splitResult(
            webDecoderDecode(handle.pointer, encoded, frameSize, fec, out.size),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            val samples = OpusWebPacking.shortsFromBase64(result)
            samples.copyInto(out, 0, 0, minOf(samples.size, out.size))
        }
        return code
    }

    actual fun decoderDecodeFloat(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: FloatArray,
    ): Int {
        ensureInitialised()
        val channels = webDecoderChannels(handle.pointer)
        if (channels <= 0 || frameSize <= 0) return OK_INVALID
        val encoded = if (packet == null || packet.isEmpty()) "" else OpusWebPacking.bytesToBase64(packet)
        val (code, result) = OpusWebPacking.splitResult(
            webDecoderDecodeFloat(handle.pointer, encoded, frameSize, fec, out.size),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            val samples = OpusWebPacking.floatsFromBase64(result)
            samples.copyInto(out, 0, 0, minOf(samples.size, out.size))
        }
        return code
    }

    actual fun decoderCtlInt(handle: DecoderHandle, request: Int, value: Int): Int {
        ensureInitialised()
        return webDecoderCtlInt(handle.pointer, request, value)
    }

    actual fun decoderGetInt(handle: DecoderHandle, request: Int): Int {
        ensureInitialised()
        return webDecoderGetInt(handle.pointer, request)
    }

    actual fun decoderFinalRange(handle: DecoderHandle): Long {
        ensureInitialised()
        return webDecoderFinalRange(handle.pointer).toLongOrNull() ?: 0L
    }

    actual fun decoderGetNbSamples(handle: DecoderHandle, packet: ByteArray): Int {
        ensureInitialised()
        return webDecoderGetNbSamples(handle.pointer, OpusWebPacking.bytesToBase64(packet))
    }

    actual fun packetBandwidth(packet: ByteArray): Int {
        ensureInitialised()
        return webPacketBandwidth(OpusWebPacking.bytesToBase64(packet))
    }

    actual fun packetSamplesPerFrame(packet: ByteArray, sampleRate: Int): Int {
        ensureInitialised()
        return webPacketSamplesPerFrame(OpusWebPacking.bytesToBase64(packet), sampleRate)
    }

    actual fun packetChannels(packet: ByteArray): Int {
        ensureInitialised()
        return webPacketChannels(OpusWebPacking.bytesToBase64(packet))
    }

    actual fun packetFrames(packet: ByteArray): Int {
        ensureInitialised()
        return webPacketFrames(OpusWebPacking.bytesToBase64(packet))
    }

    actual fun packetSamples(packet: ByteArray, sampleRate: Int): Int {
        ensureInitialised()
        return webPacketSamples(OpusWebPacking.bytesToBase64(packet), sampleRate)
    }

    actual fun packetHasLbrr(packet: ByteArray): Int {
        ensureInitialised()
        return webPacketHasLbrr(OpusWebPacking.bytesToBase64(packet))
    }

    actual fun packetParse(packet: ByteArray, sizes: IntArray, offsets: IntArray): Int {
        ensureInitialised()
        val result = webPacketParse(OpusWebPacking.bytesToBase64(packet))
        val parts = result.split(':')
        if (parts.size != 3) return OK_INVALID
        val count = parts[0].toIntOrNull() ?: return OK_INVALID
        if (count < 0) return count
        if (count == 0) return 0
        val sizeValues = parts[1].split(',').mapNotNull { it.toIntOrNull() }
        val offsetValues = parts[2].split(',').mapNotNull { it.toIntOrNull() }
        if (sizeValues.size < count || offsetValues.size < count) return OK_INVALID
        val available = minOf(count, sizes.size, offsets.size)
        for (index in 0 until available) {
            sizes[index] = sizeValues[index]
            offsets[index] = offsetValues[index]
        }
        return available
    }

    actual fun packetPad(packet: ByteArray, length: Int, newLength: Int): Int {
        ensureInitialised()
        val (code, result) = OpusWebPacking.splitResult(
            webPacketPad(OpusWebPacking.bytesToBase64(packet), length, newLength),
        ) ?: return OK_INVALID
        if (code >= 0 && result.isNotEmpty()) {
            OpusWebPacking.bytesFromBase64(result).copyInto(packet)
        }
        return code
    }

    actual fun packetUnpad(packet: ByteArray): Int {
        ensureInitialised()
        val (code, result) = OpusWebPacking.splitResult(
            webPacketUnpad(OpusWebPacking.bytesToBase64(packet)),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            OpusWebPacking.bytesFromBase64(result).copyInto(packet)
        }
        return code
    }

    actual fun repacketizerCreate(): RepacketizerHandle? {
        ensureInitialised()
        val pointer = webRepacketizerCreate()
        if (pointer == 0) return null
        return RepacketizerHandle().also { it.pointer = pointer }
    }

    actual fun repacketizerFree(handle: RepacketizerHandle) {
        ensureInitialised()
        webRepacketizerFree(handle.pointer)
        handle.pointer = 0
    }

    actual fun repacketizerReset(handle: RepacketizerHandle): Int {
        ensureInitialised()
        return webRepacketizerReset(handle.pointer)
    }

    actual fun repacketizerCat(handle: RepacketizerHandle, packet: ByteArray): Int {
        ensureInitialised()
        return webRepacketizerCat(handle.pointer, OpusWebPacking.bytesToBase64(packet))
    }

    actual fun repacketizerFrameCount(handle: RepacketizerHandle): Int {
        ensureInitialised()
        return webRepacketizerFrameCount(handle.pointer)
    }

    actual fun repacketizerOutRange(
        handle: RepacketizerHandle,
        begin: Int,
        end: Int,
        out: ByteArray,
    ): Int {
        ensureInitialised()
        val (code, result) = OpusWebPacking.splitResult(
            webRepacketizerOutRange(handle.pointer, begin, end, out.size),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            OpusWebPacking.bytesFromBase64(result).copyInto(out)
        }
        return code
    }

    actual fun repacketizerOut(handle: RepacketizerHandle, out: ByteArray): Int {
        ensureInitialised()
        val (code, result) = OpusWebPacking.splitResult(
            webRepacketizerOut(handle.pointer, out.size),
        ) ?: return OK_INVALID
        if (code > 0 && result.isNotEmpty()) {
            OpusWebPacking.bytesFromBase64(result).copyInto(out)
        }
        return code
    }

    actual fun pcmSoftClip(pcm: FloatArray, frameSize: Int, channels: Int, memory: FloatArray): Int {
        ensureInitialised()
        val result = webPcmSoftClip(
            OpusWebPacking.floatsToBase64(pcm),
            frameSize,
            channels,
            OpusWebPacking.floatsToBase64(memory),
        )
        val parts = result.split(':')
        if (parts.size < 3) return OK_INVALID
        val code = parts[0].toIntOrNull() ?: return OK_INVALID
        if (code >= 0) {
            OpusWebPacking.floatsFromBase64(parts[1]).copyInto(pcm)
            OpusWebPacking.floatsFromBase64(parts[2]).copyInto(memory)
        }
        return code
    }
}
