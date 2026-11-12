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
 * Stateful Opus decoder.
 *
 * Pass `null` (or an empty array) to [decode] to run packet-loss concealment.
 * [decodeFec] recovers a lost frame from the in-band FEC of the packet that
 * follows it.
 *
 * Not thread-safe: Opus decoder state must be driven by one thread at a time.
 */
class OpusDecoder private constructor(
    private val handle: DecoderHandle,
    val sampleRate: Int,
    val channels: Int,
) : AutoCloseable {

    private var closed = false

    /** Largest frame this decoder can produce, in samples per channel (120 ms). */
    val maxFrameSize: Int
        get() = sampleRate * 120 / 1000

    /** Output gain in Q8 dB, as accepted by `OPUS_SET_GAIN`. */
    var gain: Int
        get() = get(OpusCtl.GET_GAIN)
        set(value) {
            require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "gain must fit in a signed 16-bit value" }
            set(OpusCtl.SET_GAIN, value)
        }

    /** Disables phase inversion; only meaningful for some low-delay configurations. */
    var phaseInversionDisabled: Boolean
        get() = get(OpusCtl.GET_PHASE_INVERSION_DISABLED) != 0
        set(value) = set(OpusCtl.SET_PHASE_INVERSION_DISABLED, if (value) 1 else 0)

    /** Entropy coder range of the last decoded packet. */
    val finalRange: Long
        get() {
            checkOpen()
            return OpusNative.decoderFinalRange(handle)
        }

    /** Pitch period of the last decoded frame, in samples at 48 kHz. */
    val pitch: Int
        get() = get(OpusCtl.GET_PITCH)

    /** Bandwidth of the last decoded packet, or [OpusBandwidth.AUTO] before the first one. */
    val bandwidth: OpusBandwidth
        get() {
            val value = get(OpusCtl.GET_BANDWIDTH)
            return if (value > 0) OpusBandwidth.of(value) else OpusBandwidth.AUTO
        }

    /** Duration of the last decoded packet, in samples at 48 kHz. */
    val lastPacketDuration: Int
        get() = get(OpusCtl.GET_LAST_PACKET_DURATION)

    /** Whether the last packet was decoded from a DTX (silence) frame. */
    val inDtx: Boolean
        get() = get(OpusCtl.GET_IN_DTX) != 0

    /** Resets the decoder state. Control settings are kept. */
    fun reset() {
        checkOpen()
        val code = OpusNative.decoderReset(handle)
        if (code < 0) Opus.throwFailure(code)
    }

    /**
     * Decodes one packet into 16-bit PCM. The returned array holds
     * `samples * channels` interleaved samples.
     *
     * [frameSize] is the capacity in samples per channel, up to [maxFrameSize];
     * the decoder uses the packet's own duration. A `null` or empty [packet]
     * triggers concealment and produces [frameSize] samples.
     */
    fun decode(
        packet: ByteArray?,
        frameSize: Int = maxFrameSize,
        fec: Boolean = false,
    ): ShortArray {
        checkOpen()
        require(frameSize > 0 && frameSize <= maxFrameSize) {
            "frameSize must be in 1..$maxFrameSize"
        }
        val out = ShortArray(frameSize * channels)
        val samples = OpusNative.decoderDecode(
            handle,
            packet?.takeIf { it.isNotEmpty() },
            frameSize,
            fec,
            out,
        )
        if (samples < 0) Opus.throwFailure(samples)
        return out.copyOf(samples * channels)
    }

    /** Floating-point variant of [decode]; samples are in `-1..1`. */
    fun decodeFloat(
        packet: ByteArray?,
        frameSize: Int = maxFrameSize,
        fec: Boolean = false,
    ): FloatArray {
        checkOpen()
        require(frameSize > 0 && frameSize <= maxFrameSize) {
            "frameSize must be in 1..$maxFrameSize"
        }
        val out = FloatArray(frameSize * channels)
        val samples = OpusNative.decoderDecodeFloat(
            handle,
            packet?.takeIf { it.isNotEmpty() },
            frameSize,
            fec,
            out,
        )
        if (samples < 0) Opus.throwFailure(samples)
        return out.copyOf(samples * channels)
    }

    /**
     * Recovers the frame lost before [packet] using its in-band FEC data.
     * The packet itself is not decoded; pass the following packet.
     */
    fun decodeFec(packet: ByteArray, frameSize: Int = maxFrameSize): ShortArray =
        decode(packet, frameSize, fec = true)

    /** Samples per channel carried by [packet], without decoding it. */
    fun decodedSampleCount(packet: ByteArray): Int {
        checkOpen()
        require(packet.isNotEmpty()) { "packet must not be empty" }
        val samples = OpusNative.decoderGetNbSamples(handle, packet)
        if (samples < 0) Opus.throwFailure(samples)
        return samples
    }

    override fun close() {
        if (closed) return
        closed = true
        OpusNative.decoderFree(handle)
    }

    private fun set(request: Int, value: Int) {
        checkOpen()
        val code = OpusNative.decoderCtlInt(handle, request, value)
        if (code < 0) Opus.throwFailure(code)
    }

    private fun get(request: Int): Int {
        checkOpen()
        return OpusNative.decoderGetInt(handle, request)
    }

    private fun checkOpen() {
        check(!closed) { "decoder is closed" }
    }

    companion object {
        /** Opens a decoder for 8000, 12000, 16000, 24000 or 48000 Hz and 1 or 2 channels. */
        fun open(sampleRate: Int, channels: Int = 1): OpusDecoder {
            require(sampleRate in SUPPORTED_SAMPLE_RATES) {
                "sampleRate must be one of ${SUPPORTED_SAMPLE_RATES.joinToString()}"
            }
            require(channels in 1..2) { "channels must be 1 or 2" }
            val handle = OpusNative.decoderCreate(sampleRate, channels)
                ?: throw OpusException(-7, "Opus rejected the decoder configuration")
            return OpusDecoder(handle, sampleRate, channels)
        }

        private val SUPPORTED_SAMPLE_RATES = intArrayOf(8000, 12000, 16000, 24000, 48000)
    }
}
