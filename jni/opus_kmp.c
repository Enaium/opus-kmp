/*
 * C shim for the Opus Kotlin Multiplatform binding. See opus_kmp.h for the
 * contract shared by the JNI, Kotlin/Native and WebAssembly bridges.
 */
#include "opus_kmp.h"

#include <stdlib.h>
#include <string.h>

#include "opus.h"

struct ok_encoder {
  OpusEncoder *state;
  int sample_rate;
  int channels;
};

struct ok_decoder {
  OpusDecoder *state;
  int sample_rate;
  int channels;
};

struct ok_repacketizer {
  OpusRepacketizer *state;
  /* Opus stores frame pointers into the packets passed to repacketizer_cat
   * rather than copying them. The JNI array release and the Kotlin garbage
   * collector can free those buffers before out() is called, so the shim keeps
   * one stable copy of every packet it has accepted. */
  uint8_t **packets;
  int count;
  int capacity;
};

/* ------------------------------------------------------------------------- */
/* Memory                                                                     */
/* ------------------------------------------------------------------------- */

void *ok_alloc(size_t size) { return malloc(size); }

void ok_free(void *pointer) { free(pointer); }

/* ------------------------------------------------------------------------- */
/* Library version                                                            */
/* ------------------------------------------------------------------------- */

const char *ok_version_string(void) {
  const char *version = opus_get_version_string();
  return version ? version : "";
}

const char *ok_strerror(int error) {
  const char *message = opus_strerror(error);
  return message ? message : "";
}

/* ------------------------------------------------------------------------- */
/* Argument validation                                                        */
/* ------------------------------------------------------------------------- */

static int ok_sample_rate_valid(int sample_rate) {
  switch (sample_rate) {
    case 8000:
    case 12000:
    case 16000:
    case 24000:
    case 48000:
      return 1;
    default:
      return 0;
  }
}

static int ok_channels_valid(int channels) { return channels == 1 || channels == 2; }

static int ok_application_valid(int application) {
  return application == OPUS_APPLICATION_VOIP || application == OPUS_APPLICATION_AUDIO ||
         application == OPUS_APPLICATION_RESTRICTED_LOWDELAY;
}

/* Requests the binding may pass with an int value. Pointer-valued requests
 * (notably OPUS_SET_DNN_BLOB_REQUEST) are deliberately excluded: pushing an
 * int through them would dereference an invalid pointer inside Opus. */
static int ok_request_is_encoder_set(int request) {
  switch (request) {
    case OPUS_SET_APPLICATION_REQUEST:
    case OPUS_SET_BITRATE_REQUEST:
    case OPUS_SET_MAX_BANDWIDTH_REQUEST:
    case OPUS_SET_VBR_REQUEST:
    case OPUS_SET_BANDWIDTH_REQUEST:
    case OPUS_SET_COMPLEXITY_REQUEST:
    case OPUS_SET_INBAND_FEC_REQUEST:
    case OPUS_SET_PACKET_LOSS_PERC_REQUEST:
    case OPUS_SET_DTX_REQUEST:
    case OPUS_SET_VBR_CONSTRAINT_REQUEST:
    case OPUS_SET_FORCE_CHANNELS_REQUEST:
    case OPUS_SET_SIGNAL_REQUEST:
    case OPUS_SET_GAIN_REQUEST:
    case OPUS_SET_LSB_DEPTH_REQUEST:
    case OPUS_SET_EXPERT_FRAME_DURATION_REQUEST:
    case OPUS_SET_PREDICTION_DISABLED_REQUEST:
    case OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
    case OPUS_SET_DRED_DURATION_REQUEST:
      return 1;
    default:
      return 0;
  }
}

static int ok_request_is_decoder_set(int request) {
  switch (request) {
    case OPUS_SET_GAIN_REQUEST:
    case OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
    case OPUS_SET_DRED_DURATION_REQUEST:
      return 1;
    default:
      return 0;
  }
}

static int ok_request_is_get(int request) {
  switch (request) {
    case OPUS_GET_APPLICATION_REQUEST:
    case OPUS_GET_BITRATE_REQUEST:
    case OPUS_GET_MAX_BANDWIDTH_REQUEST:
    case OPUS_GET_VBR_REQUEST:
    case OPUS_GET_BANDWIDTH_REQUEST:
    case OPUS_GET_COMPLEXITY_REQUEST:
    case OPUS_GET_INBAND_FEC_REQUEST:
    case OPUS_GET_PACKET_LOSS_PERC_REQUEST:
    case OPUS_GET_DTX_REQUEST:
    case OPUS_GET_VBR_CONSTRAINT_REQUEST:
    case OPUS_GET_FORCE_CHANNELS_REQUEST:
    case OPUS_GET_SIGNAL_REQUEST:
    case OPUS_GET_LOOKAHEAD_REQUEST:
    case OPUS_GET_SAMPLE_RATE_REQUEST:
    case OPUS_GET_PITCH_REQUEST:
    case OPUS_GET_GAIN_REQUEST:
    case OPUS_GET_LSB_DEPTH_REQUEST:
    case OPUS_GET_LAST_PACKET_DURATION_REQUEST:
    case OPUS_GET_EXPERT_FRAME_DURATION_REQUEST:
    case OPUS_GET_PREDICTION_DISABLED_REQUEST:
    case OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
    case OPUS_GET_IN_DTX_REQUEST:
    case OPUS_GET_DRED_DURATION_REQUEST:
      return 1;
    default:
      return 0;
  }
}

/* ------------------------------------------------------------------------- */
/* Encoder                                                                    */
/* ------------------------------------------------------------------------- */

ok_encoder *ok_encoder_create(int sample_rate, int channels, int application) {
  if (!ok_sample_rate_valid(sample_rate) || !ok_channels_valid(channels) ||
      !ok_application_valid(application)) {
    return NULL;
  }

  ok_encoder *encoder = (ok_encoder *)calloc(1, sizeof(ok_encoder));
  if (!encoder) return NULL;

  int error = OPUS_OK;
  encoder->state = opus_encoder_create(sample_rate, channels, application, &error);
  if (error != OPUS_OK || !encoder->state) {
    free(encoder);
    return NULL;
  }
  encoder->sample_rate = sample_rate;
  encoder->channels = channels;
  return encoder;
}

void ok_encoder_free(ok_encoder *encoder) {
  if (!encoder) return;
  if (encoder->state) opus_encoder_destroy(encoder->state);
  free(encoder);
}

int ok_encoder_reset(ok_encoder *encoder) {
  if (!encoder || !encoder->state) return OK_INVALID;
  return opus_encoder_ctl(encoder->state, OPUS_RESET_STATE);
}

int ok_encoder_sample_rate(const ok_encoder *encoder) {
  return encoder ? encoder->sample_rate : 0;
}

int ok_encoder_channels(const ok_encoder *encoder) { return encoder ? encoder->channels : 0; }

int ok_encoder_encode(ok_encoder *encoder, const int16_t *pcm, int frame_size,
                      uint8_t *out, int max_bytes) {
  if (!encoder || !encoder->state || !pcm || !out || frame_size <= 0 || max_bytes <= 0) {
    return OK_INVALID;
  }
  return opus_encode(encoder->state, pcm, frame_size, out, max_bytes);
}

int ok_encoder_encode_float(ok_encoder *encoder, const float *pcm, int frame_size,
                            uint8_t *out, int max_bytes) {
  if (!encoder || !encoder->state || !pcm || !out || frame_size <= 0 || max_bytes <= 0) {
    return OK_INVALID;
  }
  return opus_encode_float(encoder->state, pcm, frame_size, out, max_bytes);
}

int ok_encoder_ctl_int(ok_encoder *encoder, int request, int value) {
  if (!encoder || !encoder->state || !ok_request_is_encoder_set(request)) return OK_INVALID;
  return opus_encoder_ctl(encoder->state, request, value);
}

int ok_encoder_get_int(ok_encoder *encoder, int request) {
  if (!encoder || !encoder->state || !ok_request_is_get(request)) return 0;
  int value = 0;
  if (opus_encoder_ctl(encoder->state, request, &value) != OPUS_OK) return 0;
  return value;
}

uint32_t ok_encoder_final_range(ok_encoder *encoder) {
  if (!encoder || !encoder->state) return 0;
  opus_uint32 range = 0;
  if (opus_encoder_ctl(encoder->state, OPUS_GET_FINAL_RANGE_REQUEST, &range) != OPUS_OK) return 0;
  return (uint32_t)range;
}

/* ------------------------------------------------------------------------- */
/* Decoder                                                                    */
/* ------------------------------------------------------------------------- */

ok_decoder *ok_decoder_create(int sample_rate, int channels) {
  if (!ok_sample_rate_valid(sample_rate) || !ok_channels_valid(channels)) return NULL;

  ok_decoder *decoder = (ok_decoder *)calloc(1, sizeof(ok_decoder));
  if (!decoder) return NULL;

  int error = OPUS_OK;
  decoder->state = opus_decoder_create(sample_rate, channels, &error);
  if (error != OPUS_OK || !decoder->state) {
    free(decoder);
    return NULL;
  }
  decoder->sample_rate = sample_rate;
  decoder->channels = channels;
  return decoder;
}

void ok_decoder_free(ok_decoder *decoder) {
  if (!decoder) return;
  if (decoder->state) opus_decoder_destroy(decoder->state);
  free(decoder);
}

int ok_decoder_reset(ok_decoder *decoder) {
  if (!decoder || !decoder->state) return OK_INVALID;
  return opus_decoder_ctl(decoder->state, OPUS_RESET_STATE);
}

int ok_decoder_sample_rate(const ok_decoder *decoder) {
  return decoder ? decoder->sample_rate : 0;
}

int ok_decoder_channels(const ok_decoder *decoder) { return decoder ? decoder->channels : 0; }

int ok_decoder_decode(ok_decoder *decoder, const uint8_t *packet, int len, int frame_size,
                      int16_t *out, int max_samples, int fec) {
  if (!decoder || !decoder->state || !out || frame_size <= 0) return OK_INVALID;
  if (len < 0 || (!packet && len > 0)) return OK_INVALID;
  if ((long long)frame_size * decoder->channels > max_samples) return OK_INVALID;
  return opus_decode(decoder->state, packet, len, out, frame_size, fec ? 1 : 0);
}

int ok_decoder_decode_float(ok_decoder *decoder, const uint8_t *packet, int len, int frame_size,
                            float *out, int max_samples, int fec) {
  if (!decoder || !decoder->state || !out || frame_size <= 0) return OK_INVALID;
  if (len < 0 || (!packet && len > 0)) return OK_INVALID;
  if ((long long)frame_size * decoder->channels > max_samples) return OK_INVALID;
  return opus_decode_float(decoder->state, packet, len, out, frame_size, fec ? 1 : 0);
}

int ok_decoder_ctl_int(ok_decoder *decoder, int request, int value) {
  if (!decoder || !decoder->state || !ok_request_is_decoder_set(request)) return OK_INVALID;
  return opus_decoder_ctl(decoder->state, request, value);
}

int ok_decoder_get_int(ok_decoder *decoder, int request) {
  if (!decoder || !decoder->state || !ok_request_is_get(request)) return 0;
  int value = 0;
  if (opus_decoder_ctl(decoder->state, request, &value) != OPUS_OK) return 0;
  return value;
}

uint32_t ok_decoder_final_range(ok_decoder *decoder) {
  if (!decoder || !decoder->state) return 0;
  opus_uint32 range = 0;
  if (opus_decoder_ctl(decoder->state, OPUS_GET_FINAL_RANGE_REQUEST, &range) != OPUS_OK) return 0;
  return (uint32_t)range;
}

int ok_decoder_get_nb_samples(ok_decoder *decoder, const uint8_t *packet, int len) {
  if (!decoder || !decoder->state || !packet || len <= 0) return OK_INVALID;
  return opus_decoder_get_nb_samples(decoder->state, packet, len);
}

/* ------------------------------------------------------------------------- */
/* Packet inspection                                                          */
/* ------------------------------------------------------------------------- */

int ok_packet_get_bandwidth(const uint8_t *packet, int len) {
  if (!packet || len <= 0) return OK_INVALID;
  return opus_packet_get_bandwidth(packet);
}

int ok_packet_get_samples_per_frame(const uint8_t *packet, int len, int sample_rate) {
  if (!packet || len <= 0 || !ok_sample_rate_valid(sample_rate)) return OK_INVALID;
  return opus_packet_get_samples_per_frame(packet, sample_rate);
}

int ok_packet_get_nb_channels(const uint8_t *packet, int len) {
  if (!packet || len <= 0) return OK_INVALID;
  return opus_packet_get_nb_channels(packet);
}

int ok_packet_get_nb_frames(const uint8_t *packet, int len) {
  if (!packet || len <= 0) return OK_INVALID;
  return opus_packet_get_nb_frames(packet, len);
}

int ok_packet_get_nb_samples(const uint8_t *packet, int len, int sample_rate) {
  if (!packet || len <= 0 || !ok_sample_rate_valid(sample_rate)) return OK_INVALID;
  return opus_packet_get_nb_samples(packet, len, sample_rate);
}

int ok_packet_has_lbrr(const uint8_t *packet, int len) {
  if (!packet || len <= 0) return OK_INVALID;
  return opus_packet_has_lbrr(packet, len);
}

int ok_packet_parse(const uint8_t *packet, int len, int *sizes, int *offsets, int max_frames) {
  if (!packet || len <= 0 || !sizes || !offsets || max_frames <= 0) return OK_INVALID;

  unsigned char toc = 0;
  const unsigned char *frames[48];
  opus_int16 frame_sizes[48];
  int payload_offset = 0;
  int count = opus_packet_parse(packet, len, &toc, frames, frame_sizes, &payload_offset);
  if (count < 0) return count;
  if (count > max_frames) return OK_INVALID;

  for (int index = 0; index < count; index++) {
    ptrdiff_t offset = frames[index] - packet;
    if (offset < 0 || offset > len || frame_sizes[index] < 0 ||
        offset + frame_sizes[index] > len) {
      return OK_INVALID;
    }
    sizes[index] = frame_sizes[index];
    offsets[index] = (int)offset;
  }
  return count;
}

int ok_packet_pad(uint8_t *packet, int len, int new_len) {
  if (!packet || len <= 0 || new_len < len) return OK_INVALID;
  return opus_packet_pad(packet, len, new_len);
}

int ok_packet_unpad(uint8_t *packet, int len) {
  if (!packet || len <= 0) return OK_INVALID;
  return opus_packet_unpad(packet, len);
}

/* ------------------------------------------------------------------------- */
/* Repacketizer                                                               */
/* ------------------------------------------------------------------------- */

static void ok_repacketizer_release_packets(ok_repacketizer *repacketizer) {
  for (int index = 0; index < repacketizer->count; index++) {
    free(repacketizer->packets[index]);
  }
  repacketizer->count = 0;
}

ok_repacketizer *ok_repacketizer_create(void) {
  ok_repacketizer *repacketizer = (ok_repacketizer *)calloc(1, sizeof(ok_repacketizer));
  if (!repacketizer) return NULL;
  repacketizer->state = opus_repacketizer_create();
  if (!repacketizer->state) {
    free(repacketizer);
    return NULL;
  }
  return repacketizer;
}

void ok_repacketizer_free(ok_repacketizer *repacketizer) {
  if (!repacketizer) return;
  ok_repacketizer_release_packets(repacketizer);
  free(repacketizer->packets);
  if (repacketizer->state) opus_repacketizer_destroy(repacketizer->state);
  free(repacketizer);
}

int ok_repacketizer_reset(ok_repacketizer *repacketizer) {
  if (!repacketizer || !repacketizer->state) return OK_INVALID;
  ok_repacketizer_release_packets(repacketizer);
  opus_repacketizer_init(repacketizer->state);
  return OPUS_OK;
}

static int ok_repacketizer_store(ok_repacketizer *repacketizer, const uint8_t *packet, int len) {
  if (repacketizer->count == repacketizer->capacity) {
    int capacity = repacketizer->capacity ? repacketizer->capacity * 2 : 4;
    uint8_t **replacement =
        (uint8_t **)realloc(repacketizer->packets, (size_t)capacity * sizeof(uint8_t *));
    if (!replacement) return OK_INVALID;
    repacketizer->packets = replacement;
    repacketizer->capacity = capacity;
  }

  uint8_t *copy = (uint8_t *)malloc((size_t)len);
  if (!copy) return OK_INVALID;
  memcpy(copy, packet, (size_t)len);

  int result = opus_repacketizer_cat(repacketizer->state, copy, len);
  if (result != OPUS_OK) {
    free(copy);
    return result;
  }
  repacketizer->packets[repacketizer->count++] = copy;
  return OPUS_OK;
}

int ok_repacketizer_cat(ok_repacketizer *repacketizer, const uint8_t *packet, int len) {
  if (!repacketizer || !repacketizer->state || !packet || len <= 0) return OK_INVALID;
  return ok_repacketizer_store(repacketizer, packet, len);
}

int ok_repacketizer_get_nb_frames(ok_repacketizer *repacketizer) {
  if (!repacketizer || !repacketizer->state) return 0;
  return opus_repacketizer_get_nb_frames(repacketizer->state);
}

int ok_repacketizer_out_range(ok_repacketizer *repacketizer, int begin, int end, uint8_t *out,
                              int max_bytes) {
  if (!repacketizer || !repacketizer->state || !out || max_bytes <= 0 || begin < 0 ||
      end <= begin) {
    return OK_INVALID;
  }
  return opus_repacketizer_out_range(repacketizer->state, begin, end, out, max_bytes);
}

int ok_repacketizer_out(ok_repacketizer *repacketizer, uint8_t *out, int max_bytes) {
  if (!repacketizer || !repacketizer->state || !out || max_bytes <= 0) return OK_INVALID;
  return opus_repacketizer_out(repacketizer->state, out, max_bytes);
}

/* ------------------------------------------------------------------------- */
/* Shared helpers                                                             */
/* ------------------------------------------------------------------------- */

int ok_pcm_soft_clip(float *pcm, int frame_size, int channels, float *memory) {
  if (!pcm || frame_size <= 0 || !ok_channels_valid(channels)) return OK_INVALID;
  opus_pcm_soft_clip(pcm, frame_size, channels, memory);
  return OPUS_OK;
}
