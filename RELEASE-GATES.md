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

RC1 now requires stored RAR4 byte-exact extraction and RAR traversal rejection in addition to 7z, and direct-engine 16/32-range byte-exact JVM regression. Actual RAR5 compressed/solid variants, sustained throughput and real-device performance remain open.

RC1 optional screen-lock candidate uses Android device credentials and a private marker across app processes, after 30 seconds away or new process; FLAG_SECURE, authenticated enable/disable, PiP disabled while lock enabled. Not encryption of shared downloads or protection of notification controls. Device credential prompts, cross-process re-entry and external picker lifecycle require physical testing.

RC1 adds MediaStore recycle/restore candidate and test gate (21 Android tests per emulator). Custom SAF trash not implemented; Android may expire trashed files. App lock timeout check also covers pre-start shared intents.

RC2 candidate extends background queue, basic ASS and gestures; physical lock-screen/PiP/gesture/codec compatibility and ASS styling remain to verify. No new decoder is claimed.

RC2 adds estimated-size video conversion/batch and multi-file LAN ZIP share candidates. Actual target sizes, media compatibility, batch and LAN lifecycle require validation. Optional engine modules, widget and full localization remain open.

RC2 mini-player and aggregate launcher widget candidates added; widget host rendering, background queue transitions and real video output sizes remain device verification tasks. Optional downloadable engine modules and full localization not completed.

RC2 runtime engine switches and partial localization added. Full localization, independently downloadable modules and controlled physical measurements against competitors remain open. Device script records startup/PSS and battery/thermal context only.

RC2 adds compatibility conversion entry point and device decoder report, Arabic labels in legacy export, and hard LAN session expiry. Full native decoder coverage and real multi-device share verification remain open.

RC3 introduces consent-based floating shortcut and foreground clipboard suggestion (not automatic clipboard read over unrelated apps), plus API29 emulator and rate-limit-only build retries. Verify on actual OPPO F11 and Huawei nova 7 (Android/EMUI variant), including overlay permission, focus, share, revoke/stop and lock-screen behavior. Final release remains blocked until exact commit tests and owner signing/device validation.

RC3 UI regression added: valid shared URL displays without auto-starting download; credential-bearing URL rejected. 22-test gate on API29/30/35. No claim of universal auto-popup or physical phone compatibility yet.

X8 dynamic range candidate requires exact-byte, pause/resume, split partition and invalid-range JVM regressions plus physical throughput/energy tests. 8x is a conditional target, not a measured result.

RC4 X8 interface and browser integration candidates: optional one-click within built-in browser, external MIME-aware chooser, mode/appearance backup and main RTL. Verify physical external browser behavior, layout at 125% text, and one-click duplicate/permission behavior. FTP/FTPS/SFTP/QUIC, optional independently installed engines, full localization and signed release remain open.

RC4 adds real concurrent slow-tail test and byte-coverage UI; verify actual Android rendering and device throughput/energy before any X8 claim.

RC4 boot recovery reminder and visible filtered link grabber candidates added. Require 23 instrumented tests each API29/30/35 plus physical boot/permission test; no silent restart of manually paused downloads.

RC4 FTP/FTPS candidate: anonymous public files only, plaintext opt-in, explicit TLS with trust/hostname/data peer checks. Requires actual FTPS positive/negative certificate tests and OEM testing; no SFTP or HTTP/3 claim. CI missing-artifact transient handled by bounded repeat, final failure remains fail-closed.
