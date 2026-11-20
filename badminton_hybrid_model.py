"""
Mobile Edge-Optimized Hybrid 1D-CNN + BiLSTM with Temporal Attention
=====================================================================
A high-performance TensorFlow/Keras neural network architecture tailored for
badminton stroke recognition (overhead_smash, other_stroke, idle_footwork)
from kinematic time-series.

Key Architectural Components:
1. Input Shape: (30 frames, num_features) representing 0.5s at 60 FPS.
2. 1D-CNN Feature Extractor:
   - Conv1D(64, kernel_size=3, activation='swish', padding='same') + BatchNorm + SpatialDropout1D(0.2)
   - Conv1D(128, kernel_size=3, activation='swish', padding='same') + BatchNorm + SpatialDropout1D(0.2)
   Captures localized acceleration vectors and sharp velocity transitions.
3. Bidirectional Recurrent Temporal Modeling:
   - BiLSTM(64 units, return_sequences=True, dropout=0.2)
   - BiLSTM(64 units, return_sequences=True, dropout=0.2)
   Models forward-backward kinetic chain progression (windup -> impact -> follow-through).
4. Temporal Attention Mechanism Layer:
   Computes frame-level attention weights alpha_t, focusing the context representation
   directly onto the rapid 150ms shuttlecock impact window.
5. Mobile Edge Head:
   - Dense(64, activation='swish') + BatchNorm + Dropout(0.3)
   - Dense(3, activation='softmax') for multi-class classification.
"""

import argparse
import json
import logging
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np

# Lazy imports for TensorFlow to permit CLI help without slow startup
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("HybridModel")

TARGET_CLASSES = ["overhead_smash", "other_stroke", "idle_footwork"]
NUM_CLASSES = len(TARGET_CLASSES)


import tensorflow as tf
import keras
from tensorflow.keras import layers, models, optimizers


@keras.saving.register_keras_serializable(package="AceAI")
class TemporalAttention(layers.Layer):
    """
    Temporal Attention Layer:
    u_t = tanh(W * h_t + b)
    alpha_t = softmax(u_t^T * v)
    context = sum(alpha_t * h_t)
    """

    def __init__(self, units: int = 64, **kwargs):
        super().__init__(**kwargs)
        self.units = units

    def build(self, input_shape):
        feature_dim = input_shape[-1]
        self.W = self.add_weight(
            name="attention_weight",
            shape=(feature_dim, self.units),
            initializer="glorot_uniform",
            trainable=True
        )
        self.b = self.add_weight(
            name="attention_bias",
            shape=(self.units,),
            initializer="zeros",
            trainable=True
        )
        self.v = self.add_weight(
            name="attention_context_vector",
            shape=(self.units, 1),
            initializer="glorot_uniform",
            trainable=True
        )
        super().build(input_shape)

    def call(self, inputs, return_attention_weights: bool = False):
        # inputs shape: (batch_size, time_steps, features)
        score = tf.nn.tanh(tf.tensordot(inputs, self.W, axes=1) + self.b)
        attention_weights = tf.tensordot(score, self.v, axes=1)
        attention_weights = tf.nn.softmax(attention_weights, axis=1)
        context_vector = tf.reduce_sum(inputs * attention_weights, axis=1)

        if return_attention_weights:
            return context_vector, tf.squeeze(attention_weights, axis=-1)
        return context_vector

    def get_config(self):
        config = super().get_config()
        config.update({"units": self.units})
        return config


def get_temporal_attention_layer():
    return TemporalAttention


def build_hybrid_badminton_model(
    input_shape: Tuple[int, int] = (30, 269),
    num_classes: int = 3,
    learning_rate: float = 1e-3,
    dropout_rate: float = 0.2
):
    """
    Constructs the end-to-end Keras Hybrid 1D-CNN + BiLSTM + Attention model.

    Args:
        input_shape: (time_steps=30, num_features=269)
        num_classes: 3 for ["overhead_smash", "other_stroke", "idle_footwork"]
        learning_rate: Adam optimizer learning rate
        dropout_rate: Dropout rate for regularization

    Returns:
        Compiled tf.keras.Model instance.
    """
    import tensorflow as tf
    from tensorflow.keras import layers, models, optimizers

    TemporalAttention = get_temporal_attention_layer()

    # 1. Input Layer
    inputs = layers.Input(shape=input_shape, name="kinematic_sequence_input")

    # 2. Stage 1: 1D-CNN Feature Extractor (Short-term velocity spikes & acceleration)
    x = layers.Conv1D(
        filters=64,
        kernel_size=3,
        padding="same",
        activation="swish",
        name="conv1d_stage1"
    )(inputs)
    x = layers.BatchNormalization(name="bn_conv1")(x)
    x = layers.SpatialDropout1D(dropout_rate, name="spatial_dropout_conv1")(x)

    x = layers.Conv1D(
        filters=128,
        kernel_size=3,
        padding="same",
        activation="swish",
        name="conv1d_stage2"
    )(x)
    x = layers.BatchNormalization(name="bn_conv2")(x)
    x = layers.SpatialDropout1D(dropout_rate, name="spatial_dropout_conv2")(x)

    # 3. Stage 2: Bi-Directional LSTM Temporal Modeling
    # First BiLSTM: 64 units forward + 64 units backward = 128 channels
    x = layers.Bidirectional(
        layers.LSTM(
            units=64,
            return_sequences=True,
            dropout=dropout_rate,
            name="forward_backward_lstm1"
        ),
        name="bidirectional_lstm1"
    )(x)

    # Second BiLSTM: 64 units forward + 64 units backward = 128 channels
    x = layers.Bidirectional(
        layers.LSTM(
            units=64,
            return_sequences=True,
            dropout=dropout_rate,
            name="forward_backward_lstm2"
        ),
        name="bidirectional_lstm2"
    )(x)

    # 4. Stage 3: Temporal Attention Mechanism (Weights the 150ms impact frame)
    context_vector = TemporalAttention(
        units=64,
        name="temporal_attention_mechanism"
    )(x)

    # 5. Stage 4: Classification Head
    dense_features = layers.Dense(64, activation="swish", name="dense_dense64")(context_vector)
    dense_features = layers.BatchNormalization(name="bn_dense")(dense_features)
    dense_features = layers.Dropout(0.3, name="dense_dropout")(dense_features)

    # Final Softmax Probabilities
    outputs = layers.Dense(
        num_classes,
        activation="softmax",
        dtype="float32",
        name="stroke_class_probabilities"
    )(dense_features)

    model = models.Model(inputs=inputs, outputs=outputs, name="AceAI_Badminton_Hybrid_Net")

    # Optimizer with gradient clipping for stable LSTM convergence
    optimizer = optimizers.Adam(learning_rate=learning_rate, clipnorm=1.0)

    model.compile(
        optimizer=optimizer,
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    return model


def export_to_tflite(
    keras_model,
    output_tflite_path: Path,
    quantize_fp16: bool = True
) -> Path:
    """
    Exports trained Keras model to an optimized FlatBuffer (.tflite) for mobile deployment.
    Supports FP16 quantization for edge accelerators (Snapdragon NPU / MediaTek APU).
    """
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
    # Enable TensorFlow Select Ops for complete BiLSTM compatibility if needed
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    converter._experimental_lower_tensor_list_ops = False

    if quantize_fp16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
        logger.info("Enabling FP16 quantization for mobile NPU/GPU acceleration.")

    tflite_model = converter.convert()

    output_tflite_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_tflite_path, "wb") as f:
        f.write(tflite_model)

    tflite_size_kb = len(tflite_model) / 1024.0
    logger.info(f"TFLite model successfully exported to: {output_tflite_path} ({tflite_size_kb:.2f} KB)")
    return output_tflite_path


def train_and_evaluate_model(
    data_dir: Path,
    output_model_dir: Path,
    epochs: int = 25,
    batch_size: int = 32
):
    """Loads processed tensors, trains the hybrid architecture, and saves artifacts."""
    import tensorflow as tf

    logger.info(f"Loading dataset tensors from: {data_dir}")
    X_train = np.load(data_dir / "X_train.npy")
    y_train = np.load(data_dir / "y_train.npy")
    X_val = np.load(data_dir / "X_val.npy")
    y_val = np.load(data_dir / "y_val.npy")
    X_test = np.load(data_dir / "X_test.npy")
    y_test = np.load(data_dir / "y_test.npy")

    input_shape = (X_train.shape[1], X_train.shape[2])
    logger.info(f"Input Shape: {input_shape} (30 frames, {input_shape[1]} features)")

    model = build_hybrid_badminton_model(
        input_shape=input_shape,
        num_classes=NUM_CLASSES,
        learning_rate=1e-3
    )
    model.summary(print_fn=logger.info)

    output_model_dir.mkdir(parents=True, exist_ok=True)
    best_weights_path = output_model_dir / "best_hybrid_badminton.keras"

    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=8,
            restore_best_weights=True,
            verbose=1
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=3,
            min_lr=1e-5,
            verbose=1
        ),
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(best_weights_path),
            monitor="val_loss",
            save_best_only=True,
            verbose=1
        )
    ]

    logger.info("Commencing model training...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=epochs,
        batch_size=batch_size,
        callbacks=callbacks,
        verbose=1
    )

    # Final Test Evaluation
    logger.info("Evaluating on held-out test partition...")
    test_loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
    logger.info(f"Test Accuracy: {test_acc * 100:.2f}% | Test Loss: {test_loss:.4f}")

    # Export to Mobile TFLite
    tflite_path = output_model_dir / "badminton_smash_classifier_fp16.tflite"
    export_to_tflite(model, tflite_path, quantize_fp16=True)

    return model


def main():
    parser = argparse.ArgumentParser(
        description="Build, train, and export Hybrid 1D-CNN + BiLSTM + Attention model."
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("dataset_root/processed_tensors"),
        help="Directory containing X_train.npy, y_train.npy, etc."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("models/hybrid_stroke_classifier"),
        help="Directory to save Keras model and exported TFLite file."
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=25,
        help="Number of training epochs (default: 25)."
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=32,
        help="Batch size (default: 32)."
    )

    args = parser.parse_args()

    train_and_evaluate_model(
        data_dir=args.data_dir,
        output_model_dir=args.output_dir,
        epochs=args.epochs,
        batch_size=args.batch_size
    )


if __name__ == "__main__":
    main()

