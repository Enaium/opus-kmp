/*
 * JNI bridge between cn.enaium.opus.JniOpus and the C shim.
 *
 * The declarations on the Kotlin side live in
 * opus-kmp/src/jvmMain/kotlin/cn/enaium/opus/OpusNative.jvm.kt.
 *
 * Handles are the raw ok_* pointers stored in a jlong. Byte, short and float
 * arrays are pinned only for the duration of a shim call; the Kotlin side owns
 * every buffer and passes its capacity, so no native allocation is involved.
 */
#include <jni.h>
#include <stdint.h>

#include "opus_kmp.h"

static ok_encoder *ok_encoder_from(jlong handle) { return (ok_encoder *)(intptr_t)handle; }

static ok_decoder *ok_decoder_from(jlong handle) { return (ok_decoder *)(intptr_t)handle; }

static ok_repacketizer *ok_repacketizer_from(jlong handle) {
  return (ok_repacketizer *)(intptr_t)handle;
}

static jstring ok_new_string(JNIEnv *env, const char *text) {
  return (*env)->NewStringUTF(env, text ? text : "");
}

static int ok_byte_array_length(JNIEnv *env, jbyteArray array) {
  return array ? (int)(*env)->GetArrayLength(env, array) : 0;
}

/* ------------------------------------------------------------------------- */
/* Library version                                                            */
/* ------------------------------------------------------------------------- */

JNIEXPORT jstring JNICALL Java_cn_enaium_opus_JniOpus_nativeVersionString(JNIEnv *env, jobject self) {
  (void)self;
  return ok_new_string(env, ok_version_string());
}

JNIEXPORT jstring JNICALL Java_cn_enaium_opus_JniOpus_nativeStrerror(JNIEnv *env, jobject self,
                                                                     jint error) {
  (void)self;
  return ok_new_string(env, ok_strerror((int)error));
}

/* ------------------------------------------------------------------------- */
/* Encoder                                                                    */
/* ------------------------------------------------------------------------- */

JNIEXPORT jlong JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderCreate(JNIEnv *env, jobject self,
                                                                        jint sample_rate,
                                                                        jint channels,
                                                                        jint application) {
  (void)env;
  (void)self;
  return (jlong)(intptr_t)ok_encoder_create((int)sample_rate, (int)channels, (int)application);
}

JNIEXPORT void JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderFree(JNIEnv *env, jobject self,
                                                                     jlong handle) {
  (void)env;
  (void)self;
  ok_encoder_free(ok_encoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderReset(JNIEnv *env, jobject self,
                                                                      jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_encoder_reset(ok_encoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderSampleRate(JNIEnv *env,
                                                                           jobject self,
                                                                           jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_encoder_sample_rate(ok_encoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderChannels(JNIEnv *env, jobject self,
                                                                         jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_encoder_channels(ok_encoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderEncode(
    JNIEnv *env, jobject self, jlong handle, jshortArray samples, jint offset, jint frame_size,
    jbyteArray out) {
  (void)self;
  ok_encoder *encoder = ok_encoder_from(handle);
  if (!encoder || !samples || !out) return OK_INVALID;

  int channels = ok_encoder_channels(encoder);
  jsize sample_length = (*env)->GetArrayLength(env, samples);
  if (offset < 0 || frame_size <= 0 ||
      (jlong)offset + (jlong)frame_size * channels > sample_length) {
    return OK_INVALID;
  }

  jshort *sample_elements = (*env)->GetShortArrayElements(env, samples, NULL);
  jbyte *out_elements = (*env)->GetByteArrayElements(env, out, NULL);
  if (!sample_elements || !out_elements) {
    if (sample_elements) (*env)->ReleaseShortArrayElements(env, samples, sample_elements, JNI_ABORT);
    if (out_elements) (*env)->ReleaseByteArrayElements(env, out, out_elements, JNI_ABORT);
    return OK_INVALID;
  }

  int result = ok_encoder_encode(encoder, sample_elements + offset, (int)frame_size,
                                 (uint8_t *)out_elements,
                                 (int)(*env)->GetArrayLength(env, out));
  (*env)->ReleaseShortArrayElements(env, samples, sample_elements, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, out, out_elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderEncodeFloat(
    JNIEnv *env, jobject self, jlong handle, jfloatArray samples, jint offset, jint frame_size,
    jbyteArray out) {
  (void)self;
  ok_encoder *encoder = ok_encoder_from(handle);
  if (!encoder || !samples || !out) return OK_INVALID;

  int channels = ok_encoder_channels(encoder);
  jsize sample_length = (*env)->GetArrayLength(env, samples);
  if (offset < 0 || frame_size <= 0 ||
      (jlong)offset + (jlong)frame_size * channels > sample_length) {
    return OK_INVALID;
  }

  jfloat *sample_elements = (*env)->GetFloatArrayElements(env, samples, NULL);
  jbyte *out_elements = (*env)->GetByteArrayElements(env, out, NULL);
  if (!sample_elements || !out_elements) {
    if (sample_elements) (*env)->ReleaseFloatArrayElements(env, samples, sample_elements, JNI_ABORT);
    if (out_elements) (*env)->ReleaseByteArrayElements(env, out, out_elements, JNI_ABORT);
    return OK_INVALID;
  }

  int result = ok_encoder_encode_float(encoder, sample_elements + offset, (int)frame_size,
                                       (uint8_t *)out_elements,
                                       (int)(*env)->GetArrayLength(env, out));
  (*env)->ReleaseFloatArrayElements(env, samples, sample_elements, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, out, out_elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderCtlInt(JNIEnv *env, jobject self,
                                                                       jlong handle, jint request,
                                                                       jint value) {
  (void)env;
  (void)self;
  return (jint)ok_encoder_ctl_int(ok_encoder_from(handle), (int)request, (int)value);
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderGetInt(JNIEnv *env, jobject self,
                                                                       jlong handle, jint request) {
  (void)env;
  (void)self;
  return (jint)ok_encoder_get_int(ok_encoder_from(handle), (int)request);
}

JNIEXPORT jlong JNICALL Java_cn_enaium_opus_JniOpus_nativeEncoderFinalRange(JNIEnv *env,
                                                                            jobject self,
                                                                            jlong handle) {
  (void)env;
  (void)self;
  return (jlong)(uint32_t)ok_encoder_final_range(ok_encoder_from(handle));
}

/* ------------------------------------------------------------------------- */
/* Decoder                                                                    */
/* ------------------------------------------------------------------------- */

JNIEXPORT jlong JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderCreate(JNIEnv *env, jobject self,
                                                                        jint sample_rate,
                                                                        jint channels) {
  (void)env;
  (void)self;
  return (jlong)(intptr_t)ok_decoder_create((int)sample_rate, (int)channels);
}

JNIEXPORT void JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderFree(JNIEnv *env, jobject self,
                                                                     jlong handle) {
  (void)env;
  (void)self;
  ok_decoder_free(ok_decoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderReset(JNIEnv *env, jobject self,
                                                                      jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_decoder_reset(ok_decoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderSampleRate(JNIEnv *env,
                                                                           jobject self,
                                                                           jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_decoder_sample_rate(ok_decoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderChannels(JNIEnv *env, jobject self,
                                                                         jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_decoder_channels(ok_decoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderDecode(
    JNIEnv *env, jobject self, jlong handle, jbyteArray packet, jint frame_size, jboolean fec,
    jshortArray out) {
  (void)self;
  ok_decoder *decoder = ok_decoder_from(handle);
  if (!decoder || !out) return OK_INVALID;

  int channels = ok_decoder_channels(decoder);
  jsize capacity = (*env)->GetArrayLength(env, out);
  if (frame_size <= 0 || (jlong)frame_size * channels > capacity) return OK_INVALID;

  jbyte *packet_elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int packet_length = ok_byte_array_length(env, packet);
  if (packet && !packet_elements) return OK_INVALID;

  jshort *out_elements = (*env)->GetShortArrayElements(env, out, NULL);
  if (!out_elements) {
    if (packet_elements) (*env)->ReleaseByteArrayElements(env, packet, packet_elements, JNI_ABORT);
    return OK_INVALID;
  }

  int result = ok_decoder_decode(decoder, (const uint8_t *)packet_elements, packet_length,
                                 (int)frame_size, out_elements, (int)capacity, (int)fec);
  if (packet_elements) (*env)->ReleaseByteArrayElements(env, packet, packet_elements, JNI_ABORT);
  (*env)->ReleaseShortArrayElements(env, out, out_elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderDecodeFloat(
    JNIEnv *env, jobject self, jlong handle, jbyteArray packet, jint frame_size, jboolean fec,
    jfloatArray out) {
  (void)self;
  ok_decoder *decoder = ok_decoder_from(handle);
  if (!decoder || !out) return OK_INVALID;

  int channels = ok_decoder_channels(decoder);
  jsize capacity = (*env)->GetArrayLength(env, out);
  if (frame_size <= 0 || (jlong)frame_size * channels > capacity) return OK_INVALID;

  jbyte *packet_elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int packet_length = ok_byte_array_length(env, packet);
  if (packet && !packet_elements) return OK_INVALID;

  jfloat *out_elements = (*env)->GetFloatArrayElements(env, out, NULL);
  if (!out_elements) {
    if (packet_elements) (*env)->ReleaseByteArrayElements(env, packet, packet_elements, JNI_ABORT);
    return OK_INVALID;
  }

  int result = ok_decoder_decode_float(decoder, (const uint8_t *)packet_elements, packet_length,
                                       (int)frame_size, out_elements, (int)capacity, (int)fec);
  if (packet_elements) (*env)->ReleaseByteArrayElements(env, packet, packet_elements, JNI_ABORT);
  (*env)->ReleaseFloatArrayElements(env, out, out_elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderCtlInt(JNIEnv *env, jobject self,
                                                                       jlong handle, jint request,
                                                                       jint value) {
  (void)env;
  (void)self;
  return (jint)ok_decoder_ctl_int(ok_decoder_from(handle), (int)request, (int)value);
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderGetInt(JNIEnv *env, jobject self,
                                                                       jlong handle, jint request) {
  (void)env;
  (void)self;
  return (jint)ok_decoder_get_int(ok_decoder_from(handle), (int)request);
}

JNIEXPORT jlong JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderFinalRange(JNIEnv *env,
                                                                            jobject self,
                                                                            jlong handle) {
  (void)env;
  (void)self;
  return (jlong)(uint32_t)ok_decoder_final_range(ok_decoder_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeDecoderGetNbSamples(JNIEnv *env,
                                                                             jobject self,
                                                                             jlong handle,
                                                                             jbyteArray packet) {
  (void)self;
  if (!packet) return OK_INVALID;
  jbyte *elements = (*env)->GetByteArrayElements(env, packet, NULL);
  if (!elements) return OK_INVALID;
  int result = ok_decoder_get_nb_samples(ok_decoder_from(handle), (const uint8_t *)elements,
                                         (int)(*env)->GetArrayLength(env, packet));
  (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

/* ------------------------------------------------------------------------- */
/* Packet inspection                                                          */
/* ------------------------------------------------------------------------- */

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketBandwidth(JNIEnv *env, jobject self,
                                                                         jbyteArray packet) {
  (void)self;
  jbyte *elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int result = ok_packet_get_bandwidth((const uint8_t *)elements, ok_byte_array_length(env, packet));
  if (elements) (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketSamplesPerFrame(JNIEnv *env,
                                                                               jobject self,
                                                                               jbyteArray packet,
                                                                               jint sample_rate) {
  (void)self;
  jbyte *elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int result = ok_packet_get_samples_per_frame((const uint8_t *)elements,
                                               ok_byte_array_length(env, packet), (int)sample_rate);
  if (elements) (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketChannels(JNIEnv *env, jobject self,
                                                                        jbyteArray packet) {
  (void)self;
  jbyte *elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int result = ok_packet_get_nb_channels((const uint8_t *)elements, ok_byte_array_length(env, packet));
  if (elements) (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketFrames(JNIEnv *env, jobject self,
                                                                      jbyteArray packet) {
  (void)self;
  jbyte *elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int result = ok_packet_get_nb_frames((const uint8_t *)elements, ok_byte_array_length(env, packet));
  if (elements) (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketSamples(JNIEnv *env, jobject self,
                                                                       jbyteArray packet,
                                                                       jint sample_rate) {
  (void)self;
  jbyte *elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int result = ok_packet_get_nb_samples((const uint8_t *)elements, ok_byte_array_length(env, packet),
                                        (int)sample_rate);
  if (elements) (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketHasLbrr(JNIEnv *env, jobject self,
                                                                       jbyteArray packet) {
  (void)self;
  jbyte *elements = packet ? (*env)->GetByteArrayElements(env, packet, NULL) : NULL;
  int result = ok_packet_has_lbrr((const uint8_t *)elements, ok_byte_array_length(env, packet));
  if (elements) (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketParse(JNIEnv *env, jobject self,
                                                                     jbyteArray packet,
                                                                     jintArray sizes,
                                                                     jintArray offsets) {
  (void)self;
  if (!packet || !sizes || !offsets) return OK_INVALID;
  jsize capacity = (*env)->GetArrayLength(env, sizes);
  if ((*env)->GetArrayLength(env, offsets) < capacity) capacity = (*env)->GetArrayLength(env, offsets);

  jbyte *packet_elements = (*env)->GetByteArrayElements(env, packet, NULL);
  jint *size_elements = (*env)->GetIntArrayElements(env, sizes, NULL);
  jint *offset_elements = (*env)->GetIntArrayElements(env, offsets, NULL);
  if (!packet_elements || !size_elements || !offset_elements) {
    if (packet_elements) (*env)->ReleaseByteArrayElements(env, packet, packet_elements, JNI_ABORT);
    if (size_elements) (*env)->ReleaseIntArrayElements(env, sizes, size_elements, JNI_ABORT);
    if (offset_elements) (*env)->ReleaseIntArrayElements(env, offsets, offset_elements, JNI_ABORT);
    return OK_INVALID;
  }

  int result = ok_packet_parse((const uint8_t *)packet_elements,
                               (int)(*env)->GetArrayLength(env, packet), size_elements,
                               offset_elements, (int)capacity);
  (*env)->ReleaseByteArrayElements(env, packet, packet_elements, JNI_ABORT);
  (*env)->ReleaseIntArrayElements(env, sizes, size_elements, 0);
  (*env)->ReleaseIntArrayElements(env, offsets, offset_elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketPad(JNIEnv *env, jobject self,
                                                                   jbyteArray packet, jint length,
                                                                   jint new_length) {
  (void)self;
  if (!packet) return OK_INVALID;
  jbyte *elements = (*env)->GetByteArrayElements(env, packet, NULL);
  if (!elements) return OK_INVALID;
  int result = ok_packet_pad((uint8_t *)elements, (int)length, (int)new_length);
  (*env)->ReleaseByteArrayElements(env, packet, elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePacketUnpad(JNIEnv *env, jobject self,
                                                                     jbyteArray packet) {
  (void)self;
  if (!packet) return OK_INVALID;
  jbyte *elements = (*env)->GetByteArrayElements(env, packet, NULL);
  if (!elements) return OK_INVALID;
  int result = ok_packet_unpad((uint8_t *)elements, (int)(*env)->GetArrayLength(env, packet));
  (*env)->ReleaseByteArrayElements(env, packet, elements, 0);
  return (jint)result;
}

/* ------------------------------------------------------------------------- */
/* Repacketizer                                                               */
/* ------------------------------------------------------------------------- */

JNIEXPORT jlong JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerCreate(JNIEnv *env,
                                                                              jobject self) {
  (void)env;
  (void)self;
  return (jlong)(intptr_t)ok_repacketizer_create();
}

JNIEXPORT void JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerFree(JNIEnv *env, jobject self,
                                                                          jlong handle) {
  (void)env;
  (void)self;
  ok_repacketizer_free(ok_repacketizer_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerReset(JNIEnv *env,
                                                                            jobject self,
                                                                            jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_repacketizer_reset(ok_repacketizer_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerCat(JNIEnv *env, jobject self,
                                                                         jlong handle,
                                                                         jbyteArray packet) {
  (void)self;
  if (!packet) return OK_INVALID;
  jbyte *elements = (*env)->GetByteArrayElements(env, packet, NULL);
  if (!elements) return OK_INVALID;
  int result = ok_repacketizer_cat(ok_repacketizer_from(handle), (const uint8_t *)elements,
                                    (int)(*env)->GetArrayLength(env, packet));
  (*env)->ReleaseByteArrayElements(env, packet, elements, JNI_ABORT);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerFrameCount(JNIEnv *env,
                                                                                 jobject self,
                                                                                 jlong handle) {
  (void)env;
  (void)self;
  return (jint)ok_repacketizer_get_nb_frames(ok_repacketizer_from(handle));
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerOutRange(
    JNIEnv *env, jobject self, jlong handle, jint begin, jint end, jbyteArray out) {
  (void)self;
  if (!out) return OK_INVALID;
  jbyte *elements = (*env)->GetByteArrayElements(env, out, NULL);
  if (!elements) return OK_INVALID;
  int result = ok_repacketizer_out_range(ok_repacketizer_from(handle), (int)begin, (int)end,
                                          (uint8_t *)elements,
                                          (int)(*env)->GetArrayLength(env, out));
  (*env)->ReleaseByteArrayElements(env, out, elements, 0);
  return (jint)result;
}

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativeRepacketizerOut(JNIEnv *env, jobject self,
                                                                          jlong handle,
                                                                          jbyteArray out) {
  (void)self;
  if (!out) return OK_INVALID;
  jbyte *elements = (*env)->GetByteArrayElements(env, out, NULL);
  if (!elements) return OK_INVALID;
  int result = ok_repacketizer_out(ok_repacketizer_from(handle), (uint8_t *)elements,
                                    (int)(*env)->GetArrayLength(env, out));
  (*env)->ReleaseByteArrayElements(env, out, elements, 0);
  return (jint)result;
}

/* ------------------------------------------------------------------------- */
/* Shared helpers                                                             */
/* ------------------------------------------------------------------------- */

JNIEXPORT jint JNICALL Java_cn_enaium_opus_JniOpus_nativePcmSoftClip(
    JNIEnv *env, jobject self, jfloatArray pcm, jint frame_size, jint channels,
    jfloatArray memory) {
  (void)self;
  if (!pcm) return OK_INVALID;
  jsize pcm_length = (*env)->GetArrayLength(env, pcm);
  if (frame_size <= 0 || channels <= 0 || (jlong)frame_size * channels > pcm_length) {
    return OK_INVALID;
  }
  jsize memory_length = memory ? (*env)->GetArrayLength(env, memory) : 0;
  if (memory && memory_length < channels) return OK_INVALID;

  jfloat *pcm_elements = (*env)->GetFloatArrayElements(env, pcm, NULL);
  jfloat *memory_elements = memory ? (*env)->GetFloatArrayElements(env, memory, NULL) : NULL;
  if (!pcm_elements || (memory && !memory_elements)) {
    if (pcm_elements) (*env)->ReleaseFloatArrayElements(env, pcm, pcm_elements, JNI_ABORT);
    if (memory_elements) (*env)->ReleaseFloatArrayElements(env, memory, memory_elements, JNI_ABORT);
    return OK_INVALID;
  }

  int result = ok_pcm_soft_clip(pcm_elements, (int)frame_size, (int)channels, memory_elements);
  (*env)->ReleaseFloatArrayElements(env, pcm, pcm_elements, 0);
  if (memory_elements) (*env)->ReleaseFloatArrayElements(env, memory, memory_elements, 0);
  return (jint)result;
}
