# 🏸 AceAI: Autonomous Badminton Stroke Classification & Biomechanical Edge Pipeline

[![Python](https://img.shields.io/badge/Python-3.10%2B-blue?logo=python&logoColor=white)](https://www.python.org/)
[![TensorFlow](https://img.shields.io/badge/TensorFlow-2.16%2B-orange?logo=tensorflow&logoColor=white)](https://tensorflow.org/)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-Pose%20Landmarker-teal?logo=google&logoColor=white)](https://developers.google.com/mediapipe)
[![TFLite](https://img.shields.io/badge/TFLite-Pure%20FP16-green?logo=tensorflow&logoColor=white)](https://tensorflow.org/lite)
[![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-34A853?logo=android&logoColor=white)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

An end-to-end edge-AI biomechanics and stroke recognition engine designed to deliver elite-level coaching analytics directly on mobile hardware in real time. **AceAI** combines 3D kinematic pose extraction, Savitzky-Golay temporal smoothing, kinetic chain angular derivations, and a mobile-optimized **Hybrid 1D-CNN + Bidirectional LSTM + Multi-Head Temporal Attention** deep neural network unrolled and quantized to a compact **1.0 MB FP16 TFLite model** operating with **< 5ms on-device latency**.

---

## 🏛 Architecture Overview

```
                                      ┌─────────────────────────────────────────────────────────┐
                                      │              Video / CameraX Ingestion                  │
                                      │              30 FPS Monocular 1080p                     │
                                      └──────────────────────────┬──────────────────────────────┘
                                                                 │
                                                                 ▼
                                      ┌─────────────────────────────────────────────────────────┐
                                      │             MediaPipe Pose Landmarker                   │
                                      │             33 Keypoints (x, y, z, visibility)          │
                                      └──────────────────────────┬──────────────────────────────┘
                                                                 │
                                                                 ▼
                                      ┌─────────────────────────────────────────────────────────┐
                                      │          Biomechanical Feature Engine                   │
                                      │  • Savitzky-Golay Smoothing (Window=7, Poly=2)          │
                                      │  • Scale-Invariant Torso Normalization                  │
                                      │  • 3D Angular Kinematics (Elbow, Shoulder, Trunk)       │
                                      │  • Hip-Shoulder Rotational Separation Δθ                │
                                      │  • Angular Velocity & Kinetic Chain Accelerations       │
                                      └──────────────────────────┬──────────────────────────────┘
                                                                 │
                                                                 ▼
                                      ┌─────────────────────────────────────────────────────────┐
                                      │        Sliding-Window Temporal Normalizer               │
                                      │  Receptive Window: 30 Frames (1.0 sec stroke window)    │
                                      │  Augmentation: Y-axis Rotation, Noise, Time-Warp        │
                                      └──────────────────────────┬──────────────────────────────┘
                                                                 │
                                                                 ▼
                                      ┌─────────────────────────────────────────────────────────┐
                                      │      Hybrid 1D-CNN + BiLSTM + Temporal Attention        │
                                      │  Stage 1-2: Conv1D (64, 128) + SpatialDropout1D         │
                                      │  Stage 3-4: Stacked BiLSTM (64 units, Temporal Context) │
                                      │  Stage 5:   Multi-Head Temporal Impact Attention        │
                                      │  Stage 6:   Focal Loss Classification Head (γ=2.0)      │
                                      └──────────────────────────┬──────────────────────────────┘
                                                                 │
                                                                 ▼
                                      ┌─────────────────────────────────────────────────────────┐
                                      │            Pure TFLite FP16 Graph Engine                │
                                      │  • BiLSTM Graph Unrolled (zero Flex Delegate)           │
                                      │  • Size: 1.0 MB | Latency: 4.8ms on Mobile NPU          │
                                      │  • Deployed to Android CameraX + Jetpack Compose App    │
                                      └─────────────────────────────────────────────────────────┘
```

---

## 🎯 Target Stroke Classes

| Class ID | Target Label | Description | Biomechanical Characteristics |
| :---: | :--- | :--- | :--- |
| `0` | **`overhead_smash`** | Explosive power smash & attacking jumps | Maximum shoulder-hip torque, rapid elbow extension, downward racket trajectory |
| `1` | **`other_stroke`** | Clears, drops, drives, and lifts | Defensive or tactical trajectory, lower rotational angular velocity |
| `2` | **`idle_footwork`** | Ready stance, split-steps & recovery | Torso stabilization, knee flexion, lack of overhead racket arm acceleration |

---

## ⚡ Biomechanical Feature Engineering Formulae

The feature engine extracts domain-specific kinetic metrics invariant to player height, court position, and camera perspective:

1. **Torso Scale Normalization**:
   $$\mathbf{P}_{\text{norm}} = \frac{\mathbf{P} - \mathbf{P}_{\text{mid\_hip}}}{\|\mathbf{P}_{\text{mid\_shoulder}} - \mathbf{P}_{\text{mid\_hip}}\|_2}$$

2. **Savitzky-Golay Trajectory Smoothing**:
   Applies local least-squares polynomial convolution ($W=7, p=2$) across 3D coordinates to eliminate camera jitter while preserving instantaneous stroke peak accelerations.

3. **3D Joint Angle Kinematics**:
   Given 3D coordinates of joint vertices $A, B, C$:
   $$\theta = \arccos\left(\frac{(\mathbf{A} - \mathbf{B}) \cdot (\mathbf{C} - \mathbf{B})}{\|\mathbf{A} - \mathbf{B}\|_2 \|\mathbf{C} - \mathbf{B}\|_2}\right)$$

4. **Hip-Shoulder Kinetic Separation**:
   Measures rotational torsion between pelvic and thoracic planes:
   $$\Delta \theta_{\text{separation}} = \theta_{\text{shoulder\_yaw}} - \theta_{\text{hip\_yaw}}$$
   Critical for quantifying explosive core torsion during the kinetic chain release of a badminton smash.

---

## 🧠 Neural Network Architecture

The classifier balances deep temporal contextual awareness with mobile real-time inference constraints:

```
Input: Tensor shape (Batch, 30 Frames, Features)
│
├── [Conv1D Block 1]: 64 filters, kernel=3, padding='same', Swish
│   └── BatchNormalization + SpatialDropout1D(0.20)
│
├── [Conv1D Block 2]: 128 filters, kernel=3, padding='same', Swish
│   └── BatchNormalization + SpatialDropout1D(0.20)
│
├── [Bidirectional LSTM 1]: 64 units (Forward 64, Backward 64), return_sequences=True
│   └── Recurrent Dropout(0.20)
│
├── [Bidirectional LSTM 2]: 64 units (Forward 64, Backward 64), return_sequences=True
│   └── Recurrent Dropout(0.20)
│
├── [Temporal Attention Layer]: Custom multi-query attention focusing on impact frame
│   └── Context Vector = Σ (α_t * h_t)
│
├── [Dense Layer]: 64 units, Swish + BatchNormalization + Dropout(0.30)
│
└── [Output Layer]: 3 units, Softmax Activation
```

### Categorical Focal Loss Formulation
To mitigate stroke frequency imbalance between routine footwork and rare explosive smashes:
$$\text{FL}(p_t) = -\alpha_t (1 - p_t)^\gamma \log(p_t)$$
Where $\gamma = 2.0$ downweights easily classified idle states, forcing gradient updates on borderline stroke mechanics.

---

## 🚀 Edge Conversion & TFLite Benchmark

Traditional recurrent architectures with dynamic while-loops require the heavy `SELECT_TF_OPS` (Flex delegate) runtime on Android, which bloats APK size by 40+ MB and degrades performance. 

AceAI employs **Loop Unrolling (`unroll=True`)** across the fixed 30-frame temporal receptive field during TFLite conversion:
* **Direct Hardware Mapping**: Unrolled cells compile directly to native TFLite Builtin matrix multiplication kernels.
* **FP16 Post-Training Quantization**: Halves memory bandwidth without losing classification sensitivity ($> 98.4\%$ parity with FP32 model).
* **Model Footprint**: Reduced from ~7.2 MB (Keras full precision) to **0.99 MB** (`badminton_stroke_classifier_pure_fp16.tflite`).
* **Android Inference Speed**: **4.8 ms** per window on Snapdragon 8 Gen 2 / Google Tensor G3.

---

## 📁 Repository Structure

```
.
├── app/                                  # Native Android Application (Kotlin, Jetpack Compose)
│   ├── src/main/java/com/dt/batminton/   # CameraX pipeline, UI overlays, Firestore sync
│   └── src/main/assets/                  # Bundled TFLite model & configs
│       └── badminton_stroke_classifier_pure_fp16.tflite (0.99 MB)
├── models/                               # Saved model weights & TFLite exports
│   ├── gpu_trained_model/                # Best checkpoint (Keras + TFLite)
│   └── hybrid_stroke_classifier/         # Model artifacts & validation checkpoints
├── dataset_root/                         # Master dataset annotations and manifests
│   ├── annotations/                      # Unified JSON annotations
│   ├── engineered_features_npz/          # Pre-computed kinematic arrays
│   └── master_manifest.csv               # Dataset clip manifest & class distribution
├── pdf_slide_renders/                    # High-res design thinking presentation renders
├── DT.pptx                               # Complete Design Thinking Presentation Deck
├── DT.pptx.pdf                           # PDF Export of Design Thinking Presentation
├── align_dt_presentation.py              # Automated PPTX slide alignment engine
├── badminton_hybrid_model.py             # Hybrid 1D-CNN + BiLSTM + Attention architecture
├── badminton_sliding_window_pipeline.py  # Scale-invariant sliding window tensor pipeline
├── biomechanical_feature_engine.py       # Savitzky-Golay filter & 3D angular kinetics
├── build_datasets.py                     # Synthetic & empirical clip dataset compiler
├── convert_and_verify_tflite.py          # Unrolled TFLite FP16 conversion & verification
├── extract_pose_landmarks.py             # MediaPipe 33-keypoint batch landmark extractor
├── generate_final_dt_deck.py             # Presentation generator with embedded figures
├── prepare_dirs.py                       # Directory structure bootstrapping utility
├── standardize_badminton_dataset.py      # Video duration standardizer & integrity validator
├── train_badminton_gpu.py                # Mixed precision GPU training with Focal Loss
└── README.md                             # Repository Documentation
```

---

## 🛠 Quickstart & Pipeline Execution

### 1. Environment Setup
```bash
# Clone the repository
git clone https://github.com/abhisheikraja2007-droid/AceAI-Badminton-ML-Pipeline.git
cd AceAI-Badminton-ML-Pipeline

# Install dependencies using uv or pip
pip install tensorflow mediapipe numpy scipy scikit-learn opencv-python matplotlib
```

### 2. Standardize Video Clips & Extract Pose Landmarks
```bash
# Standardize clips to uniform 1.5s windows at 30 FPS
python standardize_badminton_dataset.py

# Extract 3D MediaPipe pose keypoints
python extract_pose_landmarks.py
```

### 3. Generate Biomechanical Tensors
```bash
# Smooth trajectories, compute angles, and compile sliding windows
python badminton_sliding_window_pipeline.py
```

### 4. Train Deep Neural Network on GPU
```bash
# Train using mixed precision and Categorical Focal Loss
python train_badminton_gpu.py
```

### 5. Compile & Verify Pure TFLite Model
```bash
# Unroll recurrent loops and export FP16 quantized TFLite asset
python convert_and_verify_tflite.py
```

### 6. Run Android App
Open the `app/` folder in **Android Studio Hedgehog or higher**, build and install to an Android device running Android 10+ (API level 29+).

---

## 📊 Presentation & Design Thinking Artifacts

The repository includes the full Design Thinking study and evaluation artifacts:
* **Presentation Deck**: [`DT.pptx`](./DT.pptx) and [`DT.pptx.pdf`](./DT.pptx.pdf)
* **Empathy Maps & SCAMPER Analysis**: Documented in slide deck renders under [`pdf_slide_renders/`](./pdf_slide_renders/)
* **App Wireframes & HUD Overlays**: UI mockups located in [`page_dashboard.png`](./page_dashboard.png) and [`simulation_hud.png`](./simulation_hud.png)

---

## 📜 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
