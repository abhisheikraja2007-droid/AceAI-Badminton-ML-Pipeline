"""
High-Performance Pose Landmark Extraction Pipeline
===================================================
Uses MediaPipe Tasks (PoseLandmarker) in VIDEO running mode with Python
multiprocessing to process video clips listed in master manifest CSV.

Features:
- Extracts 33 normalized image landmarks (x, y, z, visibility) -> (T, 33, 4)
- Extracts 33 3D world landmarks in metric space (x, y, z) -> (T, 33, 3)
- Normalizes left-handed player clips by horizontally flipping frames prior to inference
- Multiprocessing worker pool with independent video session timestamping
- Exports compressed NumPy (.npz) archives for machine learning training
"""

import argparse
import logging
import multiprocessing as mp
import os
import time
import urllib.request
from functools import partial
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np
import pandas as pd

import mediapipe as mp_lib
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(processName)s: %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("PoseExtraction")

MODEL_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task"
NUM_KEYPOINTS = 33


def ensure_model_asset(model_path: Path) -> Path:
    """Verifies that the MediaPipe PoseLandmarker task file exists or downloads it."""
    if not model_path.is_file():
        logger.info(f"Downloading PoseLandmarker model task to: {model_path}...")
        model_path.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(MODEL_URL, str(model_path))
        logger.info(f"Model downloaded successfully ({model_path.stat().st_size} bytes).")
    return model_path


def process_video_clip(
    row_data: Tuple[str, str, str, float, str],
    model_path_str: str,
    output_dir_str: str
) -> Optional[Dict]:
    """
    Worker function executed in parallel across worker processes.
    
    Args:
        row_data: (clip_id, file_path, label, fps, dominant_hand)
        model_path_str: Absolute path to the .task model file
        output_dir_str: Destination directory for compressed .npz archives
        
    Returns:
        Summary dict of processed clip or None if failed.
    """
    clip_id, file_path_str, label, nominal_fps, dominant_hand = row_data
    video_path = Path(file_path_str)
    output_dir = Path(output_dir_str)

    out_npz_path = output_dir / f"{clip_id}.npz"
    if out_npz_path.is_file():
        return {
            "clip_id": clip_id,
            "status": "cached",
            "npz_path": str(out_npz_path)
        }

    if not video_path.is_file():
        logger.warning(f"Video file not found for {clip_id}: {video_path}")
        return None

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        logger.warning(f"Failed to open video: {video_path}")
        return None

    video_fps = cap.get(cv2.CAP_PROP_FPS)
    fps = video_fps if (video_fps and video_fps > 0) else nominal_fps
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    is_left_handed = str(dominant_hand).strip().lower() == "left"

    # Configure PoseLandmarker for this video session in VIDEO mode
    base_options = python.BaseOptions(model_asset_path=model_path_str)
    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
        output_segmentation_masks=False
    )

    norm_landmarks_list: List[np.ndarray] = []
    world_landmarks_list: List[np.ndarray] = []
    timestamps_list: List[int] = []

    frame_interval_ms = 1000.0 / fps

    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        frame_idx = 0
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                break

            # Standardize to right-handed coordinate space
            if is_left_handed:
                frame = cv2.flip(frame, 1)

            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp_lib.Image(
                image_format=mp_lib.ImageFormat.SRGB,
                data=rgb_frame
            )

            # Monotonically increasing timestamp in milliseconds
            timestamp_ms = int(round(frame_idx * frame_interval_ms))
            detection_result = landmarker.detect_for_video(mp_image, timestamp_ms)

            # Extract normalized image landmarks: (33, 4) -> [x, y, z, visibility]
            norm_frame = np.zeros((NUM_KEYPOINTS, 4), dtype=np.float32)
            if detection_result.pose_landmarks and len(detection_result.pose_landmarks) > 0:
                first_pose = detection_result.pose_landmarks[0]
                for k_idx, lm in enumerate(first_pose):
                    if k_idx < NUM_KEYPOINTS:
                        vis = getattr(lm, "visibility", 1.0)
                        norm_frame[k_idx] = [lm.x, lm.y, lm.z, vis if vis is not None else 1.0]

            # Extract 3D metric world landmarks: (33, 3) -> [x, y, z] in meters
            world_frame = np.zeros((NUM_KEYPOINTS, 3), dtype=np.float32)
            if detection_result.pose_world_landmarks and len(detection_result.pose_world_landmarks) > 0:
                first_world_pose = detection_result.pose_world_landmarks[0]
                for k_idx, wlm in enumerate(first_world_pose):
                    if k_idx < NUM_KEYPOINTS:
                        world_frame[k_idx] = [wlm.x, wlm.y, wlm.z]

            norm_landmarks_list.append(norm_frame)
            world_landmarks_list.append(world_frame)
            timestamps_list.append(timestamp_ms)

            frame_idx += 1

    cap.release()

    if len(norm_landmarks_list) == 0:
        logger.warning(f"No frames extracted from {video_path}")
        return None

    # Stack time-series arrays
    norm_landmarks_arr = np.stack(norm_landmarks_list, axis=0)   # (T, 33, 4)
    world_landmarks_arr = np.stack(world_landmarks_list, axis=0) # (T, 33, 3)
    timestamps_arr = np.array(timestamps_list, dtype=np.int64)   # (T,)

    # Save compressed .npz archive
    np.savez_compressed(
        out_npz_path,
        normalized_landmarks=norm_landmarks_arr,
        world_landmarks=world_landmarks_arr,
        timestamps_ms=timestamps_arr,
        clip_id=clip_id,
        label=label,
        fps=fps,
        dominant_hand=dominant_hand,
        is_flipped=is_left_handed,
        frame_count=len(norm_landmarks_list)
    )

    return {
        "clip_id": clip_id,
        "frames": len(norm_landmarks_list),
        "label": label,
        "is_flipped": is_left_handed,
        "npz_path": str(out_npz_path)
    }


def run_pipeline(
    manifest_csv: Path,
    output_dir: Path,
    model_path: Path,
    num_workers: Optional[int] = None
):
    """Orchestrates multiprocessing pool across all clips in manifest."""
    start_time = time.time()
    logger.info(f"Loading master manifest from: {manifest_csv}")
    df = pd.read_csv(manifest_csv)

    required_cols = {"clip_id", "file_path", "label", "fps", "dominant_hand"}
    if not required_cols.issubset(df.columns):
        raise ValueError(f"Manifest missing required columns: {required_cols - set(df.columns)}")

    output_dir.mkdir(parents=True, exist_ok=True)
    ensure_model_asset(model_path)

    records = [
        (
            str(row["clip_id"]),
            str(row["file_path"]),
            str(row["label"]),
            float(row["fps"]),
            str(row["dominant_hand"])
        )
        for _, row in df.iterrows()
    ]

    total_clips = len(records)
    workers = num_workers or max(1, min(mp.cpu_count() - 1, 8))
    logger.info(f"Starting multiprocessing extraction: {total_clips} clips across {workers} workers.")

    worker_fn = partial(
        process_video_clip,
        model_path_str=str(model_path.resolve()),
        output_dir_str=str(output_dir.resolve())
    )

    completed = 0
    with mp.Pool(processes=workers) as pool:
        for result in pool.imap_unordered(worker_fn, records, chunksize=2):
            if result:
                completed += 1
                if completed % 10 == 0 or completed == total_clips:
                    elapsed = time.time() - start_time
                    fps_rate = completed / elapsed if elapsed > 0 else 0
                    logger.info(
                        f"Progress: [{completed}/{total_clips}] clips processed "
                        f"({completed / total_clips * 100:.1f}%) | {fps_rate:.2f} clips/s"
                    )

    elapsed_total = time.time() - start_time
    logger.info("=" * 60)
    logger.info(f"Pose landmark extraction finished in {elapsed_total:.2f} seconds.")
    logger.info(f"Total .npz archives created: {completed}")
    logger.info(f"Destination folder: {output_dir.resolve()}")
    logger.info("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="High-performance MediaPipe Pose Landmark extractor with multiprocessing."
    )
    parser.add_argument(
        "--manifest-csv",
        type=Path,
        default=Path("dataset_root/master_manifest.csv"),
        help="Path to the master manifest CSV."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("dataset_root/extracted_landmarks_npz"),
        help="Directory to save extracted .npz files."
    )
    parser.add_argument(
        "--model-path",
        type=Path,
        default=Path("models/pose_landmarker_heavy.task"),
        help="Path to pose_landmarker_heavy.task model file."
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=None,
        help="Number of parallel worker processes (defaults to CPU cores - 1)."
    )

    args = parser.parse_args()

    run_pipeline(
        manifest_csv=args.manifest_csv,
        output_dir=args.output_dir,
        model_path=args.model_path,
        num_workers=args.workers
    )


if __name__ == "__main__":
    mp.freeze_support()
    main()

