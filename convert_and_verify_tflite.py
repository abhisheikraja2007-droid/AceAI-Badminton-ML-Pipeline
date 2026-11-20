"""
TensorFlow Lite Conversion & Quantization Verification Harness
=============================================================
Converts the trained Keras Hybrid 1D-CNN-BiLSTM badminton stroke classifier
into an optimized Float16 quantized TensorFlow Lite (.tflite) model.

Key Capabilities:
1. Float16 Weight Quantization: Reduces model size by ~50% with near-zero precision loss.
2. Pure TFLite Builtin Execution: Unrolls BiLSTM recurrence across the fixed 30-frame
   temporal window, eliminating Select TF Ops (Flex) for native Android NNAPI and GPU
   delegate acceleration.
3. Automated Verification Harness:
   - Evaluates 100 test sequences through both Keras and TFLite models.
   - Asserts < 0.5% accuracy deviation between Keras and TFLite.
   - Calculates max absolute probability divergence.
   - Measures single-window inference latency (Mean, P95, P99, Throughput in FPS).
"""

import argparse
import logging
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models

# Register custom Attention layer and Focal Loss
import badminton_hybrid_model
from badminton_hybrid_model import TemporalAttention
import train_badminton_gpu
from train_badminton_gpu import CategoricalFocalLoss

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("TFLiteConverterHarness")

CLASS_NAMES = ["overhead_smash", "other_stroke", "idle_footwork"]


def build_unrolled_tflite_model(keras_model: tf.keras.Model) -> tf.keras.Model:
    """
    Rebuilds the Hybrid architecture with unrolled BiLSTM layers (unroll=True).
    This compiles the 30-frame temporal loop into pure built-in matrix operations,
    enabling native NNAPI/GPU acceleration without FlexTensorList dependencies.
    """
    input_shape = (30, keras_model.input_shape[-1])
    inputs = layers.Input(shape=input_shape, name="kinematic_sequence_input")

    x = layers.Conv1D(64, 3, padding="same", activation="swish", name="conv1d_stage1")(inputs)
    x = layers.BatchNormalization(name="bn_conv1")(x)
    x = layers.SpatialDropout1D(0.2, name="spatial_dropout_conv1")(x)

    x = layers.Conv1D(128, 3, padding="same", activation="swish", name="conv1d_stage2")(x)
    x = layers.BatchNormalization(name="bn_conv2")(x)
    x = layers.SpatialDropout1D(0.2, name="spatial_dropout_conv2")(x)

    x = layers.Bidirectional(
        layers.LSTM(64, return_sequences=True, unroll=True, name="forward_backward_lstm1"),
        name="bidirectional_lstm1"
    )(x)

    x = layers.Bidirectional(
        layers.LSTM(64, return_sequences=True, unroll=True, name="forward_backward_lstm2"),
        name="bidirectional_lstm2"
    )(x)

    context_vector = TemporalAttention(units=64, name="temporal_attention_mechanism")(x)
    dense = layers.Dense(64, activation="swish", name="dense_dense64")(context_vector)
    dense = layers.BatchNormalization(name="bn_dense")(dense)
    dense = layers.Dropout(0.3, name="dense_dropout")(dense)
    outputs = layers.Dense(3, activation="softmax", dtype="float32", name="stroke_class_probabilities")(dense)

    unrolled = models.Model(inputs=inputs, outputs=outputs, name="AceAI_Unrolled_Net")
    unrolled.set_weights(keras_model.get_weights())
    return unrolled


def convert_to_tflite_fp16(
    keras_model: tf.keras.Model,
    output_path: Path,
    unroll_rnn: bool = True
) -> Path:
    """
    Converts Keras model to Float16 quantized TFLite model.
    
    Args:
        keras_model: Trained Keras model
        output_path: Target .tflite file path
        unroll_rnn: If True, builds pure built-in model; otherwise enables Select TF Ops.
    """
    logger.info("Initializing TFLite conversion...")

    if unroll_rnn:
        logger.info("Unrolling BiLSTM layers for pure TFLite built-in & NNAPI/GPU delegate compatibility.")
        model_to_convert = build_unrolled_tflite_model(keras_model)
    else:
        model_to_convert = keras_model

    converter = tf.lite.TFLiteConverter.from_keras_model(model_to_convert)

    # Float16 Weight Quantization
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]

    if unroll_rnn:
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    else:
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
            tf.lite.OpsSet.SELECT_TF_OPS
        ]
        converter._experimental_lower_tensor_list_ops = False

    tflite_bytes = converter.convert()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(tflite_bytes)

    size_kb = len(tflite_bytes) / 1024.0
    logger.info(f"TFLite FP16 model exported to: {output_path.resolve()} ({size_kb:.2f} KB)")
    return output_path


def run_verification_and_benchmark(
    keras_model: tf.keras.Model,
    tflite_path: Path,
    data_dir: Path,
    num_test_sequences: int = 100
) -> Dict:
    """
    Runs 100 test sequences through both Keras and TFLite models, asserting < 0.5%
    accuracy drop and profiling single-window inference latency.
    """
    logger.info("=" * 65)
    logger.info(f"RUNNING VERIFICATION HARNESS ({num_test_sequences} Sequences)")
    logger.info("=" * 65)

    # Assemble exactly num_test_sequences test sequences
    X_test = np.load(data_dir / "X_test.npy")
    y_test = np.load(data_dir / "y_test.npy")

    if len(X_test) < num_test_sequences:
        # Supplement with validation sequences to reach 100
        X_val = np.load(data_dir / "X_val.npy")
        y_val = np.load(data_dir / "y_val.npy")
        X_combined = np.vstack([X_test, X_val])
        y_combined = np.concatenate([y_test, y_val])

        if len(X_combined) < num_test_sequences:
            # Repeat to reach exactly 100
            reps = int(np.ceil(num_test_sequences / len(X_combined)))
            X_eval = np.tile(X_combined, (reps, 1, 1))[:num_test_sequences]
            y_eval = np.tile(y_combined, reps)[:num_test_sequences]
        else:
            X_eval = X_combined[:num_test_sequences]
            y_eval = y_combined[:num_test_sequences]
    else:
        X_eval = X_test[:num_test_sequences]
        y_eval = y_test[:num_test_sequences]

    logger.info(f"Evaluated sequences shape: {X_eval.shape}")

    # 1. Keras Model Predictions
    logger.info("Running baseline inference on original Keras model...")
    t0_keras = time.perf_counter()
    keras_probs = keras_model.predict(X_eval, batch_size=32, verbose=0)
    keras_latency_total = (time.perf_counter() - t0_keras) * 1000.0

    keras_preds = np.argmax(keras_probs, axis=1)
    keras_acc = float(np.mean(keras_preds == y_eval))

    # 2. TFLite Interpreter Setup & Inference
    logger.info("Initializing TFLite Interpreter...")
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    input_idx = input_details[0]["index"]
    output_idx = output_details[0]["index"]

    # Warmup
    dummy_input = np.ascontiguousarray(X_eval[0:1], dtype=np.float32)
    interpreter.set_tensor(input_idx, dummy_input)
    interpreter.invoke()

    tflite_probs = []
    latencies_ms = []

    # Sequential single-window inference profiling (mobile HUD emulation)
    for i in range(num_test_sequences):
        single_window = np.ascontiguousarray(X_eval[i:i+1], dtype=np.float32)

        start_t = time.perf_counter()
        interpreter.set_tensor(input_idx, single_window)
        interpreter.invoke()
        end_t = time.perf_counter()

        output_data = interpreter.get_tensor(output_idx)
        tflite_probs.append(output_data[0])
        latencies_ms.append((end_t - start_t) * 1000.0)

    tflite_probs = np.array(tflite_probs)
    tflite_preds = np.argmax(tflite_probs, axis=1)
    tflite_acc = float(np.mean(tflite_preds == y_eval))

    # 3. Accuracy & Probability Divergence Verification
    acc_diff = abs(keras_acc - tflite_acc)
    max_prob_diff = float(np.max(np.abs(keras_probs - tflite_probs)))
    mean_prob_diff = float(np.mean(np.abs(keras_probs - tflite_probs)))

    logger.info("-" * 65)
    logger.info("ACCURACY & NUMERICAL FIDELITY CHECK:")
    logger.info(f"  Keras Baseline Accuracy:   {keras_acc * 100:.2f}%")
    logger.info(f"  TFLite FP16 Accuracy:      {tflite_acc * 100:.2f}%")
    logger.info(f"  Accuracy Drop / Shift:     {acc_diff * 100:.4f}% (Tolerance: < 0.50%)")
    logger.info(f"  Max Absolute Prob Delta:   {max_prob_diff:.6f}")
    logger.info(f"  Mean Absolute Prob Delta:  {mean_prob_diff:.6f}")

    # Assertion
    assert acc_diff <= 0.005, (
        f"FAILED: TFLite accuracy drop ({acc_diff * 100:.2f}%) exceeds allowed 0.50% threshold!"
    )
    logger.info("[✔] PASSED: Accuracy drop is strictly under 0.5% tolerance threshold.")

    # 4. Latency Benchmark Metrics
    mean_lat = float(np.mean(latencies_ms))
    std_lat = float(np.std(latencies_ms))
    p50_lat = float(np.percentile(latencies_ms, 50))
    p95_lat = float(np.percentile(latencies_ms, 95))
    p99_lat = float(np.percentile(latencies_ms, 99))
    throughput_fps = 1000.0 / mean_lat if mean_lat > 0 else 0

    logger.info("-" * 65)
    logger.info("SINGLE-WINDOW INFERENCE LATENCY BENCHMARK (ms):")
    logger.info(f"  Mean Latency:        {mean_lat:.2f} ms (+/- {std_lat:.2f} ms)")
    logger.info(f"  Median (P50):        {p50_lat:.2f} ms")
    logger.info(f"  95th Percentile:     {p95_lat:.2f} ms")
    logger.info(f"  99th Percentile:     {p99_lat:.2f} ms")
    logger.info(f"  Throughput:          {throughput_fps:.1f} windows/second")
    logger.info("-" * 65)

    return {
        "num_sequences": num_test_sequences,
        "keras_acc": keras_acc,
        "tflite_acc": tflite_acc,
        "acc_diff": acc_diff,
        "max_prob_diff": max_prob_diff,
        "mean_latency_ms": mean_lat,
        "p95_latency_ms": p95_lat,
        "p99_latency_ms": p99_lat,
        "throughput_fps": throughput_fps
    }


def main():
    parser = argparse.ArgumentParser(
        description="Convert Keras model to FP16 TFLite and run 100-sequence verification harness."
    )
    parser.add_argument(
        "--keras-model",
        type=Path,
        default=Path("models/gpu_trained_model/best_f1_badminton_model.keras"),
        help="Path to saved Keras model file."
    )
    parser.add_argument(
        "--output-tflite",
        type=Path,
        default=Path("models/gpu_trained_model/badminton_stroke_classifier_pure_fp16.tflite"),
        help="Output path for exported .tflite model."
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("dataset_root/processed_tensors"),
        help="Directory containing X_test.npy, y_test.npy, etc."
    )
    parser.add_argument(
        "--unroll-rnn",
        action="store_true",
        default=True,
        help="Unroll BiLSTM to pure built-in ops for native NNAPI/GPU delegate support."
    )
    parser.add_argument(
        "--num-sequences",
        type=int,
        default=100,
        help="Number of sequences for verification harness (default: 100)."
    )

    args = parser.parse_args()

    # Fallback to alternative model path if needed
    model_path = args.keras_model
    if not model_path.is_file():
        model_path = Path("models/hybrid_stroke_classifier/best_hybrid_badminton.keras")

    logger.info(f"Loading trained Keras model from: {model_path}")
    keras_model = tf.keras.models.load_model(str(model_path), compile=False)

    # Convert to TFLite FP16
    tflite_path = convert_to_tflite_fp16(
        keras_model=keras_model,
        output_path=args.output_tflite,
        unroll_rnn=args.unroll_rnn
    )

    # Run Verification & Latency Benchmark
    run_verification_and_benchmark(
        keras_model=keras_model,
        tflite_path=tflite_path,
        data_dir=args.data_dir,
        num_test_sequences=args.num_sequences
    )


if __name__ == "__main__":
    main()
