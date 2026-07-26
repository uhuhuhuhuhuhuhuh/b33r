# B33R IPTV 1.9.12 build input

This build starts from the generated 1.9.11 source archive and applies the responsive UI patch in this directory.

## Changes

- Adapts the smart-hub header between wide and compact portrait/landscape layouts.
- Moves account information onto its own row when horizontal space is limited.
- Resizes launch cards for narrow displays while preserving TV-sized focus targets.
- Adds ellipsis, line limits, and constrained widths to dynamic labels to prevent text overlap and clipping.
- Stacks the section title and item count on very narrow screens.
- Keeps the existing VLC and Media3 playback engines, fallback behavior, resume tracking, quality controls, and stream compatibility unchanged.

The build workflow publishes a debug-signed preview unless the production signing secrets are configured.
