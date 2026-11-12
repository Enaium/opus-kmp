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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cn.enaium.opus

import kotlin.JsFun

private var opusWebInitialized = false

internal actual fun webInit() {
    if (!opusWebInitialized) {
        initOpusWeb(OPUS_WASM_BASE64)
        opusWebInitialized = true
    }
}

@JsFun(OPUS_WEB_INIT_JS)
private external fun initOpusWeb(wasmBase64: String)

@JsFun("() => globalThis.__opusWeb.versionString()")
private external fun versionString(): String

@JsFun("(e) => globalThis.__opusWeb.strerror(e)")
private external fun strerror(error: Int): String

@JsFun("(r, c, a) => globalThis.__opusWeb.encoderCreate(r, c, a)")
private external fun encoderCreate(sampleRate: Int, channels: Int, application: Int): Int

@JsFun("(h) => globalThis.__opusWeb.encoderFree(h)")
private external fun encoderFree(handle: Int)

@JsFun("(h) => globalThis.__opusWeb.encoderReset(h)")
private external fun encoderReset(handle: Int): Int

@JsFun("(h) => globalThis.__opusWeb.encoderSampleRate(h)")
private external fun encoderSampleRate(handle: Int): Int

@JsFun("(h) => globalThis.__opusWeb.encoderChannels(h)")
private external fun encoderChannels(handle: Int): Int

@JsFun("(h, s, f, m) => globalThis.__opusWeb.encoderEncode(h, s, f, m)")
private external fun encoderEncode(handle: Int, samples: String, frameSize: Int, maxBytes: Int): String

@JsFun("(h, s, f, m) => globalThis.__opusWeb.encoderEncodeFloat(h, s, f, m)")
private external fun encoderEncodeFloat(handle: Int, samples: String, frameSize: Int, maxBytes: Int): String

@JsFun("(h, r, v) => globalThis.__opusWeb.encoderCtlInt(h, r, v)")
private external fun encoderCtlInt(handle: Int, request: Int, value: Int): Int

@JsFun("(h, r) => globalThis.__opusWeb.encoderGetInt(h, r)")
private external fun encoderGetInt(handle: Int, request: Int): Int

@JsFun("(h) => globalThis.__opusWeb.encoderFinalRange(h)")
private external fun encoderFinalRange(handle: Int): String

@JsFun("(r, c) => globalThis.__opusWeb.decoderCreate(r, c)")
private external fun decoderCreate(sampleRate: Int, channels: Int): Int

@JsFun("(h) => globalThis.__opusWeb.decoderFree(h)")
private external fun decoderFree(handle: Int)

@JsFun("(h) => globalThis.__opusWeb.decoderReset(h)")
private external fun decoderReset(handle: Int): Int

@JsFun("(h) => globalThis.__opusWeb.decoderSampleRate(h)")
private external fun decoderSampleRate(handle: Int): Int

@JsFun("(h) => globalThis.__opusWeb.decoderChannels(h)")
private external fun decoderChannels(handle: Int): Int

@JsFun("(h, p, f, e, m) => globalThis.__opusWeb.decoderDecode(h, p, f, e, m)")
private external fun decoderDecode(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String

@JsFun("(h, p, f, e, m) => globalThis.__opusWeb.decoderDecodeFloat(h, p, f, e, m)")
private external fun decoderDecodeFloat(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String

@JsFun("(h, r, v) => globalThis.__opusWeb.decoderCtlInt(h, r, v)")
private external fun decoderCtlInt(handle: Int, request: Int, value: Int): Int

@JsFun("(h, r) => globalThis.__opusWeb.decoderGetInt(h, r)")
private external fun decoderGetInt(handle: Int, request: Int): Int

@JsFun("(h) => globalThis.__opusWeb.decoderFinalRange(h)")
private external fun decoderFinalRange(handle: Int): String

@JsFun("(h, p) => globalThis.__opusWeb.decoderGetNbSamples(h, p)")
private external fun decoderGetNbSamples(handle: Int, packet: String): Int

@JsFun("(p) => globalThis.__opusWeb.packetBandwidth(p)")
private external fun packetBandwidth(packet: String): Int

@JsFun("(p, r) => globalThis.__opusWeb.packetSamplesPerFrame(p, r)")
private external fun packetSamplesPerFrame(packet: String, sampleRate: Int): Int

@JsFun("(p) => globalThis.__opusWeb.packetChannels(p)")
private external fun packetChannels(packet: String): Int

@JsFun("(p) => globalThis.__opusWeb.packetFrames(p)")
private external fun packetFrames(packet: String): Int

@JsFun("(p, r) => globalThis.__opusWeb.packetSamples(p, r)")
private external fun packetSamples(packet: String, sampleRate: Int): Int

@JsFun("(p) => globalThis.__opusWeb.packetHasLbrr(p)")
private external fun packetHasLbrr(packet: String): Int

@JsFun("(p) => globalThis.__opusWeb.packetParse(p)")
private external fun packetParse(packet: String): String

@JsFun("(p, l, n) => globalThis.__opusWeb.packetPad(p, l, n)")
private external fun packetPad(packet: String, length: Int, newLength: Int): String

@JsFun("(p) => globalThis.__opusWeb.packetUnpad(p)")
private external fun packetUnpad(packet: String): String

@JsFun("() => globalThis.__opusWeb.repacketizerCreate()")
private external fun repacketizerCreate(): Int

@JsFun("(h) => globalThis.__opusWeb.repacketizerFree(h)")
private external fun repacketizerFree(handle: Int)

@JsFun("(h) => globalThis.__opusWeb.repacketizerReset(h)")
private external fun repacketizerReset(handle: Int): Int

@JsFun("(h, p) => globalThis.__opusWeb.repacketizerCat(h, p)")
private external fun repacketizerCat(handle: Int, packet: String): Int

@JsFun("(h) => globalThis.__opusWeb.repacketizerFrameCount(h)")
private external fun repacketizerFrameCount(handle: Int): Int

@JsFun("(h, b, e, m) => globalThis.__opusWeb.repacketizerOutRange(h, b, e, m)")
private external fun repacketizerOutRange(handle: Int, begin: Int, end: Int, maxBytes: Int): String

@JsFun("(h, m) => globalThis.__opusWeb.repacketizerOut(h, m)")
private external fun repacketizerOut(handle: Int, maxBytes: Int): String

@JsFun("(p, f, c, m) => globalThis.__opusWeb.pcmSoftClip(p, f, c, m)")
private external fun pcmSoftClip(pcm: String, frameSize: Int, channels: Int, memory: String): String

internal actual fun webVersionString(): String = versionString()

internal actual fun webStrerror(error: Int): String = strerror(error)

internal actual fun webEncoderCreate(sampleRate: Int, channels: Int, application: Int): Int =
    encoderCreate(sampleRate, channels, application)

internal actual fun webEncoderFree(handle: Int) = encoderFree(handle)

internal actual fun webEncoderReset(handle: Int): Int = encoderReset(handle)

internal actual fun webEncoderSampleRate(handle: Int): Int = encoderSampleRate(handle)

internal actual fun webEncoderChannels(handle: Int): Int = encoderChannels(handle)

internal actual fun webEncoderEncode(
    handle: Int,
    samples: String,
    frameSize: Int,
    maxBytes: Int,
): String = encoderEncode(handle, samples, frameSize, maxBytes)

internal actual fun webEncoderEncodeFloat(
    handle: Int,
    samples: String,
    frameSize: Int,
    maxBytes: Int,
): String = encoderEncodeFloat(handle, samples, frameSize, maxBytes)

internal actual fun webEncoderCtlInt(handle: Int, request: Int, value: Int): Int =
    encoderCtlInt(handle, request, value)

internal actual fun webEncoderGetInt(handle: Int, request: Int): Int = encoderGetInt(handle, request)

internal actual fun webEncoderFinalRange(handle: Int): String = encoderFinalRange(handle)

internal actual fun webDecoderCreate(sampleRate: Int, channels: Int): Int =
    decoderCreate(sampleRate, channels)

internal actual fun webDecoderFree(handle: Int) = decoderFree(handle)

internal actual fun webDecoderReset(handle: Int): Int = decoderReset(handle)

internal actual fun webDecoderSampleRate(handle: Int): Int = decoderSampleRate(handle)

internal actual fun webDecoderChannels(handle: Int): Int = decoderChannels(handle)

internal actual fun webDecoderDecode(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String = decoderDecode(handle, packet, frameSize, fec, maxSamples)

internal actual fun webDecoderDecodeFloat(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String = decoderDecodeFloat(handle, packet, frameSize, fec, maxSamples)

internal actual fun webDecoderCtlInt(handle: Int, request: Int, value: Int): Int =
    decoderCtlInt(handle, request, value)

internal actual fun webDecoderGetInt(handle: Int, request: Int): Int = decoderGetInt(handle, request)

internal actual fun webDecoderFinalRange(handle: Int): String = decoderFinalRange(handle)

internal actual fun webDecoderGetNbSamples(handle: Int, packet: String): Int =
    decoderGetNbSamples(handle, packet)

internal actual fun webPacketBandwidth(packet: String): Int = packetBandwidth(packet)

internal actual fun webPacketSamplesPerFrame(packet: String, sampleRate: Int): Int =
    packetSamplesPerFrame(packet, sampleRate)

internal actual fun webPacketChannels(packet: String): Int = packetChannels(packet)

internal actual fun webPacketFrames(packet: String): Int = packetFrames(packet)

internal actual fun webPacketSamples(packet: String, sampleRate: Int): Int =
    packetSamples(packet, sampleRate)

internal actual fun webPacketHasLbrr(packet: String): Int = packetHasLbrr(packet)

internal actual fun webPacketParse(packet: String): String = packetParse(packet)

internal actual fun webPacketPad(packet: String, length: Int, newLength: Int): String =
    packetPad(packet, length, newLength)

internal actual fun webPacketUnpad(packet: String): String = packetUnpad(packet)

internal actual fun webRepacketizerCreate(): Int = repacketizerCreate()

internal actual fun webRepacketizerFree(handle: Int) = repacketizerFree(handle)

internal actual fun webRepacketizerReset(handle: Int): Int = repacketizerReset(handle)

internal actual fun webRepacketizerCat(handle: Int, packet: String): Int =
    repacketizerCat(handle, packet)

internal actual fun webRepacketizerFrameCount(handle: Int): Int = repacketizerFrameCount(handle)

internal actual fun webRepacketizerOutRange(
    handle: Int,
    begin: Int,
    end: Int,
    maxBytes: Int,
): String = repacketizerOutRange(handle, begin, end, maxBytes)

internal actual fun webRepacketizerOut(handle: Int, maxBytes: Int): String =
    repacketizerOut(handle, maxBytes)

internal actual fun webPcmSoftClip(
    pcm: String,
    frameSize: Int,
    channels: Int,
    memory: String,
): String = pcmSoftClip(pcm, frameSize, channels, memory)
