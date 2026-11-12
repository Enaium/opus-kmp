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

/** Thrown when Opus rejects a call. [code] is the negative Opus error code. */
class OpusException(
    val code: Int,
    message: String,
) : Exception(message)

/** Encoder application modes, mirroring `OPUS_APPLICATION_*`. */
enum class OpusApplication(val value: Int) {
    /** Best for VoIP and videoconferencing: intelligibility first. */
    VOIP(2048),

    /** Best for broadcast and high-fidelity music: decoded audio closest to the input. */
    AUDIO(2049),

    /** Lowest achievable latency; voice modes are unavailable. */
    RESTRICTED_LOW_DELAY(2051),
    ;

    companion object {
        fun of(value: Int): OpusApplication =
            entries.firstOrNull { it.value == value }
                ?: throw OpusException(value, "Unknown Opus application $value")
    }
}

/** Audio bandwidths, mirroring `OPUS_BANDWIDTH_*`. [AUTO] is only valid as a request. */
enum class OpusBandwidth(val value: Int) {
    AUTO(-1000),
    NARROWBAND(1101),
    MEDIUMBAND(1102),
    WIDEBAND(1103),
    SUPERWIDEBAND(1104),
    FULLBAND(1105),
    ;

    companion object {
        fun of(value: Int): OpusBandwidth =
            entries.firstOrNull { it.value == value }
                ?: throw OpusException(value, "Unknown Opus bandwidth $value")
    }
}

/** Content hints, mirroring `OPUS_SIGNAL_*`. */
enum class OpusSignal(val value: Int) {
    AUTO(-1000),
    VOICE(3001),
    MUSIC(3002),
    ;

    companion object {
        fun of(value: Int): OpusSignal =
            entries.firstOrNull { it.value == value }
                ?: throw OpusException(value, "Unknown Opus signal $value")
    }
}

/** Expert frame durations, mirroring `OPUS_FRAMESIZE_*`. */
enum class OpusFrameDuration(val value: Int) {
    ARG(5000),
    MS_2_5(5001),
    MS_5(5002),
    MS_10(5003),
    MS_20(5004),
    MS_40(5005),
    MS_60(5006),
    MS_80(5007),
    MS_100(5008),
    MS_120(5009),
    ;

    companion object {
        fun of(value: Int): OpusFrameDuration =
            entries.firstOrNull { it.value == value }
                ?: throw OpusException(value, "Unknown Opus frame duration $value")
    }
}

/** Library-wide constants and helpers. */
object Opus {
    /** Maximum size of one Opus frame, in bytes. */
    const val MAX_PACKET_SIZE: Int = 1275

    /** Maximum number of frames in one Opus packet. */
    const val MAX_FRAMES_PER_PACKET: Int = 48

    /** Samples per channel in a 120 ms frame at 48 kHz. */
    const val MAX_FRAME_SAMPLES_48K: Int = 5760

    /** Default encoder output buffer: enough for stereo at 60 ms. */
    const val DEFAULT_MAX_PACKET_BYTES: Int = 4000

    /** Capacity used by the repacketizer: 48 maximum-sized frames. */
    const val MAX_REPACKETIZER_BYTES: Int = MAX_PACKET_SIZE * MAX_FRAMES_PER_PACKET

    /** The Opus library version, e.g. `1.5.2`. */
    val version: String by lazy { OpusNative.versionString() }

    /** Human-readable description of an Opus error code. */
    fun strerror(code: Int): String = OpusNative.strerror(code)

    /**
     * Applies Opus soft clipping to [pcm] in place and returns the same array.
     * [memory] carries `channels` floats of state between calls; a fresh buffer
     * is used when omitted.
     */
    fun softClip(
        pcm: FloatArray,
        frameSize: Int = pcm.size,
        channels: Int = 1,
        memory: FloatArray? = null,
    ): FloatArray {
        require(frameSize > 0 && channels in 1..2) { "invalid soft-clip frame size or channel count" }
        require(pcm.size >= frameSize * channels) {
            "pcm holds ${pcm.size} samples, a ${frameSize}x$channels frame needs ${frameSize * channels}"
        }
        val state = memory ?: FloatArray(channels)
        require(state.size >= channels) { "soft-clip memory needs at least $channels floats" }
        val code = OpusNative.pcmSoftClip(pcm, frameSize, channels, state)
        if (code < 0) throwFailure(code)
        return pcm
    }

    internal fun throwFailure(code: Int): Nothing {
        val detail = if (code == OK_INVALID) "invalid argument" else OpusNative.strerror(code)
        throw OpusException(code, "Opus error $code: $detail")
    }
}

internal object OpusCtl {
    const val SET_APPLICATION = 4000
    const val GET_APPLICATION = 4001
    const val SET_BITRATE = 4002
    const val GET_BITRATE = 4003
    const val SET_MAX_BANDWIDTH = 4004
    const val GET_MAX_BANDWIDTH = 4005
    const val SET_VBR = 4006
    const val GET_VBR = 4007
    const val SET_BANDWIDTH = 4008
    const val GET_BANDWIDTH = 4009
    const val SET_COMPLEXITY = 4010
    const val GET_COMPLEXITY = 4011
    const val SET_INBAND_FEC = 4012
    const val GET_INBAND_FEC = 4013
    const val SET_PACKET_LOSS_PERC = 4014
    const val GET_PACKET_LOSS_PERC = 4015
    const val SET_DTX = 4016
    const val GET_DTX = 4017
    const val SET_VBR_CONSTRAINT = 4020
    const val GET_VBR_CONSTRAINT = 4021
    const val SET_FORCE_CHANNELS = 4022
    const val GET_FORCE_CHANNELS = 4023
    const val SET_SIGNAL = 4024
    const val GET_SIGNAL = 4025
    const val GET_LOOKAHEAD = 4027
    const val RESET_STATE = 4028
    const val GET_SAMPLE_RATE = 4029
    const val GET_PITCH = 4033
    const val SET_GAIN = 4034
    const val GET_GAIN = 4045
    const val SET_LSB_DEPTH = 4036
    const val GET_LSB_DEPTH = 4037
    const val GET_LAST_PACKET_DURATION = 4039
    const val SET_EXPERT_FRAME_DURATION = 4040
    const val GET_EXPERT_FRAME_DURATION = 4041
    const val SET_PREDICTION_DISABLED = 4042
    const val GET_PREDICTION_DISABLED = 4043
    const val SET_PHASE_INVERSION_DISABLED = 4046
    const val GET_PHASE_INVERSION_DISABLED = 4047
    const val GET_IN_DTX = 4049
}
