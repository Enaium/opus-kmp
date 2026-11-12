/*
 * WebAssembly entry point.
 *
 * The exported functions themselves live in opus_kmp.c. This translation unit
 * takes their addresses so the linker definitely pulls that object out of the
 * static archive; without a reference the archive member would be dropped and
 * the exports listed in CMakeLists.txt would be missing.
 */
#include "opus_kmp.h"

typedef void (*ok_wasm_export)(void);

static const ok_wasm_export ok_wasm_exports[] = {
    (ok_wasm_export)ok_version_string,
    (ok_wasm_export)ok_strerror,
    (ok_wasm_export)ok_encoder_create,
    (ok_wasm_export)ok_encoder_free,
    (ok_wasm_export)ok_encoder_reset,
    (ok_wasm_export)ok_encoder_sample_rate,
    (ok_wasm_export)ok_encoder_channels,
    (ok_wasm_export)ok_encoder_encode,
    (ok_wasm_export)ok_encoder_encode_float,
    (ok_wasm_export)ok_encoder_ctl_int,
    (ok_wasm_export)ok_encoder_get_int,
    (ok_wasm_export)ok_encoder_final_range,
    (ok_wasm_export)ok_decoder_create,
    (ok_wasm_export)ok_decoder_free,
    (ok_wasm_export)ok_decoder_reset,
    (ok_wasm_export)ok_decoder_sample_rate,
    (ok_wasm_export)ok_decoder_channels,
    (ok_wasm_export)ok_decoder_decode,
    (ok_wasm_export)ok_decoder_decode_float,
    (ok_wasm_export)ok_decoder_ctl_int,
    (ok_wasm_export)ok_decoder_get_int,
    (ok_wasm_export)ok_decoder_final_range,
    (ok_wasm_export)ok_decoder_get_nb_samples,
    (ok_wasm_export)ok_packet_get_bandwidth,
    (ok_wasm_export)ok_packet_get_samples_per_frame,
    (ok_wasm_export)ok_packet_get_nb_channels,
    (ok_wasm_export)ok_packet_get_nb_frames,
    (ok_wasm_export)ok_packet_get_nb_samples,
    (ok_wasm_export)ok_packet_has_lbrr,
    (ok_wasm_export)ok_packet_parse,
    (ok_wasm_export)ok_packet_pad,
    (ok_wasm_export)ok_packet_unpad,
    (ok_wasm_export)ok_repacketizer_create,
    (ok_wasm_export)ok_repacketizer_free,
    (ok_wasm_export)ok_repacketizer_reset,
    (ok_wasm_export)ok_repacketizer_cat,
    (ok_wasm_export)ok_repacketizer_get_nb_frames,
    (ok_wasm_export)ok_repacketizer_out_range,
    (ok_wasm_export)ok_repacketizer_out,
    (ok_wasm_export)ok_pcm_soft_clip,
    (ok_wasm_export)ok_alloc,
    (ok_wasm_export)ok_free,
};

/* Keep the table observable so no optimiser removes it. */
const ok_wasm_export *ok_wasm_keep_exports(void);

const ok_wasm_export *ok_wasm_keep_exports(void) { return ok_wasm_exports; }
