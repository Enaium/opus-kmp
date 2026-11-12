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
 * Stateful Opus encoder.
 *
 * One call encodes one frame; the caller chooses the frame size from the valid
 * Opus durations (2.5, 5, 10, 20, 40, 60 ms, or 80/100/120 ms with
 * [expertFrameDuration]). At 48 kHz that is 120, 240, 480, 960, 1920 or 2880
 * samples per channel for the standard durations.
 *
 * Not thread-safe: Opus encoder state must be driven by one thread at a time.
 */
class OpusEncoder private constructor(
    private val handle: EncoderHandle,
    val sampleRate: Int,
    val channels: Int,
    val application: OpusApplication,
) : AutoCloseable {

    private var closed = false

    /** Target bitrate in bit/s, [OPUS_AUTO] when unset, or [OPUS_BITRATE_MAX]. */
    var bitrate: Int
        get() = get(OpusCtl.GET_BITRATE)
        set(value) = set(OpusCtl.SET_BITRATE, value)

    /** Enables variable bitrate; on by default. */
    var vbr: Boolean
        get() = get(OpusCtl.GET_VBR) != 0
        set(value) = set(OpusCtl.SET_VBR, if (value) 1 else 0)

    /** Constrains VBR to a constant frame size instead of a constant bitrate. */
    var constrainedVbr: Boolean
        get() = get(OpusCtl.GET_VBR_CONSTRAINT) != 0
        set(value) = set(OpusCtl.SET_VBR_CONSTRAINT, if (value) 1 else 0)

    /** Encoder complexity, 0 (fastest) to 10 (best quality). */
    var complexity: Int
        get() = get(OpusCtl.GET_COMPLEXITY)
        set(value) {
            require(value in 0..10) { "complexity must be in 0..10" }
            set(OpusCtl.SET_COMPLEXITY, value)
        }

    /** Enables in-band FEC. Useful only when the transport loses packets. */
    var inbandFec: Boolean
        get() = get(OpusCtl.GET_INBAND_FEC) != 0
        set(value) = set(OpusCtl.SET_INBAND_FEC, if (value) 1 else 0)

    /** Expected packet loss, in percent; controls the amount of FEC redundancy. */
    var packetLossPercent: Int
        get() = get(OpusCtl.GET_PACKET_LOSS_PERC)
        set(value) {
            require(value in 0..100) { "packet loss must be in 0..100 percent" }
            set(OpusCtl.SET_PACKET_LOSS_PERC, value)
        }

    /** Enables discontinuous transmission: silence produces no packet. */
    var dtx: Boolean
        get() = get(OpusCtl.GET_DTX) != 0
        set(value) = set(OpusCtl.SET_DTX, if (value) 1 else 0)

    /** Requested maximum bandwidth, or [OpusBandwidth.AUTO]. */
    var maxBandwidth: OpusBandwidth
        get() = OpusBandwidth.of(get(OpusCtl.GET_MAX_BANDWIDTH))
        set(value) = set(OpusCtl.SET_MAX_BANDWIDTH, value.value)

    /**
     * Requested bandwidth, or the bandwidth chosen for the last packet.
     * [OpusBandwidth.AUTO] lets the encoder decide.
     */
    var bandwidth: OpusBandwidth
        get() = OpusBandwidth.of(get(OpusCtl.GET_BANDWIDTH))
        set(value) = set(OpusCtl.SET_BANDWIDTH, value.value)

    /** Content hint, or [OpusSignal.AUTO]. */
    var signal: OpusSignal
        get() = OpusSignal.of(get(OpusCtl.GET_SIGNAL))
        set(value) = set(OpusCtl.SET_SIGNAL, value.value)

    /** Forces mono/stereo coding, or [OPUS_AUTO]. */
    var forceChannels: Int
        get() = get(OpusCtl.GET_FORCE_CHANNELS)
        set(value) {
            require(value == OPUS_AUTO || value in 1..2) { "forceChannels must be -1000, 1 or 2" }
            set(OpusCtl.SET_FORCE_CHANNELS, value)
        }

    /** Encoder lookahead in samples; the decoder needs it before the first frame. */
    val lookahead: Int
        get() = get(OpusCtl.GET_LOOKAHEAD)

    /** Input sample resolution in bits, 8..24. */
    var lsbDepth: Int
        get() = get(OpusCtl.GET_LSB_DEPTH)
        set(value) {
            require(value in 8..24) { "lsbDepth must be in 8..24" }
            set(OpusCtl.SET_LSB_DEPTH, value)
        }

    /** Frame duration requested through `OPUS_SET_EXPERT_FRAME_DURATION`. */
    var expertFrameDuration: OpusFrameDuration
        get() = OpusFrameDuration.of(get(OpusCtl.GET_EXPERT_FRAME_DURATION))
        set(value) = set(OpusCtl.SET_EXPERT_FRAME_DURATION, value.value)

    /** Disables prediction between frames for independent packets. */
    var predictionDisabled: Boolean
        get() = get(OpusCtl.GET_PREDICTION_DISABLED) != 0
        set(value) = set(OpusCtl.SET_PREDICTION_DISABLED, if (value) 1 else 0)

    /** Disables phase inversion; only useful for some low-delay configurations. */
    var phaseInversionDisabled: Boolean
        get() = get(OpusCtl.GET_PHASE_INVERSION_DISABLED) != 0
        set(value) = set(OpusCtl.SET_PHASE_INVERSION_DISABLED, if (value) 1 else 0)

    /** Entropy coder range of the last encoded packet. */
    val finalRange: Long
        get() {
            checkOpen()
            return OpusNative.encoderFinalRange(handle)
        }

    /** Resets the encoder state. Control settings are kept. */
    fun reset() {
        checkOpen()
        val code = OpusNative.encoderReset(handle)
        if (code < 0) Opus.throwFailure(code)
    }

    /**
     * Samples per channel for an Opus frame duration in milliseconds.
     * Valid durations are 2.5, 5, 10, 20, 40 and 60 ms (plus the expert
     * durations when [expertFrameDuration] enables them).
     */
    fun frameSizeSamples(durationMs: Double): Int {
        require(durationMs > 0.0) { "durationMs must be positive" }
        return (sampleRate * durationMs / 1000.0).toInt()
    }

    /**
     * Encodes [frameSize] samples per channel of 16-bit PCM starting at [offset].
     *
     * Returns the packet bytes, or an empty array when DTX suppressed the
     * frame. [maxPacketBytes] bounds the output; 4000 bytes is enough for the
     * largest standard stereo packet.
     */
    fun encode(
        pcm: ShortArray,
        offset: Int = 0,
        frameSize: Int = (pcm.size - offset) / channels,
        maxPacketBytes: Int = Opus.DEFAULT_MAX_PACKET_BYTES,
    ): ByteArray {
        checkOpen()
        require(frameSize > 0) { "frameSize must be positive" }
        require(maxPacketBytes > 0) { "maxPacketBytes must be positive" }
        require(offset >= 0 && offset.toLong() + frameSize.toLong() * channels <= pcm.size) {
            "pcm holds ${pcm.size} samples, a ${frameSize}x$channels frame from $offset does not fit"
        }
        val out = ByteArray(maxPacketBytes)
        val size = OpusNative.encoderEncode(handle, pcm, offset, frameSize, out)
        if (size < 0) Opus.throwFailure(size)
        return out.copyOf(size)
    }

    /** Floating-point variant of [encode]; samples are in `-1..1`. */
    fun encodeFloat(
        pcm: FloatArray,
        offset: Int = 0,
        frameSize: Int = (pcm.size - offset) / channels,
        maxPacketBytes: Int = Opus.DEFAULT_MAX_PACKET_BYTES,
    ): ByteArray {
        checkOpen()
        require(frameSize > 0) { "frameSize must be positive" }
        require(maxPacketBytes > 0) { "maxPacketBytes must be positive" }
        require(offset >= 0 && offset.toLong() + frameSize.toLong() * channels <= pcm.size) {
            "pcm holds ${pcm.size} samples, a ${frameSize}x$channels frame from $offset does not fit"
        }
        val out = ByteArray(maxPacketBytes)
        val size = OpusNative.encoderEncodeFloat(handle, pcm, offset, frameSize, out)
        if (size < 0) Opus.throwFailure(size)
        return out.copyOf(size)
    }

    override fun close() {
        if (closed) return
        closed = true
        OpusNative.encoderFree(handle)
    }

    private fun set(request: Int, value: Int) {
        checkOpen()
        val code = OpusNative.encoderCtlInt(handle, request, value)
        if (code < 0) Opus.throwFailure(code)
    }

    private fun get(request: Int): Int {
        checkOpen()
        return OpusNative.encoderGetInt(handle, request)
    }

    private fun checkOpen() {
        check(!closed) { "encoder is closed" }
    }

    companion object {
        /** `OPUS_AUTO`. */
        const val OPUS_AUTO: Int = -1000

        /** `OPUS_BITRATE_MAX`. */
        const val OPUS_BITRATE_MAX: Int = -1

        /**
         * Opens an encoder. [sampleRate] is 8000, 12000, 16000, 24000 or 48000;
         * [channels] is 1 or 2.
         */
        fun open(
            sampleRate: Int,
            channels: Int = 1,
            application: OpusApplication = OpusApplication.AUDIO,
        ): OpusEncoder {
            require(sampleRate in SUPPORTED_SAMPLE_RATES) {
                "sampleRate must be one of ${SUPPORTED_SAMPLE_RATES.joinToString()}"
            }
            require(channels in 1..2) { "channels must be 1 or 2" }
            val handle = OpusNative.encoderCreate(sampleRate, channels, application.value)
                ?: throw OpusException(OPUS_ALLOC_FAIL, "Opus rejected the encoder configuration")
            return OpusEncoder(handle, sampleRate, channels, application)
        }

        /** `OPUS_ALLOC_FAIL`; also used when the shim returns a null handle. */
        const val OPUS_ALLOC_FAIL: Int = -7

        private val SUPPORTED_SAMPLE_RATES = intArrayOf(8000, 12000, 16000, 24000, 48000)
    }
}
