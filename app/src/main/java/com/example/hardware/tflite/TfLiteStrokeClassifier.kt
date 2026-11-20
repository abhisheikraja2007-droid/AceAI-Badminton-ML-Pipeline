package com.example.hardware.tflite

import android.content.Context
import com.example.domain.model.StrokeType
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

@Singleton
class TfLiteStrokeClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    companion object {
        const val MODEL_PATH = "badminton_stroke_classifier_pure_fp16.tflite"
        const val WINDOW_FRAMES = 30
        const val NUM_FEATURES = 269
        const val NUM_CLASSES = 3
    }

    fun initialize() {
        val assetFileDescriptor = context.assets.openFd(MODEL_PATH)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelByteBuffer: ByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val options = Interpreter.Options()
        try {
            val gpuOptions = GpuDelegate.Options().apply {
                setPrecisionLossAllowed(true)
            }
            gpuDelegate = GpuDelegate(gpuOptions)
            options.addDelegate(gpuDelegate)
        } catch (e: Throwable) {
            options.setNumThreads(4)
        }

        interpreter = Interpreter(modelByteBuffer, options)
    }

    /**
     * Runs inference on a sliding window of shape [1, 30, 269].
     * Returns a pair of the predicted [StrokeType] and the confidence score.
     */
    fun classifyWindow(featureWindow: Array<FloatArray>): Pair<StrokeType, Float> {
        val tflite = interpreter ?: return Pair(StrokeType.UNKNOWN, 0f)

        val inputBuffer = ByteBuffer.allocateDirect(1 * WINDOW_FRAMES * NUM_FEATURES * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (frame in featureWindow) {
            for (feature in frame) {
                inputBuffer.putFloat(feature)
            }
        }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(1 * NUM_CLASSES * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        tflite.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val pSmash = outputBuffer.float
        val pOther = outputBuffer.float
        val pIdle = outputBuffer.float

        return when {
            pSmash >= pOther && pSmash >= pIdle -> Pair(StrokeType.OVERHEAD_SMASH, pSmash)
            pOther >= pSmash && pOther >= pIdle -> Pair(StrokeType.OTHER_STROKE, pOther)
            else -> Pair(StrokeType.IDLE_FOOTWORK, pIdle)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}

