"""
Biomechanical Feature Engineering Module for Badminton Pose Sequences
======================================================================
Computes frame-by-frame biomechanical derivatives from 33-landmark 3D pose sequences:
1. 3D Euclidean displacement and instantaneous velocity vectors (m/s and km/h) for
   dominant shoulder (12), elbow (14), wrist (16), and index finger (20).
2. 3D trigonometric vector dot-product angles for elbow extension (12-14-16),
   shoulder abduction/elevation (24-12-14), and hip-shoulder rotational separation.
3. Vertical wrist height relative to nose (0) throughout the stroke and at peak velocity.
4. Savitzky-Golay temporal smoothing to eliminate single-frame MediaPipe jitter.
"""

import argparse
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union

import numpy as np
from scipy.signal import savgol_filter

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("BiomechanicsEngine")

# MediaPipe Pose Landmark Indices
LM_NOSE = 0
LM_LEFT_SHOULDER = 11
LM_RIGHT_SHOULDER = 12
LM_LEFT_ELBOW = 13
LM_RIGHT_ELBOW = 14
LM_LEFT_WRIST = 15
LM_RIGHT_WRIST = 16
LM_LEFT_INDEX = 19
LM_RIGHT_INDEX = 20
LM_LEFT_HIP = 23
LM_RIGHT_HIP = 24

EPSILON = 1e-7


@dataclass
class BiomechanicalFeatures:
    """Container for computed frame-by-frame and summary biomechanical metrics."""
    feature_matrix: np.ndarray           # (T, num_features) concatenated feature vector
    feature_names: List[str]            # Descriptive names for all feature dimensions
    velocities_mps: Dict[str, np.ndarray] # Per-joint velocity vectors (T, 3) in m/s
    speeds_mps: Dict[str, np.ndarray]     # Per-joint speed (T,) in m/s
    speeds_kmh: Dict[str, np.ndarray]     # Per-joint speed (T,) in km/h
    joint_angles_deg: Dict[str, np.ndarray] # Joint angles (T,) in degrees
    wrist_nose_height_m: np.ndarray      # Vertical wrist height relative to nose (T,) in meters
    peak_wrist_speed_mps: float          # Peak wrist speed in m/s
    peak_wrist_speed_kmh: float          # Peak wrist speed in km/h
    peak_wrist_frame: int                # Frame index of peak wrist speed
    wrist_height_at_peak_m: float        # Vertical wrist height at peak velocity in meters


class BiomechanicalFeatureEngine:
    """High-performance biomechanical derivative calculator using NumPy and SciPy."""

    def __init__(self, savgol_window: int = 7, savgol_polyorder: int = 2):
        """
        Args:
            savgol_window: Odd integer window length for Savitzky-Golay filter.
                           7 frames at 60 FPS corresponds to ~116ms, ideal for
                           eliminating jitter while preserving 150ms smash impact peaks.
            savgol_polyorder: Polynomial order for Savitzky-Golay smoothing.
        """
        self.savgol_window = savgol_window
        self.savgol_polyorder = savgol_polyorder

    def apply_savgol_smoothing(self, coordinates: np.ndarray) -> np.ndarray:
        """
        Applies Savitzky-Golay smoothing across the temporal axis (axis 0).
        Handles short sequence lengths gracefully.
        """
        T = coordinates.shape[0]
        # Window length must be an odd integer <= T
        window = self.savgol_window
        if window >= T:
            window = T - 1 if (T % 2 == 0) else T
        if window < 3:
            return coordinates.copy()
        if window % 2 == 0:
            window -= 1

        polyorder = min(self.savgol_polyorder, window - 1)
        return savgol_filter(coordinates, window_length=window, polyorder=polyorder, axis=0)

    @staticmethod
    def compute_joint_kinematics(
        coords: np.ndarray,
        timestamps_sec: np.ndarray
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """
        Calculates 3D spatial displacement and instantaneous velocity vectors.
        
        Args:
            coords: Joint coordinates (T, 3) in meters
            timestamps_sec: Time in seconds (T,)
            
        Returns:
            displacement_vectors: (T, 3) spatial step vectors (p[t] - p[t-1])
            velocity_vectors: (T, 3) instantaneous velocity in m/s
            speeds_mps: (T,) speed magnitude in m/s
            speeds_kmh: (T,) speed magnitude in km/h
        """
        T = coords.shape[0]
        displacement = np.zeros_like(coords)
        if T > 1:
            displacement[1:] = coords[1:] - coords[:-1]

        # Numerical gradient with respect to actual time intervals
        # Handles variable or fixed frame intervals seamlessly
        if len(np.unique(timestamps_sec)) > 1:
            velocity_mps = np.gradient(coords, timestamps_sec, axis=0)
        else:
            # Fallback for uniform 60 FPS (1/60s)
            dt = 1.0 / 60.0
            velocity_mps = np.gradient(coords, dt, axis=0)

        speeds_mps = np.linalg.norm(velocity_mps, axis=-1)
        speeds_kmh = speeds_mps * 3.6

        return displacement, velocity_mps, speeds_mps, speeds_kmh

    @staticmethod
    def compute_angle_3d(pointA: np.ndarray, vertexB: np.ndarray, pointC: np.ndarray) -> np.ndarray:
        """
        Computes 3D trigonometric dot-product angle in degrees at vertex B.
        v_BA = A - B, v_BC = C - B
        angle = arccos((v_BA . v_BC) / (|v_BA| * |v_BC|))
        """
        v_BA = pointA - vertexB
        v_BC = pointC - vertexB

        dot_product = np.sum(v_BA * v_BC, axis=-1)
        norm_BA = np.linalg.norm(v_BA, axis=-1)
        norm_BC = np.linalg.norm(v_BC, axis=-1)

        denominator = (norm_BA * norm_BC) + EPSILON
        cos_theta = np.clip(dot_product / denominator, -1.0, 1.0)
        return np.degrees(np.arccos(cos_theta))

    @staticmethod
    def compute_hip_shoulder_separation(
        left_shoulder: np.ndarray,
        right_shoulder: np.ndarray,
        left_hip: np.ndarray,
        right_hip: np.ndarray
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        Calculates torso pelvic-shoulder separation (X-Factor):
        1. 3D spatial separation angle between shoulder and hip lines.
        2. Transverse/horizontal plane projection angle (X-Z plane).
        """
        # Shoulder vector (left to right)
        v_shoulder_3d = right_shoulder - left_shoulder
        # Hip vector (left to right)
        v_hip_3d = right_hip - left_hip

        # 3D Angle
        dot_3d = np.sum(v_shoulder_3d * v_hip_3d, axis=-1)
        norm_s3d = np.linalg.norm(v_shoulder_3d, axis=-1)
        norm_h3d = np.linalg.norm(v_hip_3d, axis=-1)
        cos_3d = np.clip(dot_3d / ((norm_s3d * norm_h3d) + EPSILON), -1.0, 1.0)
        angle_3d = np.degrees(np.arccos(cos_3d))

        # Transverse (horizontal) projection: X (lateral) and Z (depth) axes in MediaPipe
        v_shoulder_trans = v_shoulder_3d[:, [0, 2]]
        v_hip_trans = v_hip_3d[:, [0, 2]]

        dot_trans = np.sum(v_shoulder_trans * v_hip_trans, axis=-1)
        norm_strans = np.linalg.norm(v_shoulder_trans, axis=-1)
        norm_htrans = np.linalg.norm(v_hip_trans, axis=-1)
        cos_trans = np.clip(dot_trans / ((norm_strans * norm_htrans) + EPSILON), -1.0, 1.0)
        angle_transverse = np.degrees(np.arccos(cos_trans))

        return angle_3d, angle_transverse

    def process_sequence(
        self,
        world_landmarks: np.ndarray,
        timestamps_ms: np.ndarray,
        normalized_landmarks: Optional[np.ndarray] = None
    ) -> BiomechanicalFeatures:
        """
        Computes all frame-by-frame biomechanical derivatives.

        Args:
            world_landmarks: (T, 33, 3) 3D keypoint coordinates in meters
            timestamps_ms: (T,) frame timestamps in milliseconds
            normalized_landmarks: Optional (T, 33, 4) normalized image coordinates
            
        Returns:
            BiomechanicalFeatures dataclass containing feature matrices and scalar metrics.
        """
        T = world_landmarks.shape[0]
        timestamps_sec = timestamps_ms.astype(np.float64) / 1000.0

        # Step 0: Savitzky-Golay temporal smoothing to eliminate 1-frame jitter
        smoothed_world = self.apply_savgol_smoothing(world_landmarks)

        # Dominant side keypoints (Right side, since left-handers were pre-flipped)
        shoulder_12 = smoothed_world[:, LM_RIGHT_SHOULDER, :]
        elbow_14 = smoothed_world[:, LM_RIGHT_ELBOW, :]
        wrist_16 = smoothed_world[:, LM_RIGHT_WRIST, :]
        index_20 = smoothed_world[:, LM_RIGHT_INDEX, :]
        hip_24 = smoothed_world[:, LM_RIGHT_HIP, :]

        # Non-dominant side keypoints for torso alignment
        shoulder_11 = smoothed_world[:, LM_LEFT_SHOULDER, :]
        hip_23 = smoothed_world[:, LM_LEFT_HIP, :]
        nose_0 = smoothed_world[:, LM_NOSE, :]

        # Step 1: Kinematics (Displacement & Instantaneous Velocity)
        joints_to_track = {
            "shoulder": shoulder_12,
            "elbow": elbow_14,
            "wrist": wrist_16,
            "index": index_20
        }

        displacements = {}
        velocities_mps = {}
        speeds_mps = {}
        speeds_kmh = {}

        for j_name, j_coords in joints_to_track.items():
            disp, vel, smps, skmh = self.compute_joint_kinematics(j_coords, timestamps_sec)
            displacements[j_name] = disp
            velocities_mps[j_name] = vel
            speeds_mps[j_name] = smps
            speeds_kmh[j_name] = skmh

        # Step 2: 3D Trigonometric Joint Angles
        # Dominant Elbow Extension: angle at elbow (14) between 14->12 and 14->16
        elbow_extension = self.compute_angle_3d(shoulder_12, elbow_14, wrist_16)

        # Dominant Shoulder Abduction/Elevation: angle at shoulder (12) between 12->24 and 12->14
        shoulder_abduction = self.compute_angle_3d(hip_24, shoulder_12, elbow_14)

        # Torso Pelvic-Shoulder Separation (X-Factor)
        torso_sep_3d, torso_sep_transverse = self.compute_hip_shoulder_separation(
            shoulder_11, shoulder_12, hip_23, hip_24
        )

        joint_angles = {
            "elbow_extension_deg": elbow_extension,
            "shoulder_abduction_deg": shoulder_abduction,
            "torso_separation_3d_deg": torso_sep_3d,
            "torso_separation_transverse_deg": torso_sep_transverse
        }

        # Step 3: Vertical Wrist Height Relative to Nose
        # In MediaPipe world coordinates: +Y points downward.
        # Height above nose = y_nose - y_wrist (positive when wrist is elevated above nose)
        wrist_nose_height_m = nose_0[:, 1] - wrist_16[:, 1]

        # Metrics at Peak Wrist Velocity (Impact Window Analysis)
        wrist_speed_series = speeds_mps["wrist"]
        peak_frame = int(np.argmax(wrist_speed_series))
        peak_wrist_speed_mps = float(wrist_speed_series[peak_frame])
        peak_wrist_speed_kmh = float(speeds_kmh["wrist"][peak_frame])
        wrist_height_at_peak_m = float(wrist_nose_height_m[peak_frame])

        # Step 4: Assemble Frame-by-Frame Feature Vector
        feature_columns = []
        feature_names = []

        # 4a. Base 3D World Landmarks flattened (T, 99)
        feature_columns.append(smoothed_world.reshape(T, -1))
        for k in range(33):
            for ax in ["x", "y", "z"]:
                feature_names.append(f"world_lm_{k}_{ax}")

        # 4b. Joint Kinematics: Displacement (3), Velocity (3), Speed m/s (1), Speed km/h (1)
        for j_name in ["shoulder", "elbow", "wrist", "index"]:
            feature_columns.append(displacements[j_name])
            feature_names.extend([f"{j_name}_disp_x", f"{j_name}_disp_y", f"{j_name}_disp_z"])

            feature_columns.append(velocities_mps[j_name])
            feature_names.extend([f"{j_name}_vel_x_mps", f"{j_name}_vel_y_mps", f"{j_name}_vel_z_mps"])

            feature_columns.append(speeds_mps[j_name][:, np.newaxis])
            feature_names.append(f"{j_name}_speed_mps")

            feature_columns.append(speeds_kmh[j_name][:, np.newaxis])
            feature_names.append(f"{j_name}_speed_kmh")

        # 4c. Joint Angles (4 features)
        for a_name in ["elbow_extension_deg", "shoulder_abduction_deg", "torso_separation_3d_deg", "torso_separation_transverse_deg"]:
            feature_columns.append(joint_angles[a_name][:, np.newaxis])
            feature_names.append(a_name)

        # 4d. Vertical Wrist Height relative to nose (2 features: time series + peak value broadcast)
        feature_columns.append(wrist_nose_height_m[:, np.newaxis])
        feature_names.append("wrist_nose_height_m")

        peak_val_broadcast = np.full((T, 1), wrist_height_at_peak_m, dtype=np.float32)
        feature_columns.append(peak_val_broadcast)
        feature_names.append("wrist_height_at_peak_velocity_m")

        # 4e. Normalized screen coordinates if available (T, 132)
        if normalized_landmarks is not None and normalized_landmarks.shape[0] == T:
            feature_columns.append(normalized_landmarks.reshape(T, -1))
            for k in range(33):
                for ax in ["x", "y", "z", "vis"]:
                    feature_names.append(f"norm_lm_{k}_{ax}")

        feature_matrix = np.hstack(feature_columns).astype(np.float32)

        return BiomechanicalFeatures(
            feature_matrix=feature_matrix,
            feature_names=feature_names,
            velocities_mps=velocities_mps,
            speeds_mps=speeds_mps,
            speeds_kmh=speeds_kmh,
            joint_angles_deg=joint_angles,
            wrist_nose_height_m=wrist_nose_height_m,
            peak_wrist_speed_mps=peak_wrist_speed_mps,
            peak_wrist_speed_kmh=peak_wrist_speed_kmh,
            peak_wrist_frame=peak_frame,
            wrist_height_at_peak_m=wrist_height_at_peak_m
        )


def process_all_npz_sequences(
    input_dir: Path,
    output_dir: Path,
    savgol_window: int = 7,
    savgol_polyorder: int = 2
):
    """Batch processes all extracted landmark .npz files into engineered feature archives."""
    engine = BiomechanicalFeatureEngine(savgol_window=savgol_window, savgol_polyorder=savgol_polyorder)
    output_dir.mkdir(parents=True, exist_ok=True)

    npz_files = sorted(input_dir.glob("*.npz"))
    if not npz_files:
        logger.warning(f"No .npz files found in {input_dir}")
        return

    logger.info(f"Computing biomechanical derivatives for {len(npz_files)} sequences...")

    summary_records = []

    for idx, npz_path in enumerate(npz_files, 1):
        data = np.load(npz_path, allow_pickle=True)
        world_landmarks = data["world_landmarks"]
        timestamps_ms = data["timestamps_ms"]
        norm_landmarks = data["normalized_landmarks"] if "normalized_landmarks" in data else None
        clip_id = str(data["clip_id"])
        label = str(data["label"])

        bio = engine.process_sequence(
            world_landmarks=world_landmarks,
            timestamps_ms=timestamps_ms,
            normalized_landmarks=norm_landmarks
        )

        out_file = output_dir / f"{clip_id}_engineered.npz"
        np.savez_compressed(
            out_file,
            feature_matrix=bio.feature_matrix,
            feature_names=np.array(bio.feature_names),
            wrist_speed_mps=bio.speeds_mps["wrist"],
            wrist_speed_kmh=bio.speeds_kmh["wrist"],
            elbow_extension_deg=bio.joint_angles_deg["elbow_extension_deg"],
            shoulder_abduction_deg=bio.joint_angles_deg["shoulder_abduction_deg"],
            torso_separation_deg=bio.joint_angles_deg["torso_separation_3d_deg"],
            wrist_nose_height_m=bio.wrist_nose_height_m,
            peak_wrist_speed_mps=bio.peak_wrist_speed_mps,
            peak_wrist_speed_kmh=bio.peak_wrist_speed_kmh,
            peak_wrist_frame=bio.peak_wrist_frame,
            wrist_height_at_peak_m=bio.wrist_height_at_peak_m,
            clip_id=clip_id,
            label=label
        )

        summary_records.append({
            "clip_id": clip_id,
            "label": label,
            "peak_wrist_speed_kmh": round(bio.peak_wrist_speed_kmh, 2),
            "peak_frame": bio.peak_wrist_frame,
            "wrist_height_at_peak_m": round(bio.wrist_height_at_peak_m, 3),
            "max_elbow_extension_deg": round(float(np.max(bio.joint_angles_deg["elbow_extension_deg"])), 1)
        })

    logger.info("=" * 65)
    logger.info(f"Engineered features successfully computed for {len(summary_records)} clips.")
    logger.info(f"Feature matrix dimension per clip: {bio.feature_matrix.shape}")
    logger.info(f"Output directory: {output_dir.resolve()}")
    logger.info("=" * 65)


def main():
    parser = argparse.ArgumentParser(
        description="Compute frame-by-frame biomechanical derivatives from 33-landmark pose sequences."
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("dataset_root/extracted_landmarks_npz"),
        help="Directory containing 33-landmark .npz sequences."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("dataset_root/engineered_features_npz"),
        help="Directory to save engineered biomechanical .npz archives."
    )
    parser.add_argument(
        "--savgol-window",
        type=int,
        default=7,
        help="Savitzky-Golay filter window length (must be odd, default: 7)."
    )
    parser.add_argument(
        "--savgol-poly",
        type=int,
        default=2,
        help="Savitzky-Golay polynomial order (default: 2)."
    )

    args = parser.parse_args()

    process_all_npz_sequences(
        input_dir=args.input_dir,
        output_dir=args.output_dir,
        savgol_window=args.savgol_window,
        savgol_polyorder=args.savgol_poly
    )


if __name__ == "__main__":
    main()

