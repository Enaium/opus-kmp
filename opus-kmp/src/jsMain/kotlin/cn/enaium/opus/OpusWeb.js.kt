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

import kotlin.js.js

// The glue is instantiated lazily: decoding and compiling the embedded module
// is the only expensive step, and it should not run for a process that never
// calls Opus.
private val opusWebInitialized: Unit by lazy {
    js(OPUS_WEB_INIT_JS)(OPUS_WASM_BASE64)
}

internal actual fun webInit() {
    opusWebInitialized
}

internal actual fun webVersionString(): String =
    js("() => globalThis.__opusWeb.versionString()")() as String

internal actual fun webStrerror(error: Int): String =
    js("(e) => globalThis.__opusWeb.strerror(e)")(error) as String

internal actual fun webEncoderCreate(sampleRate: Int, channels: Int, application: Int): Int =
    js("(r, c, a) => globalThis.__opusWeb.encoderCreate(r, c, a)")(sampleRate, channels, application) as Int

internal actual fun webEncoderFree(handle: Int) {
    js("(h) => globalThis.__opusWeb.encoderFree(h)")(handle)
}

internal actual fun webEncoderReset(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.encoderReset(h)")(handle) as Int

internal actual fun webEncoderSampleRate(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.encoderSampleRate(h)")(handle) as Int

internal actual fun webEncoderChannels(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.encoderChannels(h)")(handle) as Int

internal actual fun webEncoderEncode(
    handle: Int,
    samples: String,
    frameSize: Int,
    maxBytes: Int,
): String = js("(h, s, f, m) => globalThis.__opusWeb.encoderEncode(h, s, f, m)")(
    handle,
    samples,
    frameSize,
    maxBytes,
) as String

internal actual fun webEncoderEncodeFloat(
    handle: Int,
    samples: String,
    frameSize: Int,
    maxBytes: Int,
): String = js("(h, s, f, m) => globalThis.__opusWeb.encoderEncodeFloat(h, s, f, m)")(
    handle,
    samples,
    frameSize,
    maxBytes,
) as String

internal actual fun webEncoderCtlInt(handle: Int, request: Int, value: Int): Int =
    js("(h, r, v) => globalThis.__opusWeb.encoderCtlInt(h, r, v)")(handle, request, value) as Int

internal actual fun webEncoderGetInt(handle: Int, request: Int): Int =
    js("(h, r) => globalThis.__opusWeb.encoderGetInt(h, r)")(handle, request) as Int

internal actual fun webEncoderFinalRange(handle: Int): String =
    js("(h) => globalThis.__opusWeb.encoderFinalRange(h)")(handle) as String

internal actual fun webDecoderCreate(sampleRate: Int, channels: Int): Int =
    js("(r, c) => globalThis.__opusWeb.decoderCreate(r, c)")(sampleRate, channels) as Int

internal actual fun webDecoderFree(handle: Int) {
    js("(h) => globalThis.__opusWeb.decoderFree(h)")(handle)
}

internal actual fun webDecoderReset(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.decoderReset(h)")(handle) as Int

internal actual fun webDecoderSampleRate(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.decoderSampleRate(h)")(handle) as Int

internal actual fun webDecoderChannels(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.decoderChannels(h)")(handle) as Int

internal actual fun webDecoderDecode(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String = js("(h, p, f, e, m) => globalThis.__opusWeb.decoderDecode(h, p, f, e, m)")(
    handle,
    packet,
    frameSize,
    fec,
    maxSamples,
) as String

internal actual fun webDecoderDecodeFloat(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String = js("(h, p, f, e, m) => globalThis.__opusWeb.decoderDecodeFloat(h, p, f, e, m)")(
    handle,
    packet,
    frameSize,
    fec,
    maxSamples,
) as String

internal actual fun webDecoderCtlInt(handle: Int, request: Int, value: Int): Int =
    js("(h, r, v) => globalThis.__opusWeb.decoderCtlInt(h, r, v)")(handle, request, value) as Int

internal actual fun webDecoderGetInt(handle: Int, request: Int): Int =
    js("(h, r) => globalThis.__opusWeb.decoderGetInt(h, r)")(handle, request) as Int

internal actual fun webDecoderFinalRange(handle: Int): String =
    js("(h) => globalThis.__opusWeb.decoderFinalRange(h)")(handle) as String

internal actual fun webDecoderGetNbSamples(handle: Int, packet: String): Int =
    js("(h, p) => globalThis.__opusWeb.decoderGetNbSamples(h, p)")(handle, packet) as Int

internal actual fun webPacketBandwidth(packet: String): Int =
    js("(p) => globalThis.__opusWeb.packetBandwidth(p)")(packet) as Int

internal actual fun webPacketSamplesPerFrame(packet: String, sampleRate: Int): Int =
    js("(p, r) => globalThis.__opusWeb.packetSamplesPerFrame(p, r)")(packet, sampleRate) as Int

internal actual fun webPacketChannels(packet: String): Int =
    js("(p) => globalThis.__opusWeb.packetChannels(p)")(packet) as Int

internal actual fun webPacketFrames(packet: String): Int =
    js("(p) => globalThis.__opusWeb.packetFrames(p)")(packet) as Int

internal actual fun webPacketSamples(packet: String, sampleRate: Int): Int =
    js("(p, r) => globalThis.__opusWeb.packetSamples(p, r)")(packet, sampleRate) as Int

internal actual fun webPacketHasLbrr(packet: String): Int =
    js("(p) => globalThis.__opusWeb.packetHasLbrr(p)")(packet) as Int

internal actual fun webPacketParse(packet: String): String =
    js("(p) => globalThis.__opusWeb.packetParse(p)")(packet) as String

internal actual fun webPacketPad(packet: String, length: Int, newLength: Int): String =
    js("(p, l, n) => globalThis.__opusWeb.packetPad(p, l, n)")(packet, length, newLength) as String

internal actual fun webPacketUnpad(packet: String): String =
    js("(p) => globalThis.__opusWeb.packetUnpad(p)")(packet) as String

internal actual fun webRepacketizerCreate(): Int =
    js("() => globalThis.__opusWeb.repacketizerCreate()")() as Int

internal actual fun webRepacketizerFree(handle: Int) {
    js("(h) => globalThis.__opusWeb.repacketizerFree(h)")(handle)
}

internal actual fun webRepacketizerReset(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.repacketizerReset(h)")(handle) as Int

internal actual fun webRepacketizerCat(handle: Int, packet: String): Int =
    js("(h, p) => globalThis.__opusWeb.repacketizerCat(h, p)")(handle, packet) as Int

internal actual fun webRepacketizerFrameCount(handle: Int): Int =
    js("(h) => globalThis.__opusWeb.repacketizerFrameCount(h)")(handle) as Int

internal actual fun webRepacketizerOutRange(
    handle: Int,
    begin: Int,
    end: Int,
    maxBytes: Int,
): String = js("(h, b, e, m) => globalThis.__opusWeb.repacketizerOutRange(h, b, e, m)")(
    handle,
    begin,
    end,
    maxBytes,
) as String

internal actual fun webRepacketizerOut(handle: Int, maxBytes: Int): String =
    js("(h, m) => globalThis.__opusWeb.repacketizerOut(h, m)")(handle, maxBytes) as String

internal actual fun webPcmSoftClip(
    pcm: String,
    frameSize: Int,
    channels: Int,
    memory: String,
): String = js("(p, f, c, m) => globalThis.__opusWeb.pcmSoftClip(p, f, c, m)")(
    pcm,
    frameSize,
    channels,
    memory,
) as String
