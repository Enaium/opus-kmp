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

@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package cn.enaium.opus

import kotlin.io.encoding.Base64

/**
 * Web bridge shared by the Kotlin/JS and Kotlin/Wasm targets.
 *
 * The JavaScript glue owns the WebAssembly instance and exposes one function
 * per C entry point on `globalThis.__opusWeb`. Pointers are 32-bit WebAssembly
 * addresses and therefore `Int`s here.
 *
 * Bulk data (PCM samples and packets) crosses the boundary as base64 strings
 * because Kotlin/JS and Kotlin/Wasm cannot share typed arrays with a module
 * compiled outside their own toolchain.
 */

internal expect fun webInit()

internal expect fun webVersionString(): String

internal expect fun webStrerror(error: Int): String

internal expect fun webEncoderCreate(sampleRate: Int, channels: Int, application: Int): Int

internal expect fun webEncoderFree(handle: Int)

internal expect fun webEncoderReset(handle: Int): Int

internal expect fun webEncoderSampleRate(handle: Int): Int

internal expect fun webEncoderChannels(handle: Int): Int

internal expect fun webEncoderEncode(
    handle: Int,
    samples: String,
    frameSize: Int,
    maxBytes: Int,
): String

internal expect fun webEncoderEncodeFloat(
    handle: Int,
    samples: String,
    frameSize: Int,
    maxBytes: Int,
): String

internal expect fun webEncoderCtlInt(handle: Int, request: Int, value: Int): Int

internal expect fun webEncoderGetInt(handle: Int, request: Int): Int

/** Unsigned 32-bit range as a decimal string, so it survives JS number precision. */
internal expect fun webEncoderFinalRange(handle: Int): String

internal expect fun webDecoderCreate(sampleRate: Int, channels: Int): Int

internal expect fun webDecoderFree(handle: Int)

internal expect fun webDecoderReset(handle: Int): Int

internal expect fun webDecoderSampleRate(handle: Int): Int

internal expect fun webDecoderChannels(handle: Int): Int

internal expect fun webDecoderDecode(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String

internal expect fun webDecoderDecodeFloat(
    handle: Int,
    packet: String,
    frameSize: Int,
    fec: Boolean,
    maxSamples: Int,
): String

internal expect fun webDecoderCtlInt(handle: Int, request: Int, value: Int): Int

internal expect fun webDecoderGetInt(handle: Int, request: Int): Int

internal expect fun webDecoderFinalRange(handle: Int): String

internal expect fun webDecoderGetNbSamples(handle: Int, packet: String): Int

internal expect fun webPacketBandwidth(packet: String): Int

internal expect fun webPacketSamplesPerFrame(packet: String, sampleRate: Int): Int

internal expect fun webPacketChannels(packet: String): Int

internal expect fun webPacketFrames(packet: String): Int

internal expect fun webPacketSamples(packet: String, sampleRate: Int): Int

internal expect fun webPacketHasLbrr(packet: String): Int

/** `count:sizesCsv:offsetsCsv` or `<error>::`. */
internal expect fun webPacketParse(packet: String): String

/** `code:base64` with the whole padded buffer. */
internal expect fun webPacketPad(packet: String, length: Int, newLength: Int): String

/** `code:base64` with the unpadded prefix. */
internal expect fun webPacketUnpad(packet: String): String

internal expect fun webRepacketizerCreate(): Int

internal expect fun webRepacketizerFree(handle: Int)

internal expect fun webRepacketizerReset(handle: Int): Int

internal expect fun webRepacketizerCat(handle: Int, packet: String): Int

internal expect fun webRepacketizerFrameCount(handle: Int): Int

internal expect fun webRepacketizerOutRange(
    handle: Int,
    begin: Int,
    end: Int,
    maxBytes: Int,
): String

internal expect fun webRepacketizerOut(handle: Int, maxBytes: Int): String

/** `code:pcmBase64:memoryBase64` with the clipped PCM and updated state. */
internal expect fun webPcmSoftClip(
    pcm: String,
    frameSize: Int,
    channels: Int,
    memory: String,
): String

/** Base64 and little-endian conversions used by the web bridge. */
internal object OpusWebPacking {

    fun bytesToBase64(bytes: ByteArray): String = Base64.encode(bytes)

    fun bytesFromBase64(text: String): ByteArray = Base64.decode(text)

    fun shortsToBase64(samples: ShortArray, offset: Int = 0, count: Int = samples.size - offset): String {
        val bytes = ByteArray(count * 2)
        for (index in 0 until count) {
            val value = samples[offset + index].toInt()
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        return Base64.encode(bytes)
    }

    fun shortsFromBase64(text: String): ShortArray {
        val bytes = Base64.decode(text)
        return ShortArray(bytes.size / 2) { index ->
            (((bytes[index * 2].toInt() and 0xFF) or (bytes[index * 2 + 1].toInt() shl 8))).toShort()
        }
    }

    fun floatsToBase64(samples: FloatArray, offset: Int = 0, count: Int = samples.size - offset): String {
        val bytes = ByteArray(count * 4)
        for (index in 0 until count) {
            val bits = samples[offset + index].toBits()
            bytes[index * 4] = (bits and 0xFF).toByte()
            bytes[index * 4 + 1] = ((bits ushr 8) and 0xFF).toByte()
            bytes[index * 4 + 2] = ((bits ushr 16) and 0xFF).toByte()
            bytes[index * 4 + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        return Base64.encode(bytes)
    }

    fun floatsFromBase64(text: String): FloatArray {
        val bytes = Base64.decode(text)
        return FloatArray(bytes.size / 4) { index ->
            Float.fromBits(
                (bytes[index * 4].toInt() and 0xFF) or
                    ((bytes[index * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[index * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[index * 4 + 3].toInt() and 0xFF) shl 24),
            )
        }
    }

    /** Parses the `code:payload` protocol; returns `null` for a malformed result. */
    fun splitResult(result: String): Pair<Int, String>? {
        val separator = result.indexOf(':')
        if (separator < 0) return null
        val code = result.substring(0, separator).toIntOrNull() ?: return null
        return code to result.substring(separator + 1)
    }
}

/**
 * JavaScript glue that owns the WebAssembly instance.
 *
 * Opus is compiled to a standalone WASI module. The glue provides the handful
 * of WASI imports the C runtime references, exposes one function per C entry
 * point on `globalThis.__opusWeb`, and marshals strings through the module's
 * own `ok_alloc`.
 *
 * Bulk data (PCM samples and packets) crosses the boundary as base64 strings.
 * Results are returned as `code:base64` or `code:csv` strings so a single call
 * carries both the status and the payload.
 */
internal const val OPUS_WEB_INIT_JS: String = """
(base64) => {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  let wasmExports = null;
  const memory = () => new Uint8Array(wasmExports.memory.buffer);
  const dataView = () => new DataView(wasmExports.memory.buffer);
  const decoder = new TextDecoder();

  const encodeBase64 = (data) => {
    let text = '';
    const chunk = 0x8000;
    for (let i = 0; i < data.length; i += chunk) {
      text += String.fromCharCode.apply(null, data.subarray(i, i + chunk));
    }
    return btoa(text);
  };

  const decodeBase64 = (text) => {
    const raw = atob(text);
    const out = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
    return out;
  };

  const readString = (pointer) => {
    if (!pointer) return '';
    const data = memory();
    let end = pointer;
    while (data[end] !== 0) end++;
    return decoder.decode(data.subarray(pointer, end));
  };

  const writeBytes = (data) => {
    const pointer = wasmExports.ok_alloc(data.length || 1);
    if (!pointer) return 0;
    memory().set(data, pointer);
    return pointer;
  };

  const bytesAt = (pointer, size) => {
    if (!pointer || size <= 0) return '';
    return encodeBase64(memory().slice(pointer, pointer + size));
  };

  const imports = {
    env: {
      emscripten_notify_memory_growth() {},
      __syscall_unlinkat() { return 0; },
      __syscall_renameat() { return 0; },
    },
    wasi_snapshot_preview1: {
      fd_write(fd, iovs, iovsLength, written) {
        let total = 0;
        for (let i = 0; i < iovsLength; i++) {
          const pointer = dataView().getUint32(iovs + i * 8, true);
          const length = dataView().getUint32(iovs + i * 8 + 4, true);
          const text = decoder.decode(memory().subarray(pointer, pointer + length));
          if (text.trim().length > 0) {
            if (fd === 2) console.error(text.trimEnd());
            else console.log(text.trimEnd());
          }
          total += length;
        }
        dataView().setUint32(written, total, true);
        return 0;
      },
      fd_read() { return 0; },
      fd_seek() { return 0; },
      fd_close() { return 0; },
      fd_fdstat_get() { return 0; },
      clock_time_get(clock, precision, out) {
        const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
        dataView().setBigUint64(out, BigInt(Math.floor(now * 1e6)), true);
        return 0;
      },
      environ_sizes_get(count, size) {
        dataView().setUint32(count, 0, true);
        dataView().setUint32(size, 0, true);
        return 0;
      },
      environ_get() { return 0; },
      random_get(pointer, size) {
        const data = memory();
        for (let i = 0; i < size; i++) data[pointer + i] = (Math.random() * 256) | 0;
        return 0;
      },
      proc_exit(code) {
        throw new Error('Opus WebAssembly exited with code ' + code);
      },
    },
  };

  const instance = new WebAssembly.Instance(new WebAssembly.Module(bytes), imports);
  wasmExports = instance.exports;
  if (wasmExports._initialize) wasmExports._initialize();

  const encodeResult = (code, pointer, size) => code + ':' + (code > 0 ? bytesAt(pointer, size) : '');

  globalThis.__opusWeb = {
    versionString() {
      return readString(wasmExports.ok_version_string());
    },
    strerror(error) {
      return readString(wasmExports.ok_strerror(error));
    },
    encoderCreate(sampleRate, channels, application) {
      return wasmExports.ok_encoder_create(sampleRate, channels, application);
    },
    encoderFree(handle) {
      wasmExports.ok_encoder_free(handle);
    },
    encoderReset(handle) {
      return wasmExports.ok_encoder_reset(handle);
    },
    encoderSampleRate(handle) {
      return wasmExports.ok_encoder_sample_rate(handle);
    },
    encoderChannels(handle) {
      return wasmExports.ok_encoder_channels(handle);
    },
    encoderEncode(handle, samplesBase64, frameSize, maxBytes) {
      const data = decodeBase64(samplesBase64);
      const input = writeBytes(data);
      const output = wasmExports.ok_alloc(maxBytes || 1);
      if (!input || !output) {
        if (input) wasmExports.ok_free(input);
        if (output) wasmExports.ok_free(output);
        return '-1000:';
      }
      const code = wasmExports.ok_encoder_encode(handle, input, frameSize, output, maxBytes);
      const result = encodeResult(code, output, code);
      wasmExports.ok_free(input);
      wasmExports.ok_free(output);
      return result;
    },
    encoderEncodeFloat(handle, samplesBase64, frameSize, maxBytes) {
      const data = decodeBase64(samplesBase64);
      const input = writeBytes(data);
      const output = wasmExports.ok_alloc(maxBytes || 1);
      if (!input || !output) {
        if (input) wasmExports.ok_free(input);
        if (output) wasmExports.ok_free(output);
        return '-1000:';
      }
      const code = wasmExports.ok_encoder_encode_float(handle, input, frameSize, output, maxBytes);
      const result = encodeResult(code, output, code);
      wasmExports.ok_free(input);
      wasmExports.ok_free(output);
      return result;
    },
    encoderCtlInt(handle, request, value) {
      return wasmExports.ok_encoder_ctl_int(handle, request, value);
    },
    encoderGetInt(handle, request) {
      return wasmExports.ok_encoder_get_int(handle, request);
    },
    encoderFinalRange(handle) {
      return String(wasmExports.ok_encoder_final_range(handle) >>> 0);
    },
    decoderCreate(sampleRate, channels) {
      return wasmExports.ok_decoder_create(sampleRate, channels);
    },
    decoderFree(handle) {
      wasmExports.ok_decoder_free(handle);
    },
    decoderReset(handle) {
      return wasmExports.ok_decoder_reset(handle);
    },
    decoderSampleRate(handle) {
      return wasmExports.ok_decoder_sample_rate(handle);
    },
    decoderChannels(handle) {
      return wasmExports.ok_decoder_channels(handle);
    },
    decoderDecode(handle, packetBase64, frameSize, fec, maxSamples) {
      const data = packetBase64 ? decodeBase64(packetBase64) : new Uint8Array(0);
      const input = data.length > 0 ? writeBytes(data) : 0;
      const output = wasmExports.ok_alloc((maxSamples || 1) * 2);
      if ((data.length > 0 && !input) || !output) {
        if (input) wasmExports.ok_free(input);
        if (output) wasmExports.ok_free(output);
        return '-1000:';
      }
      const code = wasmExports.ok_decoder_decode(handle, input, data.length, frameSize, output, maxSamples, fec ? 1 : 0);
      const channels = wasmExports.ok_decoder_channels(handle);
      const result = encodeResult(code, output, code * channels * 2);
      if (input) wasmExports.ok_free(input);
      wasmExports.ok_free(output);
      return result;
    },
    decoderDecodeFloat(handle, packetBase64, frameSize, fec, maxSamples) {
      const data = packetBase64 ? decodeBase64(packetBase64) : new Uint8Array(0);
      const input = data.length > 0 ? writeBytes(data) : 0;
      const output = wasmExports.ok_alloc((maxSamples || 1) * 4);
      if ((data.length > 0 && !input) || !output) {
        if (input) wasmExports.ok_free(input);
        if (output) wasmExports.ok_free(output);
        return '-1000:';
      }
      const code = wasmExports.ok_decoder_decode_float(handle, input, data.length, frameSize, output, maxSamples, fec ? 1 : 0);
      const channels = wasmExports.ok_decoder_channels(handle);
      const result = encodeResult(code, output, code * channels * 4);
      if (input) wasmExports.ok_free(input);
      wasmExports.ok_free(output);
      return result;
    },
    decoderCtlInt(handle, request, value) {
      return wasmExports.ok_decoder_ctl_int(handle, request, value);
    },
    decoderGetInt(handle, request) {
      return wasmExports.ok_decoder_get_int(handle, request);
    },
    decoderFinalRange(handle) {
      return String(wasmExports.ok_decoder_final_range(handle) >>> 0);
    },
    decoderGetNbSamples(handle, packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_decoder_get_nb_samples(handle, input, data.length);
      wasmExports.ok_free(input);
      return code;
    },
    packetBandwidth(packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_packet_get_bandwidth(input, data.length);
      wasmExports.ok_free(input);
      return code;
    },
    packetSamplesPerFrame(packetBase64, sampleRate) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_packet_get_samples_per_frame(input, data.length, sampleRate);
      wasmExports.ok_free(input);
      return code;
    },
    packetChannels(packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_packet_get_nb_channels(input, data.length);
      wasmExports.ok_free(input);
      return code;
    },
    packetFrames(packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_packet_get_nb_frames(input, data.length);
      wasmExports.ok_free(input);
      return code;
    },
    packetSamples(packetBase64, sampleRate) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_packet_get_nb_samples(input, data.length, sampleRate);
      wasmExports.ok_free(input);
      return code;
    },
    packetHasLbrr(packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_packet_has_lbrr(input, data.length);
      wasmExports.ok_free(input);
      return code;
    },
    packetParse(packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      const sizes = wasmExports.ok_alloc(48 * 4);
      const offsets = wasmExports.ok_alloc(48 * 4);
      if (!input || !sizes || !offsets) {
        if (input) wasmExports.ok_free(input);
        if (sizes) wasmExports.ok_free(sizes);
        if (offsets) wasmExports.ok_free(offsets);
        return '-1000::';
      }
      const count = wasmExports.ok_packet_parse(input, data.length, sizes, offsets, 48);
      let result;
      if (count < 0) {
        result = count + '::';
      } else {
        const sizeView = new Int32Array(memory().buffer, sizes, count);
        const offsetView = new Int32Array(memory().buffer, offsets, count);
        result = count + ':' + Array.from(sizeView).join(',') + ':' + Array.from(offsetView).join(',');
      }
      wasmExports.ok_free(input);
      wasmExports.ok_free(sizes);
      wasmExports.ok_free(offsets);
      return result;
    },
    packetPad(packetBase64, length, newLength) {
      const data = decodeBase64(packetBase64);
      const pointer = writeBytes(data);
      if (!pointer) return '-1000:';
      const code = wasmExports.ok_packet_pad(pointer, length, newLength);
      const result = code + ':' + bytesAt(pointer, newLength);
      wasmExports.ok_free(pointer);
      return result;
    },
    packetUnpad(packetBase64) {
      const data = decodeBase64(packetBase64);
      const pointer = writeBytes(data);
      if (!pointer) return '-1000:';
      const code = wasmExports.ok_packet_unpad(pointer, data.length);
      const result = code + ':' + (code > 0 ? bytesAt(pointer, code) : '');
      wasmExports.ok_free(pointer);
      return result;
    },
    repacketizerCreate() {
      return wasmExports.ok_repacketizer_create();
    },
    repacketizerFree(handle) {
      wasmExports.ok_repacketizer_free(handle);
    },
    repacketizerReset(handle) {
      return wasmExports.ok_repacketizer_reset(handle);
    },
    repacketizerCat(handle, packetBase64) {
      const data = decodeBase64(packetBase64);
      const input = writeBytes(data);
      if (!input) return -1000;
      const code = wasmExports.ok_repacketizer_cat(handle, input, data.length);
      wasmExports.ok_free(input);
      return code;
    },
    repacketizerFrameCount(handle) {
      return wasmExports.ok_repacketizer_get_nb_frames(handle);
    },
    repacketizerOutRange(handle, begin, end, maxBytes) {
      const output = wasmExports.ok_alloc(maxBytes || 1);
      if (!output) return '-1000:';
      const code = wasmExports.ok_repacketizer_out_range(handle, begin, end, output, maxBytes);
      const result = encodeResult(code, output, code);
      wasmExports.ok_free(output);
      return result;
    },
    repacketizerOut(handle, maxBytes) {
      const output = wasmExports.ok_alloc(maxBytes || 1);
      if (!output) return '-1000:';
      const code = wasmExports.ok_repacketizer_out(handle, output, maxBytes);
      const result = encodeResult(code, output, code);
      wasmExports.ok_free(output);
      return result;
    },
    pcmSoftClip(pcmBase64, frameSize, channels, memoryBase64) {
      const pcm = decodeBase64(pcmBase64);
      const state = decodeBase64(memoryBase64);
      const pcmPointer = writeBytes(pcm);
      const statePointer = writeBytes(state);
      if (!pcmPointer || !statePointer) {
        if (pcmPointer) wasmExports.ok_free(pcmPointer);
        if (statePointer) wasmExports.ok_free(statePointer);
        return '-1000:';
      }
      const code = wasmExports.ok_pcm_soft_clip(pcmPointer, frameSize, channels, statePointer);
      // The soft-clip state must survive between calls, exactly like the
      // caller-owned memory array on JNI and Kotlin/Native.
      const result = code + ':' + bytesAt(pcmPointer, pcm.length) + ':' + bytesAt(statePointer, state.length);
      wasmExports.ok_free(pcmPointer);
      wasmExports.ok_free(statePointer);
      return result;
    },
  };
}
"""
