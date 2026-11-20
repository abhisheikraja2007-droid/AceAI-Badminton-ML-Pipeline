---
name: AceAI Biomechanics HUD
colors:
  surface: '#0f131d'
  surface-dim: '#0f131d'
  surface-bright: '#353944'
  surface-container-lowest: '#0a0e18'
  surface-container-low: '#171b26'
  surface-container: '#1c1f2a'
  surface-container-high: '#262a35'
  surface-container-highest: '#313540'
  on-surface: '#dfe2f1'
  on-surface-variant: '#c4c9ac'
  inverse-surface: '#dfe2f1'
  inverse-on-surface: '#2c303b'
  outline: '#8e9379'
  outline-variant: '#444933'
  surface-tint: '#abd600'
  primary: '#ffffff'
  on-primary: '#283500'
  primary-container: '#c3f400'
  on-primary-container: '#556d00'
  inverse-primary: '#506600'
  secondary: '#bdf4ff'
  on-secondary: '#00363d'
  secondary-container: '#00e3fd'
  on-secondary-container: '#00616d'
  tertiary: '#ffffff'
  on-tertiary: '#670020'
  tertiary-container: '#ffd9dc'
  on-tertiary-container: '#c90046'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#c3f400'
  primary-fixed-dim: '#abd600'
  on-primary-fixed: '#161e00'
  on-primary-fixed-variant: '#3c4d00'
  secondary-fixed: '#9cf0ff'
  secondary-fixed-dim: '#00daf3'
  on-secondary-fixed: '#001f24'
  on-secondary-fixed-variant: '#004f58'
  tertiary-fixed: '#ffd9dc'
  tertiary-fixed-dim: '#ffb2ba'
  on-tertiary-fixed: '#400011'
  on-tertiary-fixed-variant: '#910030'
  background: '#0f131d'
  on-background: '#dfe2f1'
  surface-variant: '#313540'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 26px
    fontWeight: '800'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 18px
    letterSpacing: 0.05em
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.08em
  telemetry-display:
    fontFamily: JetBrains Mono
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.03em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.5rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system establishes a high-performance, real-time edge biomechanics environment engineered for courtside tracking and real-time computer vision feedback. Tailored for professional athletes, performance analysts, and elite coaches, the UI conveys precision instrumentation, microsecond responsiveness, and relentless technical rigor. 

Visually, the style merges **High-Contrast Dark Mode** with an **Edge-Computing Heads-Up Display (HUD)**. Key characteristics:
- **Optical Contrast:** Intense, luminescent neon highlights against deep, carbon-tinted court surfaces to maintain legibility under harsh stadium halogen lights or direct outdoor sunlight.
- **Instrumental Glassmorphism:** Translucent surface cards layered over real-time camera viewports and 3D joint meshes without degrading GPU draw speeds.
- **Architectural Telemetry:** Rejection of skeuomorphic decorations in favor of programmatic grids, dynamic vector ticks, calibration brackets, and tabular tracking arrays.

## Colors

The palette is tuned specifically for sub-millisecond recognition, data grouping, and high-frequency real-time edge tracking:

- **Primary (`#CCFF00` - Shuttlecock Optic Neon-Lime):** Primary actions, optimal biomechanical execution, joint alignment nodes within tolerance (&lt;5° error), and peak velocity vectors.
- **Secondary (`#00E5FF` - 3D Telemetry Cyber-Cyan):** Spatial tracking grids, 3D kinematic raycasts, skeletal wireframes, and secondary sensor data streams.
- **Tertiary / Error (`#FF3366` - Biomechanical Fault Crimson):** Joint overextension, hazardous kinematic angles, service faults, and latency drops.
- **Neutral Canvas (`#0B0F19` - Deep Carbon Black):** Absolute base layer designed to maximize OLED battery efficiency during edge AI model inference.
- **Surface Elevation (`#131B2E` at 85% alpha):** Elevated HUD panels and floating cards with backdrop blur.
- **Muted Outlines (`#1E293B`):** Structural layout divisions, structural telemetry cages, and inactive control bounds.
- **Content Hierarchy:** Pure `#FFFFFF` for primary telemetry reads and labels; `#8E9BAE` for metric units, descriptive subheads, and inactive statuses.

## Typography

Typography prioritizes fast scanning under dynamic, high-vibration physical conditions:

1. **Primary Structural Type (`Inter`):** Handles headlines, narrative coaching suggestions, status text, and standard body flows. Tightly tracked at headline scales to maintain an assertive, industrial character.
2. **Telemetry & Metric Type (`JetBrains Mono`):** Applied to all raw numbers, angular metrics, frames-per-second counters, timestamps, coordinates, and programmatic labels. Monospaced tabular numerals are non-negotiable; dynamic metric updates must avoid layout shifts during frame renders.
3. **Hierarchy Discipline:** Always format units (e.g., `KM/H`, `DEG`, `MS`, `FPS`) using `label-sm` in `#8E9BAE`, anchored directly alongside large tabular metric values.

## Layout & Spacing

The layout is built for real-time mobile display viewporting, full-screen camera overlays, and split-screen landscape tracking:

- **Layout Model:** 4-column dynamic grid on portrait mobile; 8-column split-pane (Camera Viewport 5 cols, HUD Telemetry 3 cols) on landscape orientations.
- **Spacing Rhythm:** Standard 8pt base grid with a 4pt sub-grid (`space-xs: 4px`, `space-sm: 8px`, `space-md: 16px`, `space-lg: 24px`, `space-xl: 32px`).
- **Telemetry Shell Boundaries:** Margins maintain a minimum `16px` clearance from edge device safe insets (camera cutouts, navigation bars) to prevent visual clipping during handheld coaching sessions.
- **Viewport Agility:** When tracking actively runs, metrics collapse into floating HUD modules docked to the display corners to prioritize full-field vision processing.

## Elevation & Depth

Depth is defined through optical translucency, layered HUD surfaces, and localized glow fields:

- **Surface Layering:** 
  - **Level 0 (Base Canvas):** Solid `#0B0F19`. Real-time camera feed and kinematic pose mesh.
  - **Level 1 (Docked Containers):** `#131B2E` with 85% opacity, combined with a 16px background blur (`Modifier.blur(16.dp)` in Jetpack Compose).
  - **Level 2 (Active Overlays / Overextension Alerts):** `#131B2E` at 95% opacity with high-contrast accent outlines.
- **Outlines over Drop Shadows:** Ambient drop shadows are replaced by crisp 1px borders (`#1E293B`). This maintains crisp edge definitions across shifting camera backgrounds.
- **Luminescent Glow:** For critical biomechanical faults or peak smash velocities, cards emit a directional outer glow: 0px blur, 4px spread in `#CCFF00` or `#FF3366` with 25% opacity.

## Shapes

The system uses a geometric, technical aesthetic (`roundedness: 1`):
- **Base Surfaces & Cards:** `4px` (`0.25rem`) corner radius. Rounded contours are kept minimal to mirror military HUDs and professional laboratory telemetry.
- **Telemetry Indicators & Badges:** `2px` to `4px` corner radius. Strict rectangular layouts communicate calibration stability.
- **Floating Action Controls:** `8px` (`0.5rem`) corner radius for primary interaction triggers, maintaining tactile distinction from passive display monitors.

## Components

### Buttons
- **Primary Kinetic Trigger:** Background `#CCFF00`, text `#0B0F19` (`FontWeight.Bold`), 4px border radius. Used for active recording toggles and calibration initiations.
- **Secondary HUD Action:** Background `rgba(19, 27, 46, 0.85)`, border 1px solid `#00E5FF`, text `#00E5FF`. Active/press state shifts border to 2px solid `#CCFF00`.
- **Fault / Kill Trigger:** Background transparent, border 1px solid `#FF3366`, text `#FF3366`.

### Telemetry Cards & Slot Containers
- **Edge Diagnostic Slot:** Containers intended for real-time video, joint matrices, or dynamic graphs must be rendered as programmatic wireframe slots with a `1px dashed #00E5FF` border, background `#131B2E` (85% alpha), and populated with variable tokens:
  - Example: `{{ANGLE_ELBOW_R}}`, `{{VELOCITY_SMASH_PEAK}}`, `{{LATENCY_INFERENCE_MS}}`.
- **Card Header:** Uppercase `label-sm` in `#8E9BAE` with right-aligned status indicators (e.g., green dot `4px` circle for nominal tracking, crimson blinking for drift).

### Chips & Telemetry Status Badges
- **Nominal Tracking Chip:** Background `rgba(204, 255, 0, 0.1)`, text `#CCFF00`, 1px solid `#CCFF00`.
- **Spatial Kinematic Chip:** Background `rgba(0, 229, 255, 0.1)`, text `#00E5FF`, 1px solid `#00E5FF`.
- **Biomechanical Fault Chip:** Background `rgba(255, 51, 102, 0.15)`, text `#FF3366`, 1px solid `#FF3366`.

### Lists & Key-Value Telemetry Tables
- **Telemetry Pair Rows:** Border bottom 1px solid `#1E293B`, height 36px, zero internal vertical padding to maintain dense data arrays. Left side holds variable descriptor in `Inter` (`body-sm`, `#8E9BAE`); right side holds metric in `JetBrains Mono` (`label-lg`, `#FFFFFF`).

### Inputs & Controls
- **Sliders (Angle Tolerance Adjuster):** Track height 2px `#1E293B`, active progress `#00E5FF`, thumb 12x12px square `#CCFF00` with 0px corner radius.
- **Toggles / Switches:** Rectangular housing 36x18px `#1E293B`. Thumb 14x14px `#00E5FF` on active state; active track border 1px solid `#00E5FF`.

### Biomechanical Radar & HUD Vectors
- **Skeleton Angle Overlay:** Vector lines `2px` thickness in `#00E5FF`. Joint pivot nodes rendered as dual-ring circles (Outer 6px `#CCFF00`, Inner 2px `#0B0F19`). Faulty joint segments shift to `#FF3366`.