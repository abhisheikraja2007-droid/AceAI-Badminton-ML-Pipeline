package com.example.domain.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device neural classifier that performs inference over 30-frame kinematic sliding windows
 * using the optimized pure FP16 TFLite model.
 */
@Singleton
class TFLiteStrokeClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TFLiteStrokeClassifier"
        const val DEFAULT_MODEL_PATH = "badminton_stroke_classifier_pure_fp16.tflite"
        const val WINDOW_FRAMES = 30
        const val NUM_FEATURES = 269
        const val NUM_CLASSES = 3

        // Output Class Mapping: Index 0: overhead_smash, 1: other_stroke, 2: idle_footwork
        const val CLASS_OVERHEAD_SMASH = "overhead_smash"
        const val CLASS_OTHER_STROKE = "other_stroke"
        const val CLASS_IDLE_FOOTWORK = "idle_footwork"
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isInitialized = false

    data class StrokePrediction(
        val strokeClass: String,
        val smashProbability: Float,
        val confidence: Float,
        val probabilities: FloatArray
    )

    fun initialize(modelPath: String = DEFAULT_MODEL_PATH, preferGpu: Boolean = true) {
        if (isInitialized) return

        try {
            val modelBuffer = loadModelFile(modelPath)
            var createdInterpreter: Interpreter? = null

            if (preferGpu) {
                try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val gpuOptions = GpuDelegate.Options().apply {
                            setPrecisionLossAllowed(true)
                        }
                        val delegate = GpuDelegate(gpuOptions)
                        val gpuInterpreterOptions = Interpreter.Options().apply {
                            addDelegate(delegate)
                        }
                        createdInterpreter = Interpreter(modelBuffer, gpuInterpreterOptions)
                        gpuDelegate = delegate
                        Log.i(TAG, "TFLite GPU Delegate successfully initialized on compatible device.")
                    } else {
                        Log.i(TAG, "Device GPU not in TFLite compatibility whitelist; using CPU XNNPACK.")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "GPU Delegate setup/binding failed (${t.message}). Falling back to CPU.", t)
                    gpuDelegate?.close()
                    gpuDelegate = null
                }
            }

            if (createdInterpreter == null) {
                val cpuOptions = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseXNNPACK(true)
                }
                createdInterpreter = Interpreter(modelBuffer, cpuOptions)
                Log.i(TAG, "TFLite Interpreter initialized with 4-thread CPU XNNPACK.")
            }

            interpreter = createdInterpreter
            isInitialized = true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}", e)
        }
    }

    /**
     * Runs inference on a sliding sequence of shape [30, 269].
     * Returns a StrokePrediction containing the winning class and the overhead_smash probability.
     */
    fun classify(sequenceWindow: Array<FloatArray>): StrokePrediction {
        val tflite = interpreter
        if (tflite == null) {
            Log.w(TAG, "classify invoked before initialization; returning default IDLE.")
            return StrokePrediction(CLASS_IDLE_FOOTWORK, 0f, 0f, floatArrayOf(0f, 0f, 1f))
        }

        val inputBuffer = ByteBuffer.allocateDirect(1 * WINDOW_FRAMES * NUM_FEATURES * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        // Fill buffer with 30 frames x 269 features
        for (f in 0 until WINDOW_FRAMES) {
            val frame = if (f < sequenceWindow.size) sequenceWindow[f] else FloatArray(NUM_FEATURES)
            for (i in 0 until NUM_FEATURES) {
                val value = if (i < frame.size) frame[i] else 0f
                inputBuffer.putFloat(value)
            }
        }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(1 * NUM_CLASSES * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        synchronized(this) {
            tflite.run(inputBuffer, outputBuffer)
        }
        outputBuffer.rewind()

        val pSmash = outputBuffer.float
        val pOther = outputBuffer.float
        val pIdle = outputBuffer.float
        val probs = floatArrayOf(pSmash, pOther, pIdle)

        val winningClass = when {
            pSmash >= pOther && pSmash >= pIdle -> CLASS_OVERHEAD_SMASH
            pOther >= pSmash && pOther >= pIdle -> CLASS_OTHER_STROKE
            else -> CLASS_IDLE_FOOTWORK
        }
        val confidence = maxOf(pSmash, maxOf(pOther, pIdle))

        return StrokePrediction(
            strokeClass = winningClass,
            smashProbability = pSmash,
            confidence = confidence,
            probabilities = probs
        )
    }

    private fun loadModelFile(modelPath: String): ByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.w(TAG, "openFd failed for $modelPath ($e), loading via InputStream bytes directly.")
            val bytes = context.assets.open(modelPath).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            buffer
        }
    }

    fun isReady(): Boolean = isInitialized

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        isInitialized = false
    }
}

