# NEXORA Studio

A mobile-first Android video editor. The product scope is video editing only; 3D modelling is intentionally excluded.

## Design
Professional, clean and touch-friendly. Neutral surfaces, clear hierarchy, compact tools, and a responsive editing workspace.

## Current foundation
- Kotlin and Jetpack Compose Android application
- Video, image and audio import through Android's document picker
- Video preview with playback controls and local media access
- Single-clip MP4 export using AndroidX Media3 Transformer
- HSL hue, saturation and lightness adjustments in preview and export
- Editable four-point speed curve, sampled into speed changes for export
- Editable blend-opacity curve and selectable blend-mode settings stored in project data
- Local SQLite project save, reopen and delete
- Device capability and USB/OTG information
- Offline object/point motion tracking prototype
- Android GitHub Actions build, lint and unit-test workflow

## Editing roadmap
- Frame-accurate timeline with a real playhead, one-second snapping and split/trim operations
- Multi-track timeline and true blend compositing between clips/overlays
- Per-property keyframe tracks with Linear, Ease In, Ease Out, Ease In-Out and Bezier interpolation
- Full color grading panel, including RGB curves, exposure, contrast and HSL ranges
- Audio mixer, waveforms, fades, text, titles, subtitles and transitions
- Robust undo/redo and autosave
- Local render-worker pairing and render-farm support

## Current limitations
NEXORA Studio is an active foundation, not yet a finished professional NLE. Blend modes are currently selectable and saved, but clip-to-clip compositing needs the multi-track/overlay engine. The keyframe interpolation selector is stored, but complete animated property tracks and Bezier handles are not yet implemented. Timeline split markers are not yet frame-accurate edits.
