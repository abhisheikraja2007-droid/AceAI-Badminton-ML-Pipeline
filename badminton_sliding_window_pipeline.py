"""
Badminton Kinematic Dataset Preprocessing & Augmentation Pipeline
================================================================
Converts engineered biomechanical sequences into scale-invariant,
sliding-window tensors with 3D data augmentation and stratified splitting.

Key Features:
1. Fixed-length sliding windows: 30 frames with 50% overlap (stride = 15).
2. Scale-invariant normalization: Centering at mid-hip origin and scaling by torso length.
3. 3D Data Augmentation:
   - Random Y-axis rotation (+/- 10 degrees) simulating camera angle variance.
   - Gaussian coordinate noise (sigma = 0.015).
   - Temporal time-warping (0.85x to 1.15x speed scaling via linear interpolation).
4. Stratified 80% Train / 10% Validation / 10% Test split at clip level to prevent leakage.
5. Saves clean NumPy tensors (.npy) ready for deep learning models (LSTM, Transformer, GCN).
"""

import argparse
import json
import logging
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from scipy.interpolate import interp1d
from sklearn.model_selection import train_test_split

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("SlidingWindowPipeline")

# Class Mapping
LABEL_TO_INT = {
    "overhead_smash": 0,
    "other_stroke": 1,
    "idle_footwork": 2
}
INT_TO_LABEL = {v: k for k, v in LABEL_TO_INT.items()}

# MediaPipe Landmark Indices for Torso Normalization
LM_LEFT_SHOULDER = 11
LM_RIGHT_SHOULDER = 12
LM_LEFT_HIP = 23
LM_RIGHT_HIP = 24


class ScaleInvariantNormalizer:
    """Normalizes 3D coordinates by centering at mid-hip and scaling by torso length."""

    @staticmethod
    def normalize_sequence(
        world_landmarks: np.ndarray,
        eps: float = 1e-6
    ) -> Tuple[np.ndarray, np.ndarray, float]:
        """
        Args:
            world_landmarks: (T, 33, 3) keypoint coordinates in meters
            eps: Numerical stability constant
            
        Returns:
            normalized_coords: (T, 33, 3) scale-invariant keypoints
            mid_hip: (T, 3) frame-by-frame hip origins
            median_torso_len: Median torso length across sequence
        """
        # Mid-hip origin (T, 3)
        left_hip = world_landmarks[:, LM_LEFT_HIP, :]
        right_hip = world_landmarks[:, LM_RIGHT_HIP, :]
        mid_hip = (left_hip + right_hip) / 2.0

        # Mid-shoulder (T, 3)
        left_shoulder = world_landmarks[:, LM_LEFT_SHOULDER, :]
        right_shoulder = world_landmarks[:, LM_RIGHT_SHOULDER, :]
        mid_shoulder = (left_shoulder + right_shoulder) / 2.0

        # Torso length per frame (T, 1)
        torso_lengths = np.linalg.norm(mid_shoulder - mid_hip, axis=-1, keepdims=True)
        median_torso_len = float(np.median(torso_lengths))
        if median_torso_len < eps:
            median_torso_len = 0.5  # Typical adult torso length fallback in meters

        # Translate mid-hip to (0, 0, 0) and scale by median torso length
        centered = world_landmarks - mid_hip[:, np.newaxis, :]
        normalized_coords = centered / (median_torso_len + eps)

        return normalized_coords.astype(np.float32), mid_hip, median_torso_len


class DataAugmentor:
    """Applies realistic 3D spatial, sensor noise, and temporal speed augmentations."""

    def __init__(
        self,
        rotation_range_deg: float = 10.0,
        noise_sigma: float = 0.015,
        warp_min: float = 0.85,
        warp_max: float = 1.15
    ):
        self.rotation_range = rotation_range_deg
        self.noise_sigma = noise_sigma
        self.warp_min = warp_min
        self.warp_max = warp_max

    def rotate_y_axis(self, window_coords: np.ndarray, angle_deg: Optional[float] = None) -> np.ndarray:
        """
        Rotates 3D coordinates around vertical Y-axis:
        R_y(theta) = [[cos(t), 0, sin(t)], [0, 1, 0], [-sin(t), 0, cos(t)]]
        Simulates camera azimuth variance (e.g., +/- 10 degrees).
        """
        if angle_deg is None:
            angle_deg = np.random.uniform(-self.rotation_range, self.rotation_range)

        rad = np.radians(angle_deg)
        cos_t, sin_t = np.cos(rad), np.sin(rad)
        R_y = np.array([
            [cos_t, 0.0, sin_t],
            [0.0,   1.0, 0.0],
            [-sin_t, 0.0, cos_t]
        ], dtype=np.float32)

        # window_coords shape: (W, 33, 3) or (W, N, 3)
        orig_shape = window_coords.shape
        reshaped = window_coords.reshape(-1, 3)
        rotated = np.dot(reshaped, R_y.T)
        return rotated.reshape(orig_shape).astype(np.float32)

    def add_gaussian_noise(self, window_coords: np.ndarray) -> np.ndarray:
        """Injects zero-mean Gaussian coordinate jitter simulating optical/sensor noise."""
        noise = np.random.normal(0.0, self.noise_sigma, size=window_coords.shape).astype(np.float32)
        return window_coords + noise

    def time_warp_window(self, window_data: np.ndarray, speed_factor: Optional[float] = None) -> np.ndarray:
        """
        Scales stroke execution speed (0.85x to 1.15x) along temporal axis (axis 0)
        and resamples back to original window length via linear interpolation.
        """
        W, feat_dim = window_data.shape[0], window_data.shape[1]
        if speed_factor is None:
            speed_factor = np.random.uniform(self.warp_min, self.warp_max)

        orig_time = np.linspace(0.0, 1.0, W)
        # Warped time grid
        warped_len = max(5, int(round(W * speed_factor)))
        warped_grid = np.linspace(0.0, 1.0, warped_len)

        # Interpolate features along temporal axis
        interp_fn = interp1d(orig_time, window_data, axis=0, kind="linear", fill_value="extrapolate")
        warped_sequence = interp_fn(warped_grid)

        # Resample back to fixed window length W
        resample_fn = interp1d(np.linspace(0.0, 1.0, warped_len), warped_sequence, axis=0, kind="linear")
        resampled_window = resample_fn(orig_time)

        return resampled_window.astype(np.float32)

    def augment_window(self, window_coords: np.ndarray, extra_features: Optional[np.ndarray] = None) -> np.ndarray:
        """
        Executes complete composite augmentation:
        1. 3D Y-axis rotation (+/- 10 deg)
        2. Gaussian coordinate noise (sigma = 0.015)
        3. Temporal speed warping (0.85x - 1.15x)
        """
        W = window_coords.shape[0]

        # 1. Rotate Y-axis
        aug_coords = self.rotate_y_axis(window_coords)

        # 2. Add Gaussian noise
        aug_coords = self.add_gaussian_noise(aug_coords)

        # Flatten coordinates: (W, 33*3)
        flat_coords = aug_coords.reshape(W, -1)

        # Combine with extra features if present
        if extra_features is not None:
            full_window = np.hstack([flat_coords, extra_features])
        else:
            full_window = flat_coords

        # 3. Time warp full composite window
        warped_full = self.time_warp_window(full_window)
        return warped_full


class SlidingWindowPipeline:
    """Extracts overlapping sliding windows and partitions into stratified splits."""

    def __init__(
        self,
        window_size: int = 30,
        stride: int = 15,
        augment_multiplier: int = 2,
        seed: int = 42
    ):
        """
        Args:
            window_size: Frames per temporal window (30 frames = 0.5s at 60 FPS).
            stride: Stride step (15 frames = 50% overlap).
            augment_multiplier: Number of synthetic augmented variants per training window.
            seed: Random seed for deterministic reproducibility.
        """
        self.window_size = window_size
        self.stride = stride
        self.augment_multiplier = augment_multiplier
        self.seed = seed
        np.random.seed(seed)
        self.augmentor = DataAugmentor()

    def extract_windows_from_sequence(
        self,
        sequence_features: np.ndarray,
        label_int: int,
        is_train: bool = False,
        raw_coords_3d: Optional[np.ndarray] = None
    ) -> Tuple[List[np.ndarray], List[int]]:
        """
        Slices a sequence into fixed (window_size, D) tensors.
        Applies data augmentation on training sequences.
        """
        T = sequence_features.shape[0]
        windows: List[np.ndarray] = []
        labels: List[int] = []

        if T < self.window_size:
            # Pad short sequences if necessary
            pad_len = self.window_size - T
            pad_arr = np.tile(sequence_features[-1:], (pad_len, 1))
            padded = np.vstack([sequence_features, pad_arr])
            windows.append(padded)
            labels.append(label_int)
            return windows, labels

        num_windows = (T - self.window_size) // self.stride + 1

        for w in range(num_windows):
            start = w * self.stride
            end = start + self.window_size
            window_slice = sequence_features[start:end]

            # Original clean window
            windows.append(window_slice.copy())
            labels.append(label_int)

            # Apply data augmentation for training partition
            if is_train and raw_coords_3d is not None and self.augment_multiplier > 0:
                coords_slice = raw_coords_3d[start:end]
                # Extra features (velocities, angles, etc.)
                extra_slice = window_slice[:, coords_slice.shape[1] * 3:] if window_slice.shape[1] > 99 else None

                for _ in range(self.augment_multiplier):
                    aug_window = self.augmentor.augment_window(coords_slice, extra_slice)
                    windows.append(aug_window)
                    labels.append(label_int)

        return windows, labels

    def process_and_split(
        self,
        engineered_dir: Path,
        landmarks_dir: Path,
        output_dir: Path
    ) -> Dict[str, Tuple[np.ndarray, np.ndarray]]:
        """
        Orchestrates full ingestion, scale normalization, clip-level stratified split,
        sliding-window extraction, augmentation, and tensor serialization.
        """
        logger.info(f"Ingesting data from: {engineered_dir} and {landmarks_dir}")
        output_dir.mkdir(parents=True, exist_ok=True)

        engineered_files = sorted(engineered_dir.glob("*_engineered.npz"))
        if not engineered_files:
            raise FileNotFoundError(f"No engineered files found in {engineered_dir}")

        clips_meta = []
        normalizer = ScaleInvariantNormalizer()

        for f in engineered_files:
            data = np.load(f, allow_pickle=True)
            clip_id = str(data["clip_id"])
            label_str = str(data["label"]).strip().lower()
            if label_str not in LABEL_TO_INT:
                label_str = "other_stroke"
            label_int = LABEL_TO_INT[label_str]

            # Find matching raw 3D landmarks for geometric scale normalization
            lm_file = landmarks_dir / f"{clip_id}.npz"
            if not lm_file.is_file():
                continue
            lm_data = np.load(lm_file, allow_pickle=True)
            raw_world = lm_data["world_landmarks"] # (T, 33, 3)

            # Apply scale-invariant normalization
            norm_world, _, _ = normalizer.normalize_sequence(raw_world)

            # Base engineered feature matrix
            feat_matrix = data["feature_matrix"] # (T, 269)

            # Replace raw world coords with scale-invariant normalized coords in feature matrix
            W_frames = norm_world.shape[0]
            flat_norm_world = norm_world.reshape(W_frames, -1)
            feat_matrix[:, :99] = flat_norm_world

            clips_meta.append({
                "clip_id": clip_id,
                "label_int": label_int,
                "label_str": label_str,
                "features": feat_matrix,
                "norm_coords": norm_world
            })

        logger.info(f"Loaded {len(clips_meta)} clips for partitioning.")
        clip_labels = [c["label_int"] for c in clips_meta]

        # Stratified 80% Train, 10% Validation, 10% Test split at clip level
        train_clips, temp_clips, train_y, temp_y = train_test_split(
            clips_meta, clip_labels, test_size=0.20, random_state=self.seed, stratify=clip_labels
        )
        val_clips, test_clips, val_y, test_y = train_test_split(
            temp_clips, temp_y, test_size=0.50, random_state=self.seed, stratify=temp_y
        )

        logger.info(
            f"Clip Split Counts -> Train: {len(train_clips)} | Val: {len(val_clips)} | Test: {len(test_clips)}"
        )

        splits = {
            "train": (train_clips, True),
            "val": (val_clips, False),
            "test": (test_clips, False)
        }

        dataset_tensors = {}

        for split_name, (clip_list, is_train) in splits.items():
            all_windows = []
            all_labels = []

            for clip in clip_list:
                w_list, l_list = self.extract_windows_from_sequence(
                    sequence_features=clip["features"],
                    label_int=clip["label_int"],
                    is_train=is_train,
                    raw_coords_3d=clip["norm_coords"]
                )
                all_windows.extend(w_list)
                all_labels.extend(l_list)

            X_tensor = np.array(all_windows, dtype=np.float32)
            y_tensor = np.array(all_labels, dtype=np.int64)

            # Save clean NumPy tensors
            np.save(output_dir / f"X_{split_name}.npy", X_tensor)
            np.save(output_dir / f"y_{split_name}.npy", y_tensor)

            dataset_tensors[split_name] = (X_tensor, y_tensor)
            logger.info(
                f"[{split_name.upper()}] Tensor Shape: X={X_tensor.shape}, y={y_tensor.shape} | "
                f"Class counts: {np.bincount(y_tensor, minlength=3)}"
            )

        # Save metadata JSON
        metadata = {
            "window_size": self.window_size,
            "stride": self.stride,
            "overlap_percent": 50,
            "feature_dim": dataset_tensors["train"][0].shape[-1],
            "class_mapping": LABEL_TO_INT,
            "shapes": {
                "X_train": list(dataset_tensors["train"][0].shape),
                "y_train": list(dataset_tensors["train"][1].shape),
                "X_val": list(dataset_tensors["val"][0].shape),
                "y_val": list(dataset_tensors["val"][1].shape),
                "X_test": list(dataset_tensors["test"][0].shape),
                "y_test": list(dataset_tensors["test"][1].shape),
            },
            "augmentations_applied_to_train": {
                "rotation_y_deg": "+/- 10 degrees",
                "gaussian_noise_sigma": 0.015,
                "temporal_time_warp": "0.85x to 1.15x",
                "augment_multiplier": self.augment_multiplier
            }
        }
        with open(output_dir / "dataset_metadata.json", "w", encoding="utf-8") as f:
            json.dump(metadata, f, indent=2)

        logger.info("=" * 65)
        logger.info(f"Dataset tensors successfully saved to: {output_dir.resolve()}")
        logger.info("=" * 65)

        return dataset_tensors


def main():
    parser = argparse.ArgumentParser(
        description="Extract sliding windows, apply scale normalization & 3D augmentation, and split dataset."
    )
    parser.add_argument(
        "--engineered-dir",
        type=Path,
        default=Path("dataset_root/engineered_features_npz"),
        help="Directory with engineered feature .npz files."
    )
    parser.add_argument(
        "--landmarks-dir",
        type=Path,
        default=Path("dataset_root/extracted_landmarks_npz"),
        help="Directory with raw 3D landmark .npz files."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("dataset_root/processed_tensors"),
        help="Directory to save final X/y train, val, and test tensors."
    )
    parser.add_argument(
        "--window-size",
        type=int,
        default=30,
        help="Window frame length (default: 30)."
    )
    parser.add_argument(
        "--stride",
        type=int,
        default=15,
        help="Sliding window stride (default: 15 for 50% overlap)."
    )
    parser.add_argument(
        "--augment-multiplier",
        type=int,
        default=2,
        help="Number of augmented variants per training window (default: 2)."
    )

    args = parser.parse_args()

    pipeline = SlidingWindowPipeline(
        window_size=args.window_size,
        stride=args.stride,
        augment_multiplier=args.augment_multiplier
    )
    pipeline.process_and_split(
        engineered_dir=args.engineered_dir,
        landmarks_dir=args.landmarks_dir,
        output_dir=args.output_dir
    )


if __name__ == "__main__":
    main()
