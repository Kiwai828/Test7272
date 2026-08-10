package com.recapmaker.app.media.rvc

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.io.File

private const val TAG = "Rvc.Ort"

object OrtRuntime {
    val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun openSession(file: File): OrtSession {
        val t0 = System.currentTimeMillis()
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val session = env.createSession(file.absolutePath, opts)
        val elapsed = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "openSession: ${file.length() / (1024 * 1024)} MiB loaded from ${file.name} in ${elapsed}ms",
        )
        Log.d(TAG, "  inputs:  ${describe(session.inputInfo)}")
        Log.d(TAG, "  outputs: ${describe(session.outputInfo)}")
        return session
    }

    private fun describe(info: Map<String, NodeInfo>): String =
        info.entries.joinToString(prefix = "{", postfix = "}") { (name, ni) ->
            val v = ni.info
            if (v is TensorInfo) "$name:${v.type}${v.shape.contentToString()}"
            else "$name:$v"
        }
}
