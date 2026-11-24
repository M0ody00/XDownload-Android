# X Download Pro 2.0 — competitive acceptance protocol

No performance ranking has been established. Compare X Download Pro with installed, current, legitimately obtained versions of Snaptube, 1DM+, ADM, Seal, YTDLnis and FDM on the same physical device. Record each app's exact version and source; do not use modified packages or compare restricted-content extraction.

## Repeatable methodology
- Devices: OPPO F11 / Android 11 as the primary target; an additional modern Android device. Record RAM, available storage, OS, thermal status, network, and battery settings.
- Fixtures: host owner-created WAV, MP4, unencrypted HLS/DASH and 5/100/500 MB pseudorandom files on an authorized HTTPS server. Publish SHA-256 hashes, ETags, Content-Length and range capability. Add a server that ignores Range, changes ETag, delays responses, returns HTTP 429, or deliberately truncates the response.
- Use identical file, server, network, permissions and storage settings per app. Run at least 5 repetitions in randomized order; reset cache or state consistently. Record median and p95, not best-of.
- Metrics: cold-start time, share-to-quality-picker time, first-byte time, elapsed download time, throughput (verified file bytes divided by elapsed time), peak RSS, battery drain (long enough for meaningful resolution), success rate, resume success, checksum match, crash/ANR, final disk footprint. Track unsupported operations separately, not as zero-second results.
- Switch Wi-Fi/mobile network during a large authorized transfer; pause/resume; background screen off; force-stop/restart; low space; stale URL; blocked notifications. Never classify an incomplete or corrupt file as success.
- Test format and size labels against actual outputs; estimates must be marked. Test direct shares, duplicates, Arabic/English layout, screen rotation, and accessibility.
- Do not claim faster than a competitor without comparable measurements; do not extrapolate emulator outcomes to physical ARM64.

## Gate
Publish only after all automated tests pass, device installation and real-use smoke tests pass, and owner signing certificate is verified. Goals: 99% successful supported downloads, 99.9% crash-free sessions, under 2 seconds cold start on target hardware, under 5 seconds simple-link analysis. These are targets, not measured results. Full site coverage, DRM, and universal 8K are not guaranteed.

## Results (not yet measured)
| App | Exact version | Device | Fixture | Median time | p95 | SHA match | RSS | Notes |
|---|---|---|---|---|---|---|---|---|
| X Download Pro | 2.0 RC1 | OPPO F11 | pending | — | — | — | — | Physical device pending |
| Snaptube | pending | same | pending | — | — | — | — | Not installed/tested |
| 1DM+ | pending | same | pending | — | — | — | — | Not installed/tested |
| ADM | pending | same | pending | — | — | — | — | Not installed/tested |
| Seal | pending | same | pending | — | — | — | — | Not installed/tested |
| YTDLnis | pending | same | pending | — | — | — | — | Not installed/tested |
| FDM | pending | same | pending | — | — | — | — | Not installed/tested |
