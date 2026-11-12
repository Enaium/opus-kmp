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

/** The shim rejected an argument before it reached Opus. */
internal const val OK_INVALID: Int = -1000

/** Opaque encoder state owned by the native library. */
internal expect class EncoderHandle()

/** Opaque decoder state owned by the native library. */
internal expect class DecoderHandle()

/** Opaque repacketizer state owned by the native library. */
internal expect class RepacketizerHandle()

/**
 * Bridge to the C shim, implemented for JNI, Kotlin/Native cinterop and the
 * WebAssembly module. Every implementation follows the contract from
 * `jni/include/opus_kmp.h`:
 *
 * - `encoderEncode` returns the packet size or a negative Opus error code;
 * - `decoderDecode` returns samples per channel or a negative Opus error code;
 * - buffers are supplied by Kotlin and never outlive the call;
 * - handles are never reused after their `free` call.
 */
internal expect object OpusNative {
    fun versionString(): String

    fun strerror(error: Int): String

    fun encoderCreate(sampleRate: Int, channels: Int, application: Int): EncoderHandle?

    fun encoderFree(handle: EncoderHandle)

    fun encoderReset(handle: EncoderHandle): Int

    fun encoderSampleRate(handle: EncoderHandle): Int

    fun encoderChannels(handle: EncoderHandle): Int

    fun encoderEncode(
        handle: EncoderHandle,
        samples: ShortArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int

    fun encoderEncodeFloat(
        handle: EncoderHandle,
        samples: FloatArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int

    fun encoderCtlInt(handle: EncoderHandle, request: Int, value: Int): Int

    fun encoderGetInt(handle: EncoderHandle, request: Int): Int

    fun encoderFinalRange(handle: EncoderHandle): Long

    fun decoderCreate(sampleRate: Int, channels: Int): DecoderHandle?

    fun decoderFree(handle: DecoderHandle)

    fun decoderReset(handle: DecoderHandle): Int

    fun decoderSampleRate(handle: DecoderHandle): Int

    fun decoderChannels(handle: DecoderHandle): Int

    fun decoderDecode(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: ShortArray,
    ): Int

    fun decoderDecodeFloat(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: FloatArray,
    ): Int

    fun decoderCtlInt(handle: DecoderHandle, request: Int, value: Int): Int

    fun decoderGetInt(handle: DecoderHandle, request: Int): Int

    fun decoderFinalRange(handle: DecoderHandle): Long

    fun decoderGetNbSamples(handle: DecoderHandle, packet: ByteArray): Int

    fun packetBandwidth(packet: ByteArray): Int

    fun packetSamplesPerFrame(packet: ByteArray, sampleRate: Int): Int

    fun packetChannels(packet: ByteArray): Int

    fun packetFrames(packet: ByteArray): Int

    fun packetSamples(packet: ByteArray, sampleRate: Int): Int

    fun packetHasLbrr(packet: ByteArray): Int

    fun packetParse(packet: ByteArray, sizes: IntArray, offsets: IntArray): Int

    fun packetPad(packet: ByteArray, length: Int, newLength: Int): Int

    fun packetUnpad(packet: ByteArray): Int

    fun repacketizerCreate(): RepacketizerHandle?

    fun repacketizerFree(handle: RepacketizerHandle)

    fun repacketizerReset(handle: RepacketizerHandle): Int

    fun repacketizerCat(handle: RepacketizerHandle, packet: ByteArray): Int

    fun repacketizerFrameCount(handle: RepacketizerHandle): Int

    fun repacketizerOutRange(
        handle: RepacketizerHandle,
        begin: Int,
        end: Int,
        out: ByteArray,
    ): Int

    fun repacketizerOut(handle: RepacketizerHandle, out: ByteArray): Int

    fun pcmSoftClip(pcm: FloatArray, frameSize: Int, channels: Int, memory: FloatArray): Int
}
