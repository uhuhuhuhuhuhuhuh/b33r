# B33R IPTV 1.9.11 build input

This directory contains the sequential UI patches used to rebuild the 1.9.10 Android source as version 1.9.11.

The redesign provides an original beer-themed smart-hub interface inspired by modern IPTV launchers while preserving the existing VLC and Media3 playback engines, automatic decoder fallback, stream compatibility, resume tracking, track selection, and quality controls.

Patch order:

1. `AppUi-01.patch` adds the smart-hub layout foundation and routes existing section callbacks into it.
2. `AppUi-02.patch` adds the B33R top bar and large remote-friendly launch cards.
3. `AppUi-03.patch` strengthens TV focus states and navigation surfaces.
