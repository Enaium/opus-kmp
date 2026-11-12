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

package cn.enaium.opus

import cnames.structs.ok_decoder
import cnames.structs.ok_encoder
import cnames.structs.ok_repacketizer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import opus_kmp.ok_decoder_channels
import opus_kmp.ok_decoder_create
import opus_kmp.ok_decoder_ctl_int
import opus_kmp.ok_decoder_decode
import opus_kmp.ok_decoder_decode_float
import opus_kmp.ok_decoder_final_range
import opus_kmp.ok_decoder_free
import opus_kmp.ok_decoder_get_int
import opus_kmp.ok_decoder_get_nb_samples
import opus_kmp.ok_decoder_reset
import opus_kmp.ok_decoder_sample_rate
import opus_kmp.ok_encoder_channels
import opus_kmp.ok_encoder_create
import opus_kmp.ok_encoder_ctl_int
import opus_kmp.ok_encoder_encode
import opus_kmp.ok_encoder_encode_float
import opus_kmp.ok_encoder_final_range
import opus_kmp.ok_encoder_free
import opus_kmp.ok_encoder_get_int
import opus_kmp.ok_encoder_reset
import opus_kmp.ok_encoder_sample_rate
import opus_kmp.ok_packet_get_bandwidth
import opus_kmp.ok_packet_get_nb_channels
import opus_kmp.ok_packet_get_nb_frames
import opus_kmp.ok_packet_get_nb_samples
import opus_kmp.ok_packet_get_samples_per_frame
import opus_kmp.ok_packet_has_lbrr
import opus_kmp.ok_packet_pad
import opus_kmp.ok_packet_parse
import opus_kmp.ok_packet_unpad
import opus_kmp.ok_pcm_soft_clip
import opus_kmp.ok_repacketizer_cat
import opus_kmp.ok_repacketizer_create
import opus_kmp.ok_repacketizer_free
import opus_kmp.ok_repacketizer_get_nb_frames
import opus_kmp.ok_repacketizer_out
import opus_kmp.ok_repacketizer_out_range
import opus_kmp.ok_repacketizer_reset
import opus_kmp.ok_strerror
import opus_kmp.ok_version_string

internal actual class EncoderHandle actual constructor() {
    internal var pointer: CPointer<ok_encoder>? = null
}

internal actual class DecoderHandle actual constructor() {
    internal var pointer: CPointer<ok_decoder>? = null
}

internal actual class RepacketizerHandle actual constructor() {
    internal var pointer: CPointer<ok_repacketizer>? = null
}

internal actual object OpusNative {
    actual fun versionString(): String = ok_version_string()?.toKString() ?: ""

    actual fun strerror(error: Int): String = ok_strerror(error)?.toKString() ?: ""

    actual fun encoderCreate(sampleRate: Int, channels: Int, application: Int): EncoderHandle? {
        val pointer = ok_encoder_create(sampleRate, channels, application) ?: return null
        return EncoderHandle().also { it.pointer = pointer }
    }

    actual fun encoderFree(handle: EncoderHandle) {
        ok_encoder_free(handle.pointer)
        handle.pointer = null
    }

    actual fun encoderReset(handle: EncoderHandle): Int = ok_encoder_reset(handle.pointer)

    actual fun encoderSampleRate(handle: EncoderHandle): Int = ok_encoder_sample_rate(handle.pointer)

    actual fun encoderChannels(handle: EncoderHandle): Int = ok_encoder_channels(handle.pointer)

    actual fun encoderEncode(
        handle: EncoderHandle,
        samples: ShortArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (offset < 0 || frameSize <= 0 || out.isEmpty()) return OK_INVALID
        return samples.usePinned { samplesPin ->
            out.usePinned { outPin ->
                ok_encoder_encode(
                    pointer,
                    samplesPin.addressOf(offset),
                    frameSize,
                    outPin.addressOf(0).reinterpret<UByteVar>(),
                    out.size,
                )
            }
        }
    }

    actual fun encoderEncodeFloat(
        handle: EncoderHandle,
        samples: FloatArray,
        offset: Int,
        frameSize: Int,
        out: ByteArray,
    ): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (offset < 0 || frameSize <= 0 || out.isEmpty()) return OK_INVALID
        return samples.usePinned { samplesPin ->
            out.usePinned { outPin ->
                ok_encoder_encode_float(
                    pointer,
                    samplesPin.addressOf(offset),
                    frameSize,
                    outPin.addressOf(0).reinterpret<UByteVar>(),
                    out.size,
                )
            }
        }
    }

    actual fun encoderCtlInt(handle: EncoderHandle, request: Int, value: Int): Int =
        ok_encoder_ctl_int(handle.pointer, request, value)

    actual fun encoderGetInt(handle: EncoderHandle, request: Int): Int =
        ok_encoder_get_int(handle.pointer, request)

    actual fun encoderFinalRange(handle: EncoderHandle): Long =
        ok_encoder_final_range(handle.pointer).toLong()

    actual fun decoderCreate(sampleRate: Int, channels: Int): DecoderHandle? {
        val pointer = ok_decoder_create(sampleRate, channels) ?: return null
        return DecoderHandle().also { it.pointer = pointer }
    }

    actual fun decoderFree(handle: DecoderHandle) {
        ok_decoder_free(handle.pointer)
        handle.pointer = null
    }

    actual fun decoderReset(handle: DecoderHandle): Int = ok_decoder_reset(handle.pointer)

    actual fun decoderSampleRate(handle: DecoderHandle): Int = ok_decoder_sample_rate(handle.pointer)

    actual fun decoderChannels(handle: DecoderHandle): Int = ok_decoder_channels(handle.pointer)

    actual fun decoderDecode(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: ShortArray,
    ): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (frameSize <= 0 || out.isEmpty()) return OK_INVALID
        return out.usePinned { outPin ->
            if (packet == null || packet.isEmpty()) {
                ok_decoder_decode(
                    pointer,
                    null,
                    0,
                    frameSize,
                    outPin.addressOf(0),
                    out.size,
                    if (fec) 1 else 0,
                )
            } else {
                packet.usePinned { packetPin ->
                    ok_decoder_decode(
                        pointer,
                        packetPin.addressOf(0).reinterpret<UByteVar>(),
                        packet.size,
                        frameSize,
                        outPin.addressOf(0),
                        out.size,
                        if (fec) 1 else 0,
                    )
                }
            }
        }
    }

    actual fun decoderDecodeFloat(
        handle: DecoderHandle,
        packet: ByteArray?,
        frameSize: Int,
        fec: Boolean,
        out: FloatArray,
    ): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (frameSize <= 0 || out.isEmpty()) return OK_INVALID
        return out.usePinned { outPin ->
            if (packet == null || packet.isEmpty()) {
                ok_decoder_decode_float(
                    pointer,
                    null,
                    0,
                    frameSize,
                    outPin.addressOf(0),
                    out.size,
                    if (fec) 1 else 0,
                )
            } else {
                packet.usePinned { packetPin ->
                    ok_decoder_decode_float(
                        pointer,
                        packetPin.addressOf(0).reinterpret<UByteVar>(),
                        packet.size,
                        frameSize,
                        outPin.addressOf(0),
                        out.size,
                        if (fec) 1 else 0,
                    )
                }
            }
        }
    }

    actual fun decoderCtlInt(handle: DecoderHandle, request: Int, value: Int): Int =
        ok_decoder_ctl_int(handle.pointer, request, value)

    actual fun decoderGetInt(handle: DecoderHandle, request: Int): Int =
        ok_decoder_get_int(handle.pointer, request)

    actual fun decoderFinalRange(handle: DecoderHandle): Long =
        ok_decoder_final_range(handle.pointer).toLong()

    actual fun decoderGetNbSamples(handle: DecoderHandle, packet: ByteArray): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (packet.isEmpty()) return OK_INVALID
        return packet.usePinned {
            ok_decoder_get_nb_samples(pointer, it.addressOf(0).reinterpret<UByteVar>(), packet.size)
        }
    }

    actual fun packetBandwidth(packet: ByteArray): Int = withPacket(packet) { data, size ->
        ok_packet_get_bandwidth(data, size)
    }

    actual fun packetSamplesPerFrame(packet: ByteArray, sampleRate: Int): Int =
        withPacket(packet) { data, size -> ok_packet_get_samples_per_frame(data, size, sampleRate) }

    actual fun packetChannels(packet: ByteArray): Int = withPacket(packet) { data, size ->
        ok_packet_get_nb_channels(data, size)
    }

    actual fun packetFrames(packet: ByteArray): Int = withPacket(packet) { data, size ->
        ok_packet_get_nb_frames(data, size)
    }

    actual fun packetSamples(packet: ByteArray, sampleRate: Int): Int =
        withPacket(packet) { data, size -> ok_packet_get_nb_samples(data, size, sampleRate) }

    actual fun packetHasLbrr(packet: ByteArray): Int = withPacket(packet) { data, size ->
        ok_packet_has_lbrr(data, size)
    }

    actual fun packetParse(packet: ByteArray, sizes: IntArray, offsets: IntArray): Int {
        if (packet.isEmpty() || sizes.isEmpty() || offsets.isEmpty()) return OK_INVALID
        val capacity = minOf(sizes.size, offsets.size)
        return packet.usePinned { packetPin ->
            sizes.usePinned { sizesPin ->
                offsets.usePinned { offsetsPin ->
                    ok_packet_parse(
                        packetPin.addressOf(0).reinterpret<UByteVar>(),
                        packet.size,
                        sizesPin.addressOf(0),
                        offsetsPin.addressOf(0),
                        capacity,
                    )
                }
            }
        }
    }

    actual fun packetPad(packet: ByteArray, length: Int, newLength: Int): Int {
        if (packet.isEmpty() || length <= 0 || newLength < length) return OK_INVALID
        return packet.usePinned {
            ok_packet_pad(it.addressOf(0).reinterpret<UByteVar>(), length, newLength)
        }
    }

    actual fun packetUnpad(packet: ByteArray): Int {
        if (packet.isEmpty()) return OK_INVALID
        return packet.usePinned {
            ok_packet_unpad(it.addressOf(0).reinterpret<UByteVar>(), packet.size)
        }
    }

    actual fun repacketizerCreate(): RepacketizerHandle? {
        val pointer = ok_repacketizer_create() ?: return null
        return RepacketizerHandle().also { it.pointer = pointer }
    }

    actual fun repacketizerFree(handle: RepacketizerHandle) {
        ok_repacketizer_free(handle.pointer)
        handle.pointer = null
    }

    actual fun repacketizerReset(handle: RepacketizerHandle): Int =
        ok_repacketizer_reset(handle.pointer)

    actual fun repacketizerCat(handle: RepacketizerHandle, packet: ByteArray): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (packet.isEmpty()) return OK_INVALID
        return packet.usePinned {
            ok_repacketizer_cat(pointer, it.addressOf(0).reinterpret<UByteVar>(), packet.size)
        }
    }

    actual fun repacketizerFrameCount(handle: RepacketizerHandle): Int =
        ok_repacketizer_get_nb_frames(handle.pointer)

    actual fun repacketizerOutRange(
        handle: RepacketizerHandle,
        begin: Int,
        end: Int,
        out: ByteArray,
    ): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (out.isEmpty()) return OK_INVALID
        return out.usePinned {
            ok_repacketizer_out_range(
                pointer,
                begin,
                end,
                it.addressOf(0).reinterpret<UByteVar>(),
                out.size,
            )
        }
    }

    actual fun repacketizerOut(handle: RepacketizerHandle, out: ByteArray): Int {
        val pointer = handle.pointer ?: return OK_INVALID
        if (out.isEmpty()) return OK_INVALID
        return out.usePinned {
            ok_repacketizer_out(pointer, it.addressOf(0).reinterpret<UByteVar>(), out.size)
        }
    }

    actual fun pcmSoftClip(pcm: FloatArray, frameSize: Int, channels: Int, memory: FloatArray): Int {
        if (pcm.isEmpty() || memory.isEmpty()) return OK_INVALID
        return pcm.usePinned { pcmPin ->
            memory.usePinned { memoryPin ->
                ok_pcm_soft_clip(pcmPin.addressOf(0), frameSize, channels, memoryPin.addressOf(0))
            }
        }
    }

    private inline fun withPacket(packet: ByteArray, block: (data: kotlinx.cinterop.CPointer<UByteVar>, size: Int) -> Int): Int {
        if (packet.isEmpty()) return OK_INVALID
        return packet.usePinned {
            block(it.addressOf(0).reinterpret<UByteVar>(), packet.size)
        }
    }
}

