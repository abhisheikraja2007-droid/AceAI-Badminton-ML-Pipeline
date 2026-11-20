"""
Modular Badminton Video Dataset Standardization Pipeline
=========================================================
Standardizes multi-source badminton video footage (ShuttleSet, CoachAI, and Custom Clips)
into centered 1.5-second stroke/footwork clips with validated frame integrity and
generates a master metadata manifest CSV.

Target Classes:
- "overhead_smash"
- "other_stroke" (clear, drop, drive, push, net kill, etc.)
- "idle_footwork" (footwork, split-step, preparation, idle recovery)

Output Manifest Columns:
clip_id, file_path, label, fps, dominant_hand
"""

import argparse
import logging
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import cv2
import pandas as pd

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("DatasetStandardizer")


class LabelMapper:
    """Maps varied multi-source stroke labels into three canonical target classes."""

    SMASH_SYNONYMS = {
        "smash", "overhead smash", "overhead_smash", "jump smash",
        "forehand smash", "backhand smash", "steep smash"
    }

    OTHER_STROKE_SYNONYMS = {
        "clear", "overhead clear", "overhead_clear", "attacking clear",
        "drop", "overhead drop", "overhead_drop", "slice drop", "reverse drop",
        "drive", "push", "net shot", "net kill", "net lift", "lift", "lob",
        "defensive return", "block", "hairpin"
    }

    IDLE_FOOTWORK_SYNONYMS = {
        "idle", "footwork", "court_footwork", "idle_footwork",
        "split_step", "split step", "recovery", "ready_stance", "shadow"
    }

    @classmethod
    def map_label(cls, raw_label: Optional[str]) -> Optional[str]:
        """Normalizes and maps raw string label to one of the 3 target classes."""
        if not raw_label or pd.isna(raw_label):
            return None

        cleaned = str(raw_label).strip().lower().replace("-", "_")

        if any(smash_term in cleaned for smash_term in cls.SMASH_SYNONYMS):
            return "overhead_smash"
        if any(other_term in cleaned for other_term in cls.OTHER_STROKE_SYNONYMS):
            return "other_stroke"
        if any(fw_term in cleaned for fw_term in cls.IDLE_FOOTWORK_SYNONYMS):
            return "idle_footwork"

        # Default fallback for unrecognized non-idle strokes
        return "other_stroke"


class VideoIntegrityValidator:
    """Validates video decodability, frame counts, and sliced clip integrity."""

    @staticmethod
    def get_video_properties(video_path: Path) -> Optional[Tuple[float, int, int, int]]:
        """
        Extracts (fps, frame_count, width, height) and verifies decodability.
        Returns None if video cannot be opened or is corrupted.
        """
        if not video_path.is_file():
            logger.warning(f"File not found: {video_path}")
            return None

        cap = cv2.VideoCapture(str(video_path))
        if not cap.isOpened():
            logger.warning(f"Unable to open video: {video_path}")
            cap.release()
            return None

        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        cap.release()

        if total_frames <= 0 or width <= 0 or height <= 0:
            logger.warning(f"Invalid stream dimensions or zero frames in: {video_path}")
            return None

        return (fps, total_frames, width, height)

    @staticmethod
    def verify_sliced_clip(clip_path: Path, expected_frames: int) -> bool:
        """Ensures sliced clip was written successfully with exact frame decodability."""
        cap = cv2.VideoCapture(str(clip_path))
        if not cap.isOpened():
            return False

        decoded_count = 0
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                break
            decoded_count += 1

        cap.release()
        is_valid = decoded_count == expected_frames
        if not is_valid:
            logger.warning(
                f"Integrity check failed for {clip_path.name}: "
                f"expected {expected_frames} frames, decoded {decoded_count}."
            )
        return is_valid


class VideoSlicer:
    """Extracts 1.5-second clips centered around the shuttlecock impact frame."""

    CLIP_DURATION_SEC = 1.5

    @classmethod
    def calculate_window(
        cls, impact_frame: int, total_frames: int, fps: float
    ) -> Tuple[int, int, int]:
        """
        Calculates window: 45 frames for 30 FPS, 90 frames for 60 FPS.
        Returns (start_frame, end_frame, target_window_frames).
        """
        # 45 frames @ 30 FPS, 90 frames @ 60 FPS
        window_size = int(round(cls.CLIP_DURATION_SEC * fps))
        half_window = window_size // 2

        start_frame = impact_frame - half_window
        end_frame = start_frame + window_size

        # Clamp boundaries while preserving window_size whenever possible
        if start_frame < 0:
            start_frame = 0
            end_frame = min(window_size, total_frames)
        elif end_frame > total_frames:
            end_frame = total_frames
            start_frame = max(0, total_frames - window_size)

        actual_frames = end_frame - start_frame
        return start_frame, end_frame, actual_frames

    @classmethod
    def slice_clip(
        cls,
        src_video_path: Path,
        dst_clip_path: Path,
        impact_frame: int,
        fps: float,
        fourcc_str: str = "mp4v"
    ) -> bool:
        """
        Reads source video, slices exact 1.5s window around impact_frame,
        writes output clip, and performs frame integrity validation.
        """
        cap = cv2.VideoCapture(str(src_video_path))
        if not cap.isOpened():
            return False

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

        start_frame, end_frame, expected_window = cls.calculate_window(
            impact_frame, total_frames, fps
        )

        cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
        dst_clip_path.parent.mkdir(parents=True, exist_ok=True)

        fourcc = cv2.VideoWriter_fourcc(*fourcc_str)
        out = cv2.VideoWriter(str(dst_clip_path), fourcc, fps, (width, height))

        frames_written = 0
        current_idx = start_frame

        while current_idx < end_frame:
            ret, frame = cap.read()
            if not ret or frame is None:
                break
            out.write(frame)
            frames_written += 1
            current_idx += 1

        cap.release()
        out.release()

        # Validate integrity of written clip
        if not VideoIntegrityValidator.verify_sliced_clip(dst_clip_path, frames_written):
            if dst_clip_path.exists():
                dst_clip_path.unlink()
            return False

        return True


class ShuttleSetIngestor:
    """Ingests raw ShuttleSet / CoachAI video and CSV stroke annotations."""

    @staticmethod
    def parse_annotations(csv_path: Path, video_dir: Path) -> List[Dict]:
        """
        Parses ShuttleSet CSV format. Looks for common column permutations:
        - Video: 'video_id', 'match_id', 'video'
        - Impact: 'hit_frame', 'frame_num', 'timestamp'
        - Stroke: 'type', 'stroke', 'ball_type'
        - Dominant Hand: 'dominant_hand', 'hand', or defaults to 'right'
        """
        records = []
        if not csv_path.is_file():
            logger.warning(f"ShuttleSet CSV not found: {csv_path}")
            return records

        df = pd.read_csv(csv_path)
        # Normalize column names for flexible parsing
        col_map = {c.strip().lower(): c for c in df.columns}

        vid_col = col_map.get("video_id") or col_map.get("match_id") or col_map.get("video")
        frame_col = col_map.get("hit_frame") or col_map.get("frame_num") or col_map.get("frame")
        type_col = col_map.get("type") or col_map.get("stroke") or col_map.get("ball_type")
        hand_col = col_map.get("dominant_hand") or col_map.get("hand")

        for idx, row in df.iterrows():
            raw_stroke = row[type_col] if type_col else None
            label = LabelMapper.map_label(raw_stroke)
            if not label:
                continue

            # Locate video file
            raw_vid = str(row[vid_col]) if vid_col else None
            matched_vid = None
            if raw_vid:
                candidates = [
                    video_dir / raw_vid,
                    video_dir / f"{raw_vid}.mp4",
                    video_dir / f"{raw_vid}.mov"
                ]
                for c in candidates:
                    if c.is_file():
                        matched_vid = c
                        break

            impact_frame = int(row[frame_col]) if frame_col and pd.notna(row[frame_col]) else None
            dominant_hand = str(row[hand_col]).strip().lower() if hand_col and pd.notna(row[hand_col]) else "right"
            if dominant_hand not in {"right", "left"}:
                dominant_hand = "right"

            records.append({
                "source": "shuttleset",
                "video_path": matched_vid,
                "impact_frame": impact_frame,
                "label": label,
                "dominant_hand": dominant_hand,
                "raw_id": f"shuttle_{idx}"
            })

        return records


class CustomClipsIngestor:
    """Ingests custom court-side and smartphone video clips from custom_clips directory."""

    @staticmethod
    def parse_directory(custom_dir: Path) -> List[Dict]:
        """
        Scans custom_clips directory. If an annotations.csv is present, uses it;
        otherwise infers impact center frame from video midpoint.
        """
        records = []
        if not custom_dir.is_dir():
            logger.warning(f"Custom clips directory not found: {custom_dir}")
            return records

        csv_file = custom_dir / "annotations.csv"
        annot_df = pd.read_csv(csv_file) if csv_file.is_file() else None

        for vid_path in custom_dir.glob("*.mp4"):
            stem = vid_path.stem
            impact_frame = None
            raw_label = None
            dominant_hand = "right"

            # Check CSV annotations if available
            if annot_df is not None:
                matches = annot_df[annot_df["filename"] == vid_path.name]
                if not matches.empty:
                    row = matches.iloc[0]
                    impact_frame = int(row.get("impact_frame", 0)) if "impact_frame" in row else None
                    raw_label = row.get("label", None)
                    dominant_hand = str(row.get("dominant_hand", "right")).lower()

            # Fallback to filename parsing (e.g. phone_calib_01_Overhead_Smash.mp4)
            if not raw_label:
                raw_label = stem

            label = LabelMapper.map_label(raw_label)
            if not label:
                label = "other_stroke"

            records.append({
                "source": "custom_clips",
                "video_path": vid_path,
                "impact_frame": impact_frame,
                "label": label,
                "dominant_hand": dominant_hand,
                "raw_id": f"custom_{stem}"
            })

        return records


class DatasetStandardizationPipeline:
    """Main orchestrator for multi-source standardization and manifest generation."""

    def __init__(
        self,
        shuttleset_video_dir: Path,
        shuttleset_csv_path: Optional[Path],
        custom_clips_dir: Path,
        output_clips_dir: Path,
        manifest_csv_path: Path
    ):
        self.shuttleset_video_dir = shuttleset_video_dir
        self.shuttleset_csv_path = shuttleset_csv_path
        self.custom_clips_dir = custom_clips_dir
        self.output_clips_dir = output_clips_dir
        self.manifest_csv_path = manifest_csv_path

    def run(self) -> pd.DataFrame:
        """Executes full standardization, slicing, validation, and manifest creation."""
        logger.info("Initializing multi-source standardization pipeline...")
        self.output_clips_dir.mkdir(parents=True, exist_ok=True)
        self.manifest_csv_path.parent.mkdir(parents=True, exist_ok=True)

        all_candidates: List[Dict] = []

        # 1. Ingest ShuttleSet
        if self.shuttleset_csv_path and self.shuttleset_csv_path.is_file():
            logger.info("Ingesting ShuttleSet annotations...")
            shuttle_records = ShuttleSetIngestor.parse_annotations(
                self.shuttleset_csv_path, self.shuttleset_video_dir
            )
            all_candidates.extend(shuttle_records)
            logger.info(f"Loaded {len(shuttle_records)} ShuttleSet records.")

        # 2. Ingest Custom Clips
        logger.info("Ingesting custom clips...")
        custom_records = CustomClipsIngestor.parse_directory(self.custom_clips_dir)
        all_candidates.extend(custom_records)
        logger.info(f"Loaded {len(custom_records)} custom clips.")

        manifest_rows: List[Dict] = []
        clip_index = 1

        # 3. Process, Slice, and Validate Clips
        for item in all_candidates:
            src_video: Optional[Path] = item["video_path"]
            if not src_video or not src_video.is_file():
                continue

            props = VideoIntegrityValidator.get_video_properties(src_video)
            if not props:
                continue

            fps, total_frames, _, _ = props
            # Standardize FPS reporting to integer bucket (30 or 60)
            nominal_fps = 60 if round(fps) >= 45 else 30

            # Determine impact frame (default to midpoint if unannotated)
            impact_frame = item.get("impact_frame")
            if impact_frame is None or impact_frame <= 0 or impact_frame >= total_frames:
                impact_frame = total_frames // 2

            clip_id = f"clip_{clip_index:05d}"
            label = item["label"]
            out_filename = f"{clip_id}_{label}.mp4"
            out_clip_path = self.output_clips_dir / out_filename

            # Slice and validate
            success = VideoSlicer.slice_clip(
                src_video_path=src_video,
                dst_clip_path=out_clip_path,
                impact_frame=impact_frame,
                fps=fps
            )

            if success:
                manifest_rows.append({
                    "clip_id": clip_id,
                    "file_path": str(out_clip_path.resolve()),
                    "label": label,
                    "fps": nominal_fps,
                    "dominant_hand": item.get("dominant_hand", "right")
                })
                clip_index += 1

        # 4. Generate Master Metadata Manifest CSV
        manifest_df = pd.DataFrame(manifest_rows, columns=[
            "clip_id", "file_path", "label", "fps", "dominant_hand"
        ])
        manifest_df.to_csv(self.manifest_csv_path, index=False)

        logger.info("=" * 60)
        logger.info(f"Standardization Complete.")
        logger.info(f"Total Validated Clips Sliced: {len(manifest_df)}")
        logger.info(f"Class Distribution:\n{manifest_df['label'].value_counts().to_string()}")
        logger.info(f"Master Manifest Saved: {self.manifest_csv_path.resolve()}")
        logger.info("=" * 60)

        return manifest_df


def main():
    parser = argparse.ArgumentParser(
        description="Standardize badminton dataset into 3 target classes and 1.5s clips."
    )
    parser.add_argument(
        "--shuttleset-dir",
        type=Path,
        default=Path("dataset_root/raw/shuttleset"),
        help="Directory containing raw ShuttleSet video files."
    )
    parser.add_argument(
        "--shuttleset-csv",
        type=Path,
        default=Path("dataset_root/raw/shuttleset/annotations.csv"),
        help="Path to ShuttleSet CSV annotation file."
    )
    parser.add_argument(
        "--custom-clips-dir",
        type=Path,
        default=Path("dataset_root/raw/smartphone_calibration"),
        help="Directory containing custom court-level clips."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("dataset_root/standardized_clips_1.5s"),
        help="Directory to store sliced 1.5-second clips."
    )
    parser.add_argument(
        "--manifest-csv",
        type=Path,
        default=Path("dataset_root/master_manifest.csv"),
        help="Path to output master metadata manifest CSV."
    )

    args = parser.parse_args()

    pipeline = DatasetStandardizationPipeline(
        shuttleset_video_dir=args.shuttleset_dir,
        shuttleset_csv_path=args.shuttleset_csv,
        custom_clips_dir=args.custom_clips_dir,
        output_clips_dir=args.output_dir,
        manifest_csv_path=args.manifest_csv
    )
    pipeline.run()


if __name__ == "__main__":
    main()

