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

// =========================================================================
// JNI bridge: declarations are shared by the JVM and Android targets.
// =========================================================================

internal object JniOpus {
    init {
        NativeLoader.load()
    }

    external fun nativeVersionString(): String

    external fun nativeStrerror(error: Int): String

    external fun nativeEncoderCreate(sampleRate: Int, channels: Int, application: Int): Long

    external fun nativeEncoderFree(handle: Long)

    external fun nativeEncoderReset(handle: Long): Int

    external fun nativeEncoderSampleRate(handle: Long): Int

    external fun nativeEncoderChannels(handle: Long): Int

    external fun nativeEncoderEncode(
        handle: Long,
        samples: ShortArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int

    external fun nativeEncoderEncodeFloat(
        handle: Long,
        samples: FloatArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int

    external fun nativeEncoderCtlInt(handle: Long, request: Int, value: Int): Int

    external fun nativeEncoderGetInt(handle: Long, request: Int): Int

    external fun nativeEncoderFinalRange(handle: Long): Long

    external fun nativeDecoderCreate(sampleRate: Int, channels: Int): Long

    external fun nativeDecoderFree(handle: Long)

    external fun nativeDecoderReset(handle: Long): Int

    external fun nativeDecoderSampleRate(handle: Long): Int

    external fun nativeDecoderChannels(handle: Long): Int

    external fun nativeDecoderDecode(
        handle: Long,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: ShortArray,
    ): Int

    external fun nativeDecoderDecodeFloat(
        handle: Long,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: FloatArray,
    ): Int

    external fun nativeDecoderCtlInt(handle: Long, request: Int, value: Int): Int

    external fun nativeDecoderGetInt(handle: Long, request: Int): Int

    external fun nativeDecoderFinalRange(handle: Long): Long

    external fun nativeDecoderGetNbSamples(handle: Long, packet: ByteArray): Int

    external fun nativePacketBandwidth(packet: ByteArray): Int

    external fun nativePacketSamplesPerFrame(packet: ByteArray, sampleRate: Int): Int

    external fun nativePacketChannels(packet: ByteArray): Int

    external fun nativePacketFrames(packet: ByteArray): Int

    external fun nativePacketSamples(packet: ByteArray, sampleRate: Int): Int

    external fun nativePacketHasLbrr(packet: ByteArray): Int

    external fun nativePacketParse(packet: ByteArray, sizes: IntArray, offsets: IntArray): Int

    external fun nativePacketPad(packet: ByteArray, length: Int, newLength: Int): Int

    external fun nativePacketUnpad(packet: ByteArray): Int

    external fun nativeRepacketizerCreate(): Long

    external fun nativeRepacketizerFree(handle: Long)

    external fun nativeRepacketizerReset(handle: Long): Int

    external fun nativeRepacketizerCat(handle: Long, packet: ByteArray): Int

    external fun nativeRepacketizerFrameCount(handle: Long): Int

    external fun nativeRepacketizerOutRange(handle: Long, begin: Int, end: Int, out: ByteArray): Int

    external fun nativeRepacketizerOut(handle: Long, out: ByteArray): Int

    external fun nativePcmSoftClip(pcm: FloatArray, frameSize: Int, channels: Int, memory: FloatArray): Int
}

internal actual class EncoderHandle actual constructor() {
    internal var pointer: Long = 0
}

internal actual class DecoderHandle actual constructor() {
    internal var pointer: Long = 0
}

internal actual class RepacketizerHandle actual constructor() {
    internal var pointer: Long = 0
}

internal actual object OpusNative {
    actual fun versionString(): String = JniOpus.nativeVersionString()

    actual fun strerror(error: Int): String = JniOpus.nativeStrerror(error)

    actual fun encoderCreate(sampleRate: Int, channels: Int, application: Int): EncoderHandle? {
        val pointer = JniOpus.nativeEncoderCreate(sampleRate, channels, application)
        if (pointer == 0L) return null
        return EncoderHandle().also { it.pointer = pointer }
    }

    actual fun encoderFree(handle: EncoderHandle) {
        JniOpus.nativeEncoderFree(handle.pointer)
        handle.pointer = 0
    }

    actual fun encoderReset(handle: EncoderHandle): Int = JniOpus.nativeEncoderReset(handle.pointer)

    actual fun encoderSampleRate(handle: EncoderHandle): Int =
        JniOpus.nativeEncoderSampleRate(handle.pointer)

    actual fun encoderChannels(handle: EncoderHandle): Int =
        JniOpus.nativeEncoderChannels(handle.pointer)

    actual fun encoderEncode(
        handle: EncoderHandle,
        samples: ShortArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int = JniOpus.nativeEncoderEncode(handle.pointer, samples, offset, frameSize, out)

    actual fun encoderEncodeFloat(
        handle: EncoderHandle,
        samples: FloatArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int = JniOpus.nativeEncoderEncodeFloat(handle.pointer, samples, offset, frameSize, out)

    actual fun encoderCtlInt(handle: EncoderHandle, request: Int, value: Int): Int =
        JniOpus.nativeEncoderCtlInt(handle.pointer, request, value)

    actual fun encoderGetInt(handle: EncoderHandle, request: Int): Int =
        JniOpus.nativeEncoderGetInt(handle.pointer, request)

    actual fun encoderFinalRange(handle: EncoderHandle): Long =
        JniOpus.nativeEncoderFinalRange(handle.pointer)

    actual fun decoderCreate(sampleRate: Int, channels: Int): DecoderHandle? {
        val pointer = JniOpus.nativeDecoderCreate(sampleRate, channels)
        if (pointer == 0L) return null
        return DecoderHandle().also { it.pointer = pointer }
    }

    actual fun decoderFree(handle: DecoderHandle) {
        JniOpus.nativeDecoderFree(handle.pointer)
        handle.pointer = 0
    }

    actual fun decoderReset(handle: DecoderHandle): Int = JniOpus.nativeDecoderReset(handle.pointer)

    actual fun decoderSampleRate(handle: DecoderHandle): Int =
        JniOpus.nativeDecoderSampleRate(handle.pointer)

    actual fun decoderChannels(handle: DecoderHandle): Int =
        JniOpus.nativeDecoderChannels(handle.pointer)

    actual fun decoderDecode(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: ShortArray,
    ): Int = JniOpus.nativeDecoderDecode(handle.pointer, packet, frameSize, fec, out)

    actual fun decoderDecodeFloat(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: FloatArray,
    ): Int = JniOpus.nativeDecoderDecodeFloat(handle.pointer, packet, frameSize, fec, out)

    actual fun decoderCtlInt(handle: DecoderHandle, request: Int, value: Int): Int =
        JniOpus.nativeDecoderCtlInt(handle.pointer, request, value)

    actual fun decoderGetInt(handle: DecoderHandle, request: Int): Int =
        JniOpus.nativeDecoderGetInt(handle.pointer, request)

    actual fun decoderFinalRange(handle: DecoderHandle): Long =
        JniOpus.nativeDecoderFinalRange(handle.pointer)

    actual fun decoderGetNbSamples(handle: DecoderHandle, packet: ByteArray): Int =
        JniOpus.nativeDecoderGetNbSamples(handle.pointer, packet)

    actual fun packetBandwidth(packet: ByteArray): Int = JniOpus.nativePacketBandwidth(packet)

    actual fun packetSamplesPerFrame(packet: ByteArray, sampleRate: Int): Int =
        JniOpus.nativePacketSamplesPerFrame(packet, sampleRate)

    actual fun packetChannels(packet: ByteArray): Int = JniOpus.nativePacketChannels(packet)

    actual fun packetFrames(packet: ByteArray): Int = JniOpus.nativePacketFrames(packet)

    actual fun packetSamples(packet: ByteArray, sampleRate: Int): Int =
        JniOpus.nativePacketSamples(packet, sampleRate)

    actual fun packetHasLbrr(packet: ByteArray): Int = JniOpus.nativePacketHasLbrr(packet)

    actual fun packetParse(packet: ByteArray, sizes: IntArray, offsets: IntArray): Int =
        JniOpus.nativePacketParse(packet, sizes, offsets)

    actual fun packetPad(packet: ByteArray, length: Int, newLength: Int): Int =
        JniOpus.nativePacketPad(packet, length, newLength)

    actual fun packetUnpad(packet: ByteArray): Int = JniOpus.nativePacketUnpad(packet)

    actual fun repacketizerCreate(): RepacketizerHandle? {
        val pointer = JniOpus.nativeRepacketizerCreate()
        if (pointer == 0L) return null
        return RepacketizerHandle().also { it.pointer = pointer }
    }

    actual fun repacketizerFree(handle: RepacketizerHandle) {
        JniOpus.nativeRepacketizerFree(handle.pointer)
        handle.pointer = 0
    }

    actual fun repacketizerReset(handle: RepacketizerHandle): Int =
        JniOpus.nativeRepacketizerReset(handle.pointer)

    actual fun repacketizerCat(handle: RepacketizerHandle, packet: ByteArray): Int =
        JniOpus.nativeRepacketizerCat(handle.pointer, packet)

    actual fun repacketizerFrameCount(handle: RepacketizerHandle): Int =
        JniOpus.nativeRepacketizerFrameCount(handle.pointer)

    actual fun repacketizerOutRange(
        handle: RepacketizerHandle,
        begin: Int,
        end: Int,
        out: ByteArray,
    ): Int = JniOpus.nativeRepacketizerOutRange(handle.pointer, begin, end, out)

    actual fun repacketizerOut(handle: RepacketizerHandle, out: ByteArray): Int =
        JniOpus.nativeRepacketizerOut(handle.pointer, out)

    actual fun pcmSoftClip(pcm: FloatArray, frameSize: Int, channels: Int, memory: FloatArray): Int =
        JniOpus.nativePcmSoftClip(pcm, frameSize, channels, memory)
}
