# Ultimate Angel 4.0 release gates
**Status: integrated preview, not final.**
Baseline: 3.0 RC1, verified run 45561084159 (17 instrumentation tests on each of API 30/35).
- [ ] Exact preview commit passes JVM tests, build, signing integrity, size gate and 18 instrumentation tests on both emulators.
- [ ] Physical ARM64 OPPO F11 Android 11: install/upgrade with owner's durable signing key, download, network interruptions, reboot, sleep, battery restrictions, storage-full handling, local playback, conversions, browser, vault, permissions, and crash-free extended use.
- [ ] Test 16/32 actual ranged connections against a controlled fixture with byte-for-byte hash verification; compare against 8 and 1, measure throughput, memory, thermal state and battery. Optional high cap is not a measured speed improvement.
- [ ] Real-device playback speed, sleep and resume; native GIF/PNG encoder tests; document export and ZIP creation; privacy block rules and offline reading.
- [ ] Persisted owner signing key/environment configured by owner; no key in repository. Require same-commit successful verification.
- [ ] Complete remaining product scope: strict aggregate bandwidth control across direct/media/torrent, URL replacement workflow, storage-tree destination, background/reboot auto-resume respecting Android limits; comprehensive browser filtering/session restoration; background audio/media session, PiP/mini player, subtitles, EQ, gestures, favorites; batch conversions, compression targets, metadata/artwork, subtitles, archive support RAR/7Z, multi-file operations/recycle bin, multi-file LAN sharing, app-wide lock, encrypted backup, widgets, full localization, optional engine modules. These are not represented as completed.
- [ ] Benchmark against actual competitors using authorized content and identical device/network/thermal conditions; no rank-1 claim without evidence.
- [ ] Confirm licenses, privacy notice and source distribution obligations before public distribution.
