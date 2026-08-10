package com.recapmaker.app.media.rvc

/**
 * Default voice-clone model bundle (one-tap download).
 *
 * All three files were verified I/O-by-I/O against this app's RVC engine:
 *  - Nova_HD.onnx — synth generator from `WIAWAN/VOICE-MODELS` (apache-2.0),
 *    embedded metadata {"version":"3.0","samplingRate":48000,"f0":1,
 *    "embedder":"hubert_base_l12"}, inputs feats/p_len/pitch/pitchf/sid → audio
 *  - hubert_base_layer12_nomask_32000.onnx — the matching hubert_base layer-12
 *    embedder from `ohnoitsaninja/rvc-base-onnx` (MIT); fixed 32000-sample
 *    window (2 s @ 16 kHz) → 99×768 features per window; processed chunked
 *    in-app (HubertEmbedder) so any clip length works
 *  - rmvpe_20231006.onnx — RMVPE pitch extractor from `wok000/weights_gpl`
 *    (GPL, mirror of the voice-changer reference model), inputs
 *    waveform[1,N]+threshold[1] → pitchf — the exact format this app's
 *    extractor is built around (needed because f0 == 1)
 *
 * Total ≈ 814 MB, one-time download. Files are stored in filesDir/rvc/ so they
 * survive cache clears; ORT mmaps them straight from disk.
 */
object DefaultRvcModels {
    const val BASE_URL = "https://huggingface.co/WIAWAN/VOICE-MODELS/resolve/main"
    const val BASE_URL_OHN = "https://huggingface.co/ohnoitsaninja/rvc-base-onnx/resolve/main"
    const val BASE_URL_RMVPE = "https://huggingface.co/wok000/weights_gpl/resolve/main"

    data class Model(val file: String, val url: String, val sizeMb: Long)

    val synth = Model("synth.onnx", "$BASE_URL/Nova_HD.onnx", 109)
    val hubert = Model(
        "hubert.onnx",
        "$BASE_URL_OHN/onnx/hubert_base_layer12_nomask_32000.onnx",
        360,
    )
    val rmvpe = Model("rmvpe.onnx", "$BASE_URL_RMVPE/rmvpe/rmvpe_20231006.onnx", 345)

    val voiceName = "Nova (default voice)"
    val totalMb: Long = synth.sizeMb + hubert.sizeMb + rmvpe.sizeMb
    val all: List<Model> = listOf(synth, hubert, rmvpe)
}
