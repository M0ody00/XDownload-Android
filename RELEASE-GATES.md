# Ultimate Angel 4.0 release gates
**Status: integrated preview, not final.**
Baseline: 4.0 Preview 1, verified run 45563190176 (18 instrumentation tests on each of API 30/35).
- [ ] Exact preview commit passes JVM tests, build, signing integrity, size gate and 18 instrumentation tests on both emulators.
- [ ] Physical ARM64 OPPO F11 Android 11: install/upgrade with owner's durable signing key, download, network interruptions, reboot, sleep, battery restrictions, storage-full handling, local playback, conversions, browser, vault, permissions, and crash-free extended use.
- [ ] Test 16/32 actual ranged connections against a controlled fixture with byte-for-byte hash verification; compare against 8 and 1, measure throughput, memory, thermal state and battery. Optional high cap is not a measured speed improvement.
- [ ] Real-device playback speed, sleep and resume; native GIF/PNG encoder tests; document export and ZIP creation; privacy block rules and offline reading.
- [ ] Persisted owner signing key/environment configured by owner; no key in repository. Require same-commit successful verification.
- [ ] Complete remaining product scope: strict aggregate bandwidth control across direct/media/torrent, URL replacement workflow, storage-tree destination, background/reboot auto-resume respecting Android limits; comprehensive browser filtering/session restoration; background audio/media session, PiP/mini player, subtitles, EQ, gestures, favorites; batch conversions, compression targets, metadata/artwork, subtitles, archive support RAR/7Z, multi-file operations/recycle bin, multi-file LAN sharing, app-wide lock, encrypted backup, widgets, full localization, optional engine modules. These are not represented as completed.
- [ ] Benchmark against actual competitors using authorized content and identical device/network/thermal conditions; no rank-1 claim without evidence.
- [ ] Confirm licenses, privacy notice and source distribution obligations before public distribution.

Preview 2: background single-track audio is implemented as a candidate. Verify new same-commit run; full queue/controls and physical lock-screen/audio focus behavior remain open.

Preview 3 candidate adds explicit PiP and local SRT. Verify PiP lifecycle on OPPO/Android 11, subtitle timing with real video; ASS, background queue and remaining features still open.

Preview 3 follow-up: favorites and conservative separate-download renewal are implemented as candidates; seamless validator-backed replacement still open. New UI requires testing.

Candidate follow-up implements direct HTTP aggregate pacing and platform audio effects; strict native torrent/media bandwidth cap and actual effect/throughput validation remain open.

Preview 4 includes sequential local batch audio conversion to ZIP; batch video and actual multi-file device tests remain pending.

Preview 4 follow-up adds encrypted settings backup only (not file/history backup), actual video-image and backup regression gate. Final release still blocked until tests, real ARM64 device and durable signing verification, plus remaining scope.

Candidate adds selected storage-tree output, with Android/provider restrictions; device and removable-storage testing remains open.

RC1 adds bounded 7z/RAR candidate; 20-test gate includes real 7z extraction. RAR fixture testing, full license/security audit and physical benchmarks remain mandatory. A release candidate is not an approved Final Edition.
