package com.recapmaker.app.media.rvc

import ai.onnxruntime.OrtSession
import android.util.Log
import org.json.JSONObject

private const val TAG = "Rvc.Meta"

data class ModelMetadata(
    val samplingRate: Int,
    val f0: Boolean,
    val embChannels: Int,
    val embedder: String,
    val embOutputLayer: Int,
    val useFinalProj: Boolean,
    val modelType: String? = null,
    val version: String? = null,
) {
    companion object {
        // voice-changer's export2onnx.py embeds these as a JSON string under the
        // custom_metadata_props key "metadata". v3 exports a slim shape
        // ({application, version, samplingRate, f0, embedder}); older exports add
        // embChannels / embOutputLayer / useFinalProj. We fill the missing fields
        // from the embedder name so both formats drop in unchanged.
        fun fromSession(session: OrtSession): ModelMetadata? {
            val raw = session.metadata.customMetadata["metadata"]
            if (raw == null) {
                Log.w(TAG, "no 'metadata' key in custom_metadata_props")
                return null
            }
            return parse(raw).also { Log.i(TAG, "parsed: $it") }
        }

        fun parse(json: String): ModelMetadata {
            val o = JSONObject(json)
            val embedder = o.optString("embedder").ifBlank { "hubert_base_l12" }
            // v3 omits the embedder layout — infer it from the embedder name:
            // *_l12 → layer 12 raw; bare contentvec/hubert_base (v1) → layer 9 + final_proj
            val embOutputLayer = o.optInt("embOutputLayer", if (embedder.contains("l9")) 9 else 12)
            val useFinalProj = if (o.has("useFinalProj")) o.getBoolean("useFinalProj")
            else !embedder.contains("l12") // bare v1 embedders use the final projection
            val embChannels = o.optInt("embChannels", if (embOutputLayer == 9) 256 else 768)
            return ModelMetadata(
                samplingRate = o.getInt("samplingRate"),
                f0 = o.optBoolean("f0", true),
                embChannels = embChannels,
                embedder = embedder,
                embOutputLayer = embOutputLayer,
                useFinalProj = useFinalProj,
                modelType = o.optString("modelType").ifEmpty { null },
                version = o.optString("version").ifEmpty { null },
            )
        }
    }
}
