package com.recapmaker.app.media.rvc

/**
 * Default voice-clone model bundle (one-tap download).
 *
 * All three files are verified voice-changer v3-compatible exports from the
 * `WIAWAN/VOICE-MODELS` HuggingFace repo (apache-2.0):
 *  - Nova_HD.onnx — synth generator with embedded metadata
 *    ({"application":"VC_CLIENT","version":"3.0","samplingRate":48000,
 *      "f0":1,"embedder":"hubert_base_l12"}), inputs feats/p_len/pitch/pitchf → audio
 *  - base_layer12_32000.onnx — the matching hubert_base layer-12 embedder
 *  - rmvpe.onnx — RMVPE pitch extractor (needed because f0 == 1)
 *
 * Total ≈ 813 MB, one-time download. Files are stored in filesDir/rvc/ so they
 * survive cache clears; ORT mmaps them straight from disk.
 */
object DefaultRvcModels {
    const val BASE_URL = "https://huggingface.co/WIAWAN/VOICE-MODELS/resolve/main"

    data class Model(val file: String, val url: String, val sizeMb: Long)

    val synth = Model("synth.onnx", "$BASE_URL/Nova_HD.onnx", 109)
    val hubert = Model("hubert.onnx", "$BASE_URL/base_layer12_32000.onnx", 360)
    val rmvpe = Model("rmvpe.onnx", "$BASE_URL/rmvpe.onnx", 344)

    val voiceName = "Nova (default voice)"
    val totalMb: Long = synth.sizeMb + hubert.sizeMb + rmvpe.sizeMb
    val all: List<Model> = listOf(synth, hubert, rmvpe)
}
