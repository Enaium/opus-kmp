/*
 * C shim for the Opus Kotlin Multiplatform binding.
 *
 * The same implementation is compiled three ways:
 *   - into the static archive linked by every Kotlin/Native cinterop klib,
 *   - into the JNI shared library (opus_jni.c),
 *   - into the standalone WebAssembly module (opus_wasm_entry.c).
 *
 * All three expose this API, so argument validation and the encode/decode
 * contract are identical on every platform.
 *
 * Ownership:
 *   - every `*_create()` result must be released with the matching `*_free()`;
 *   - functions write into caller-provided buffers and return the number of
 *     bytes/samples written, or a negative Opus error code.
 *
 * The shim compiles Opus in its portable floating-point configuration, with
 * runtime CPU dispatch disabled, so every target shares one code path.
 */
#ifndef OPUS_KMP_H
#define OPUS_KMP_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** A shim function rejected one of its arguments (or allocation failed). */
#define OK_INVALID (-1000)

/** Opaque encoder state. */
typedef struct ok_encoder ok_encoder;

/** Opaque decoder state. */
typedef struct ok_decoder ok_decoder;

/** Opaque repacketizer state. */
typedef struct ok_repacketizer ok_repacketizer;

/* ------------------------------------------------------------------ */
/* Library version                                                    */
/* ------------------------------------------------------------------ */

/** Opus library version, e.g. "1.5.2". */
const char *ok_version_string(void);

/** Human-readable description of an Opus error code. */
const char *ok_strerror(int error);

/* ------------------------------------------------------------------ */
/* Encoder                                                            */
/* ------------------------------------------------------------------ */

/**
 * Creates an encoder. `sample_rate` is one of 8000, 12000, 16000, 24000 or
 * 48000 Hz, `channels` is 1 or 2, and `application` is one of the
 * `OPUS_APPLICATION_*` values. Returns NULL when Opus rejects the arguments.
 */
ok_encoder *ok_encoder_create(int sample_rate, int channels, int application);

void ok_encoder_free(ok_encoder *encoder);

/** Resets the encoder state, keeping every control setting. */
int ok_encoder_reset(ok_encoder *encoder);

int ok_encoder_sample_rate(const ok_encoder *encoder);

int ok_encoder_channels(const ok_encoder *encoder);

/**
 * Encodes `frame_size` samples per channel of 16-bit PCM into `out`, which
 * holds at least `max_bytes`. Returns the packet size in bytes or a negative
 * Opus error code. `frame_size` must be a valid Opus frame duration for the
 * encoder's sample rate.
 */
int ok_encoder_encode(ok_encoder *encoder, const int16_t *pcm, int frame_size,
                      uint8_t *out, int max_bytes);

/** Floating-point variant of `ok_encoder_encode`, samples in -1..1. */
int ok_encoder_encode_float(ok_encoder *encoder, const float *pcm, int frame_size,
                            uint8_t *out, int max_bytes);

/** Sends an integer-valued `OPUS_SET_*` request. Returns OPUS_OK or an error. */
int ok_encoder_ctl_int(ok_encoder *encoder, int request, int value);

/** Reads an integer-valued `OPUS_GET_*` request, or returns an error code. */
int ok_encoder_get_int(ok_encoder *encoder, int request);

/** Reads `OPUS_GET_FINAL_RANGE` as an unsigned 32-bit value. */
uint32_t ok_encoder_final_range(ok_encoder *encoder);

/* ------------------------------------------------------------------ */
/* Decoder                                                            */
/* ------------------------------------------------------------------ */

/** Creates a decoder for 8000, 12000, 16000, 24000 or 48000 Hz, 1 or 2 channels. */
ok_decoder *ok_decoder_create(int sample_rate, int channels);

void ok_decoder_free(ok_decoder *decoder);

/** Resets the decoder state, keeping every control setting. */
int ok_decoder_reset(ok_decoder *decoder);

int ok_decoder_sample_rate(const ok_decoder *decoder);

int ok_decoder_channels(const ok_decoder *decoder);

/**
 * Decodes one packet into `out`, which holds at least
 * `frame_size * channels` samples. A NULL packet (or `len` 0) runs packet-loss
 * concealment; `fec` requests in-band FEC recovery for the packet that follows.
 * Returns the number of decoded samples per channel, or an error code.
 */
int ok_decoder_decode(ok_decoder *decoder, const uint8_t *packet, int len,
                      int frame_size, int16_t *out, int max_samples, int fec);

/** Floating-point variant of `ok_decoder_decode`. */
int ok_decoder_decode_float(ok_decoder *decoder, const uint8_t *packet, int len,
                            int frame_size, float *out, int max_samples, int fec);

/** Sends an integer-valued `OPUS_SET_*` request. */
int ok_decoder_ctl_int(ok_decoder *decoder, int request, int value);

/** Reads an integer-valued `OPUS_GET_*` request, or returns an error code. */
int ok_decoder_get_int(ok_decoder *decoder, int request);

/** Reads `OPUS_GET_FINAL_RANGE` as an unsigned 32-bit value. */
uint32_t ok_decoder_final_range(ok_decoder *decoder);

/** Samples per channel contained in a packet, or an error code. */
int ok_decoder_get_nb_samples(ok_decoder *decoder, const uint8_t *packet, int len);

/* ------------------------------------------------------------------ */
/* Packet inspection                                                  */
/* ------------------------------------------------------------------ */

int ok_packet_get_bandwidth(const uint8_t *packet, int len);

int ok_packet_get_samples_per_frame(const uint8_t *packet, int len, int sample_rate);

int ok_packet_get_nb_channels(const uint8_t *packet, int len);

int ok_packet_get_nb_frames(const uint8_t *packet, int len);

int ok_packet_get_nb_samples(const uint8_t *packet, int len, int sample_rate);

/** 1 when the packet carries LBRR frames, 0 when not, negative on error. */
int ok_packet_has_lbrr(const uint8_t *packet, int len);

/**
 * Splits a packet into its frames. `sizes` and `offsets` each hold at least
 * `max_frames` entries and receive the byte length and byte offset of every
 * frame. Returns the frame count or a negative Opus error code.
 */
int ok_packet_parse(const uint8_t *packet, int len, int *sizes, int *offsets,
                    int max_frames);

/** Pads a packet in place to `new_len` bytes. Returns OPUS_OK or an error. */
int ok_packet_pad(uint8_t *packet, int len, int new_len);

/** Removes padding in place. Returns the new length or an error code. */
int ok_packet_unpad(uint8_t *packet, int len);

/* ------------------------------------------------------------------ */
/* Repacketizer                                                       */
/* ------------------------------------------------------------------ */

ok_repacketizer *ok_repacketizer_create(void);

void ok_repacketizer_free(ok_repacketizer *repacketizer);

/** Clears the accumulated frames. */
int ok_repacketizer_reset(ok_repacketizer *repacketizer);

int ok_repacketizer_cat(ok_repacketizer *repacketizer, const uint8_t *packet, int len);

int ok_repacketizer_get_nb_frames(ok_repacketizer *repacketizer);

/**
 * Writes frames [begin, end) as one packet into `out` (`max_bytes` capacity).
 * Returns the packet size or a negative error code.
 */
int ok_repacketizer_out_range(ok_repacketizer *repacketizer, int begin, int end,
                              uint8_t *out, int max_bytes);

/** Writes every accumulated frame as one packet. */
int ok_repacketizer_out(ok_repacketizer *repacketizer, uint8_t *out, int max_bytes);

/* ------------------------------------------------------------------ */
/* Shared helpers                                                     */
/* ------------------------------------------------------------------ */

/**
 * Applies Opus soft clipping in place. `memory` holds `channels` floats of
 * persistent state and may be NULL.
 */
int ok_pcm_soft_clip(float *pcm, int frame_size, int channels, float *memory);

/* Memory used by the WebAssembly bridge. */
void *ok_alloc(size_t size);

void ok_free(void *pointer);

#ifdef __cplusplus
}
#endif

#endif /* OPUS_KMP_H */
