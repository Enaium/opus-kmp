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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpusTest {

    @Test
    fun versionAndErrors() {
        assertTrue(Opus.version.contains("1.5.2"), "unexpected version: ${Opus.version}")
        assertTrue(Opus.strerror(-4).isNotBlank())
        assertTrue(OpusException(-4, Opus.strerror(-4)).message.orEmpty().isNotBlank())
    }

    @Test
    fun monoRoundTrip() {
        OpusEncoder.open(SAMPLE_RATE, 1, OpusApplication.AUDIO).use { encoder ->
            OpusDecoder.open(SAMPLE_RATE, 1).use { decoder ->
                encoder.bitrate = 32_000
                encoder.complexity = 5
                val pcm = sine(frameSize = 960, frequency = 440.0)
                val packet = encoder.encode(pcm)
                assertTrue(packet.isNotEmpty())
                assertTrue(packet.size <= Opus.DEFAULT_MAX_PACKET_BYTES)
                val decoded = decoder.decode(packet)
                assertEquals(960, decoded.size)
                assertTrue(decoded.maxOf { abs(it.toInt()) } > 1000, "decoded frame is silent")
            }
        }
    }

    @Test
    fun stereoRoundTrip() {
        OpusEncoder.open(SAMPLE_RATE, 2, OpusApplication.AUDIO).use { encoder ->
            OpusDecoder.open(SAMPLE_RATE, 2).use { decoder ->
                encoder.bitrate = 64_000
                val interleaved = ShortArray(960 * 2)
                val left = sine(960, 440.0)
                val right = sine(960, 660.0)
                for (index in 0 until 960) {
                    interleaved[index * 2] = left[index]
                    interleaved[index * 2 + 1] = right[index]
                }
                val packet = encoder.encode(interleaved)
                val decoded = decoder.decode(packet)
                assertEquals(960 * 2, decoded.size)
                assertTrue(decoded.maxOf { abs(it.toInt()) } > 1000)
            }
        }
    }

    @Test
    fun floatRoundTrip() {
        OpusEncoder.open(SAMPLE_RATE, 1, OpusApplication.AUDIO).use { encoder ->
            OpusDecoder.open(SAMPLE_RATE, 1).use { decoder ->
                val pcm = FloatArray(960) { index ->
                    (sin(2.0 * PI * 440.0 * index / SAMPLE_RATE) * 0.4).toFloat()
                }
                val packet = encoder.encodeFloat(pcm)
                val decoded = decoder.decodeFloat(packet)
                assertEquals(960, decoded.size)
                assertTrue(decoded.maxOf { abs(it) } > 0.05f)
            }
        }
    }

    @Test
    fun everySampleRateAndFrameDuration() {
        for (rate in intArrayOf(8_000, 12_000, 16_000, 24_000, 48_000)) {
            OpusEncoder.open(rate, 1, OpusApplication.VOIP).use { encoder ->
                OpusDecoder.open(rate, 1).use { decoder ->
                    val frameSize = rate / 50
                    val pcm = ShortArray(frameSize) { index ->
                        (sin(2.0 * PI * 300.0 * index / rate) * 0.3 * Short.MAX_VALUE).toInt().toShort()
                    }
                    val packet = encoder.encode(pcm)
                    assertEquals(frameSize, OpusPacket.sampleCount(packet, rate))
                    assertEquals(frameSize, decoder.decode(packet).size)
                }
            }
        }

        for (durationMs in doubleArrayOf(2.5, 5.0, 10.0, 20.0, 40.0, 60.0)) {
            OpusEncoder.open(SAMPLE_RATE, 1, OpusApplication.AUDIO).use { encoder ->
                OpusDecoder.open(SAMPLE_RATE, 1).use { decoder ->
                    val frameSize = encoder.frameSizeSamples(durationMs)
                    val pcm = ShortArray(frameSize) { index ->
                        (sin(2.0 * PI * 440.0 * index / SAMPLE_RATE) * 0.3 * Short.MAX_VALUE).toInt().toShort()
                    }
                    val packet = encoder.encode(pcm)
                    assertTrue(packet.isNotEmpty(), "no packet for $durationMs ms")
                    assertEquals(frameSize, decoder.decode(packet, frameSize).size)
                }
            }
        }
    }

    @Test
    fun packetLossConcealment() {
        OpusDecoder.open(SAMPLE_RATE, 1).use { decoder ->
            val concealed = decoder.decode(null, 960)
            assertEquals(960, concealed.size)
        }
    }

    @Test
    fun packetInspectionAndRepacketizer() {
        OpusEncoder.open(SAMPLE_RATE, 1, OpusApplication.AUDIO).use { encoder ->
            OpusDecoder.open(SAMPLE_RATE, 1).use { decoder ->
                encoder.bitrate = 24_000
                val first = encoder.encode(sine(960, 440.0))
                val second = encoder.encode(sine(960, 550.0))

                assertEquals(1, OpusPacket.frameCount(first))
                assertEquals(1, OpusPacket.channels(first))
                assertEquals(960, OpusPacket.samplesPerFrame(first, SAMPLE_RATE))
                assertEquals(960, OpusPacket.sampleCount(first, SAMPLE_RATE))
                assertEquals(1, OpusPacket.frames(first).size)
                assertTrue(OpusPacket.bandwidth(first) in CONCRETE_BANDWIDTHS)
                assertFalse(OpusPacket.hasLbrr(first))

                OpusRepacketizer().use { repacketizer ->
                    repacketizer.cat(first)
                    repacketizer.cat(second)
                    assertEquals(2, repacketizer.frameCount)
                    val combined = repacketizer.out()
                    assertEquals(2, OpusPacket.frameCount(combined))
                    val decoded = decoder.decode(combined, 1920)
                    assertEquals(1920, decoded.size)
                    assertTrue(decoded.maxOf { abs(it.toInt()) } > 1000)

                    val firstOnly = repacketizer.outRange(0, 1)
                    assertEquals(1, OpusPacket.frameCount(firstOnly))
                    assertEquals(960, decoder.decode(firstOnly, 960).size)

                    repacketizer.reset()
                    assertEquals(0, repacketizer.frameCount)
                }
            }
        }
    }

    @Test
    fun paddingRoundTrip() {
        OpusEncoder.open(SAMPLE_RATE, 1, OpusApplication.VOIP).use { encoder ->
            val packet = encoder.encode(sine(960, 300.0))
            val padded = OpusPacket.pad(packet, packet.size + 16)
            assertEquals(packet.size + 16, padded.size)
            val unpadded = OpusPacket.unpad(padded)
            assertContentEquals(packet, unpadded)
        }
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { OpusEncoder.open(44_100) }
        assertFailsWith<IllegalArgumentException> { OpusDecoder.open(48_000, 3) }
        assertFailsWith<IllegalArgumentException> { OpusPacket.bandwidth(ByteArray(0)) }

        OpusEncoder.open(SAMPLE_RATE, 1, OpusApplication.AUDIO).use { encoder ->
            assertFailsWith<IllegalArgumentException> {
                encoder.encode(ShortArray(10), offset = 0, frameSize = 960)
            }
            assertFailsWith<IllegalArgumentException> { encoder.complexity = 11 }
        }
    }

    @Test
    fun softClipKeepsSamplesInRange() {
        val pcm = FloatArray(960) { index ->
            (sin(2.0 * PI * 220.0 * index / SAMPLE_RATE) * 2.5).toFloat()
        }
        Opus.softClip(pcm, frameSize = 960, channels = 1)
        assertTrue(pcm.maxOf { abs(it) } <= 1.0001f)
    }

    private fun sine(frameSize: Int, frequency: Double): ShortArray = ShortArray(frameSize) { index ->
        (sin(2.0 * PI * frequency * index / SAMPLE_RATE) * 0.4 * Short.MAX_VALUE).toInt().toShort()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        val CONCRETE_BANDWIDTHS = setOf(
            OpusBandwidth.NARROWBAND,
            OpusBandwidth.MEDIUMBAND,
            OpusBandwidth.WIDEBAND,
            OpusBandwidth.SUPERWIDEBAND,
            OpusBandwidth.FULLBAND,
        )
    }
}
