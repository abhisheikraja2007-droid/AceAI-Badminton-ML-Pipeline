"""
Advanced TensorFlow GPU Training Script for Badminton Stroke Recognition
========================================================================
Implements a production-grade training pipeline for the Hybrid 1D-CNN-BiLSTM model:
1. Mixed-Precision Training (FP16 compute with FP32 master weights).
2. Balanced Class Weights via scikit-learn to mitigate stroke imbalance.
3. AdamW Optimizer with CosineDecayRestarts learning rate schedule.
4. Categorical Focal Loss to emphasize hard-to-classify velocity impact spikes.
5. Custom Validation F1-score Callback with EarlyStopping on validation F1.
6. ModelCheckpoint & TensorBoard logging.
7. Post-training diagnostics: Classification Report, Multi-Class ROC-AUC Curves,
   Confusion Matrix, and precision verification (>95%) on "overhead_smash".
"""

import argparse
import datetime
import logging
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import matplotlib
matplotlib.use("Agg")  # Non-interactive backend for headless execution
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve
)
from sklearn.utils.class_weight import compute_class_weight

import keras
import tensorflow as tf
from tensorflow.keras import layers, models, optimizers

# Import model architecture builder from our existing module
from badminton_hybrid_model import TemporalAttention, build_hybrid_badminton_model

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("GPUTrainer")

CLASS_NAMES = ["overhead_smash", "other_stroke", "idle_footwork"]
NUM_CLASSES = len(CLASS_NAMES)


def setup_mixed_precision():
    """Configures TensorFlow mixed-precision policy for accelerated GPU inference/training."""
    gpus = tf.config.list_physical_devices("GPU")
    if gpus:
        try:
            for gpu in gpus:
                tf.config.experimental.set_memory_growth(gpu, True)
            policy = tf.keras.mixed_precision.Policy("mixed_float16")
            tf.keras.mixed_precision.set_global_policy(policy)
            logger.info(f"GPU detected ({len(gpus)} device(s)). Global policy set to: mixed_float16.")
        except Exception as e:
            logger.warning(f"Could not configure GPU mixed precision: {e}")
    else:
        logger.info("No dedicated GPU detected. Proceeding with standard float32 precision.")


@keras.saving.register_keras_serializable(package="AceAI")
class CategoricalFocalLoss(tf.keras.losses.Loss):
    """
    Multi-Class Categorical Focal Loss:
    FL(p_t) = -alpha_t * (1 - p_t)^gamma * log(p_t)
    Down-weights easy examples (e.g. idle footwork) to focus gradients on
    subtle biomechanical boundaries (e.g. smash vs. attacking clear).
    """

    def __init__(
        self,
        gamma: float = 2.0,
        alpha: Optional[List[float]] = None,
        name: str = "categorical_focal_loss",
        **kwargs
    ):
        super().__init__(name=name, **kwargs)
        self.gamma = float(gamma)
        self.alpha = alpha

    def call(self, y_true, y_pred):
        y_true = tf.cast(y_true, tf.float32)
        y_pred = tf.clip_by_value(y_pred, 1e-7, 1.0 - 1e-7)

        # Cross entropy per class
        cross_entropy = -y_true * tf.math.log(y_pred)
        # Modulating factor (1 - p_t)^gamma
        modulating_factor = tf.pow(1.0 - y_pred, self.gamma)
        focal_loss = modulating_factor * cross_entropy

        if self.alpha is not None:
            alpha_factor = tf.constant(self.alpha, dtype=tf.float32)
            focal_loss = focal_loss * alpha_factor

        return tf.reduce_mean(tf.reduce_sum(focal_loss, axis=-1))

    def get_config(self):
        config = super().get_config()
        config.update({"gamma": self.gamma, "alpha": self.alpha})
        return config


class ValidationF1Callback(tf.keras.callbacks.Callback):
    """
    Computes macro validation F1-score and class-specific metrics at each epoch end,
    populating logs so EarlyStopping and ModelCheckpoint can monitor 'val_f1'.
    """

    def __init__(self, validation_data: Tuple[np.ndarray, np.ndarray], smash_idx: int = 0):
        super().__init__()
        self.X_val, self.y_val = validation_data
        if len(self.y_val.shape) > 1 and self.y_val.shape[1] > 1:
            self.y_true = np.argmax(self.y_val, axis=1)
        else:
            self.y_true = self.y_val.astype(int)
        self.smash_idx = smash_idx

    def on_epoch_end(self, epoch, logs=None):
        logs = logs or {}
        y_prob = self.model.predict(self.X_val, verbose=0)
        y_pred = np.argmax(y_prob, axis=1)

        val_macro_f1 = f1_score(self.y_true, y_pred, average="macro", zero_division=0)
        val_smash_f1 = f1_score(self.y_true, y_pred, labels=[self.smash_idx], average="macro", zero_division=0)
        val_smash_precision = precision_score(self.y_true, y_pred, labels=[self.smash_idx], average="macro", zero_division=0)

        logs["val_f1"] = float(val_macro_f1)
        logs["val_smash_f1"] = float(val_smash_f1)
        logs["val_smash_prec"] = float(val_smash_precision)

        logger.info(
            f"Epoch {epoch + 1:02d} Metrics -> val_macro_f1: {val_macro_f1:.4f} | "
            f"smash_f1: {val_smash_f1:.4f} | smash_precision: {val_smash_precision * 100:.2f}%"
        )


def plot_roc_curves(
    y_test_onehot: np.ndarray,
    y_prob: np.ndarray,
    output_path: Path
):
    """Generates multi-class One-vs-Rest ROC curves with micro/macro AUC."""
    plt.figure(figsize=(9, 7))
    colors = ["#e74c3c", "#3498db", "#2ecc71"]

    # Compute ROC curve and ROC area for each class
    for i, (name, color) in enumerate(zip(CLASS_NAMES, colors)):
        fpr, tpr, _ = roc_curve(y_test_onehot[:, i], y_prob[:, i])
        roc_auc = roc_auc_score(y_test_onehot[:, i], y_prob[:, i])
        plt.plot(fpr, tpr, color=color, lw=2.5, label=f"{name} (AUC = {roc_auc:.3f})")

    # Macro-average ROC AUC
    macro_auc = roc_auc_score(y_test_onehot, y_prob, average="macro")
    plt.plot([0, 1], [0, 1], "k--", lw=1.5, label="Random Guess (AUC = 0.500)")

    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel("False Positive Rate (1 - Specificity)", fontsize=12)
    plt.ylabel("True Positive Rate (Sensitivity)", fontsize=12)
    plt.title(f"Multi-Class ROC Curves (Macro AUC = {macro_auc:.3f})", fontsize=14, fontweight="bold")
    plt.legend(loc="lower right", fontsize=11)
    plt.grid(alpha=0.3)
    plt.tight_layout()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300)
    plt.close()
    logger.info(f"Saved ROC curves plot to: {output_path}")


def plot_confusion_matrix(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    output_path: Path
):
    """Generates an annotated confusion matrix heatmap with counts and percentages."""
    cm = confusion_matrix(y_true, y_pred)
    cm_norm = cm.astype("float") / (cm.sum(axis=1)[:, np.newaxis] + 1e-7)

    plt.figure(figsize=(8, 6.5))
    annotations = np.empty_like(cm, dtype=object)
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            annotations[i, j] = f"{cm[i, j]}\n({cm_norm[i, j]*100:.1f}%)"

    sns.heatmap(
        cm,
        annot=annotations,
        fmt="",
        cmap="Blues",
        xticklabels=CLASS_NAMES,
        yticklabels=CLASS_NAMES,
        cbar=True,
        annot_kws={"size": 11, "weight": "bold"}
    )

    plt.xlabel("Predicted Class", fontsize=12, fontweight="bold")
    plt.ylabel("True Class", fontsize=12, fontweight="bold")
    plt.title("Badminton Stroke Recognition - Confusion Matrix", fontsize=14, fontweight="bold")
    plt.tight_layout()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300)
    plt.close()
    logger.info(f"Saved Confusion Matrix heatmap to: {output_path}")


def train_gpu_pipeline(
    data_dir: Path,
    output_dir: Path,
    epochs: int = 35,
    batch_size: int = 32,
    focal_gamma: float = 2.0
):
    setup_mixed_precision()

    logger.info(f"Loading preprocessed tensors from: {data_dir}")
    X_train = np.load(data_dir / "X_train.npy")
    y_train_raw = np.load(data_dir / "y_train.npy")
    X_val = np.load(data_dir / "X_val.npy")
    y_val_raw = np.load(data_dir / "y_val.npy")
    X_test = np.load(data_dir / "X_test.npy")
    y_test_raw = np.load(data_dir / "y_test.npy")

    # One-hot encoding for Categorical Focal Loss
    y_train = tf.keras.utils.to_categorical(y_train_raw, num_classes=NUM_CLASSES)
    y_val = tf.keras.utils.to_categorical(y_val_raw, num_classes=NUM_CLASSES)
    y_test = tf.keras.utils.to_categorical(y_test_raw, num_classes=NUM_CLASSES)

    # 1. Compute Balanced Class Weights
    classes_present = np.unique(y_train_raw)
    class_weights_arr = compute_class_weight(
        class_weight="balanced",
        classes=classes_present,
        y=y_train_raw
    )
    class_weight_dict = {int(c): float(w) for c, w in zip(classes_present, class_weights_arr)}
    logger.info(f"Computed Balanced Class Weights: {class_weight_dict}")

    input_shape = (X_train.shape[1], X_train.shape[2])

    # 2. Build Model
    model = build_hybrid_badminton_model(
        input_shape=input_shape,
        num_classes=NUM_CLASSES,
        learning_rate=1e-3,
        dropout_rate=0.25
    )

    # 3. Learning Rate Schedule: CosineDecayRestarts
    steps_per_epoch = len(X_train) // batch_size
    first_decay_steps = max(1, steps_per_epoch * 8)
    lr_schedule = optimizers.schedules.CosineDecayRestarts(
        initial_learning_rate=1e-3,
        first_decay_steps=first_decay_steps,
        t_mul=2.0,
        m_mul=0.85,
        alpha=1e-4
    )

    # 4. AdamW Optimizer
    optimizer = optimizers.AdamW(
        learning_rate=lr_schedule,
        weight_decay=1e-4,
        clipnorm=1.0
    )

    # 5. Categorical Focal Loss
    focal_loss = CategoricalFocalLoss(
        gamma=focal_gamma,
        alpha=class_weights_arr.tolist()
    )

    model.compile(
        optimizer=optimizer,
        loss=focal_loss,
        metrics=["accuracy"]
    )

    # 6. Callbacks
    output_dir.mkdir(parents=True, exist_ok=True)
    best_model_path = output_dir / "best_f1_badminton_model.keras"
    timestamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    tb_log_dir = output_dir / "tensorboard_logs" / timestamp

    f1_callback = ValidationF1Callback(validation_data=(X_val, y_val), smash_idx=0)

    callbacks = [
        f1_callback,
        tf.keras.callbacks.EarlyStopping(
            monitor="val_f1",
            mode="max",
            patience=12,
            restore_best_weights=True,
            verbose=1
        ),
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(best_model_path),
            monitor="val_f1",
            mode="max",
            save_best_only=True,
            verbose=1
        ),
        tf.keras.callbacks.TensorBoard(
            log_dir=str(tb_log_dir),
            histogram_freq=1,
            write_graph=True
        )
    ]

    logger.info("Commencing GPU model training with AdamW, CosineDecayRestarts, and Focal Loss...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=epochs,
        batch_size=batch_size,
        class_weight=class_weight_dict,
        callbacks=callbacks,
        verbose=1
    )

    # 7. Post-Training Evaluation on Held-Out Test Set
    logger.info("\n" + "=" * 65)
    logger.info("EVALUATION ON HELD-OUT TEST PARTITION")
    logger.info("=" * 65)

    y_test_probs = model.predict(X_test, verbose=0)
    y_test_pred = np.argmax(y_test_probs, axis=1)

    # Classification Report
    report_dict = classification_report(
        y_test_raw, y_test_pred,
        target_names=CLASS_NAMES,
        output_dict=True,
        zero_division=0
    )
    report_text = classification_report(
        y_test_raw, y_test_pred,
        target_names=CLASS_NAMES,
        digits=4,
        zero_division=0
    )
    logger.info(f"\n{report_text}")

    # Generate Evaluation Plots
    plots_dir = output_dir / "evaluation_plots"
    plot_roc_curves(y_test, y_test_probs, plots_dir / "roc_auc_curves.png")
    plot_confusion_matrix(y_test_raw, y_test_pred, plots_dir / "confusion_matrix.png")

    # 8. Precision Verification on "overhead_smash"
    smash_precision = report_dict["overhead_smash"]["precision"]
    smash_recall = report_dict["overhead_smash"]["recall"]
    smash_f1 = report_dict["overhead_smash"]["f1-score"]

    logger.info("=" * 65)
    logger.info(f"TARGET CLASS EVALUATION: 'overhead_smash'")
    logger.info(f"  Precision: {smash_precision * 100:.2f}% (Target: >= 95.00%)")
    logger.info(f"  Recall:    {smash_recall * 100:.2f}%")
    logger.info(f"  F1-Score:  {smash_f1:.4f}")

    if smash_precision >= 0.95:
        logger.info("[✔] VERIFICATION PASSED: Overhead smash precision meets or exceeds the 95% threshold!")
    else:
        logger.warning(
            f"[!] Precision currently at {smash_precision * 100:.2f}%. "
            "To reach >95% in full deployment, record and ingest real court-level smartphone clips "
            "into dataset_root/raw/smartphone_calibration/."
        )
    logger.info("=" * 65)

    return model, report_dict


def main():
    parser = argparse.ArgumentParser(
        description="Train Hybrid 1D-CNN-BiLSTM with Mixed-Precision, Focal Loss, AdamW, and F1 early stopping."
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
        default=Path("models/gpu_trained_model"),
        help="Directory to save checkpoint, plots, and TensorBoard logs."
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=25,
        help="Training epochs (default: 25)."
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=32,
        help="Mini-batch size (default: 32)."
    )
    parser.add_argument(
        "--focal-gamma",
        type=float,
        default=2.0,
        help="Focal Loss focusing parameter gamma (default: 2.0)."
    )

    args = parser.parse_args()

    train_gpu_pipeline(
        data_dir=args.data_dir,
        output_dir=args.output_dir,
        epochs=args.epochs,
        batch_size=args.batch_size,
        focal_gamma=args.focal_gamma
    )


if __name__ == "__main__":
    main()

