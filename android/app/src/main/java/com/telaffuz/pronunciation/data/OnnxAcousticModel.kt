package com.telaffuz.pronunciation.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.telaffuz.pronunciation.domain.AcousticModel
import java.io.File
import java.nio.FloatBuffer

/** wav2vec2 fonem modelini ONNX Runtime ile cihaz üzerinde çalıştırır. Oturum ilk çağrıda açılır. */
class OnnxAcousticModel(private val context: Context) : AcousticModel {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val session: OrtSession by lazy {
        env.createSession(ensureModelFile().absolutePath, OrtSession.SessionOptions())
    }

    @Synchronized
    override fun logits(samples: FloatArray): Array<FloatArray> {
        val inputShape = longArrayOf(1, samples.size.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), inputShape).use { tensor ->
            session.run(mapOf("input_values" to tensor)).use { output ->
                val logitsTensor = output.get("logits").get() as OnnxTensor
                @Suppress("UNCHECKED_CAST")
                val raw = logitsTensor.value as Array<Array<FloatArray>> // [1][T][V]
                return raw[0]
            }
        }
    }

    /**
     * 120MB'lık modeli tek seferde ByteArray'e okumak Android'in Java heap limitini
     * aşıp OutOfMemoryError atıyor. Bunun yerine dosyayı (küçük bir tampon ile,
     * belleğe tek parça yüklemeden) internal storage'a bir kez kopyalayıp ONNX
     * Runtime'ın dosya yolundan (memory-map ile) yüklemesini sağlıyoruz.
     */
    private fun ensureModelFile(): File {
        val outFile = File(context.filesDir, MODEL_ASSET)
        val assetSize = context.assets.open(MODEL_ASSET).use { it.available().toLong() }
        if (outFile.exists() && outFile.length() == assetSize) {
            return outFile
        }
        context.assets.open(MODEL_ASSET).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1 shl 20)
            }
        }
        return outFile
    }

    private companion object {
        const val MODEL_ASSET = "phoneme_model.onnx"
    }
}
