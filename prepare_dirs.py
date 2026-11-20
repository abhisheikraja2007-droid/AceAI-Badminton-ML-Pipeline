import os
import sys
import json
import subprocess
from pathlib import Path
import cv2
import numpy as np

BASE_DIR = Path(r"D:\projects\Design thinking rework\dataset_root")
RAW_DIR = BASE_DIR / "raw"
SHUTTLESET_DIR = RAW_DIR / "shuttleset"
VIDEOBADMINTON_DIR = RAW_DIR / "videobadminton"
TRACKNET_DIR = RAW_DIR / "tracknetv3"
CALIB_DIR = RAW_DIR / "smartphone_calibration"
PROCESSED_DIR = BASE_DIR / "processed_60fps"
ANNOTATIONS_DIR = BASE_DIR / "annotations"

for d in [SHUTTLESET_DIR, VIDEOBADMINTON_DIR, TRACKNET_DIR, CALIB_DIR, PROCESSED_DIR, ANNOTATIONS_DIR]:
    d.mkdir(parents=True, exist_ok=True)

print("Dataset folders successfully created.")

