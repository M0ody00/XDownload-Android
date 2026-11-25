# RC5 targeted audit and acceptance gates
## Baseline
RC4 X8 run 45573541788 passed JVM and 23 instrumentation tests per API 29/30/35 (69). ARM64 APK 76,435,047 bytes; zipped artifact 65,792,076 bytes. This is not RC5 verification.
## Changes addressing device feedback
- Home: compact dashboard, X Player glyph drawing reused from x-player-pro, icon navigation, live active/completed/speed summary; no massive tool buttons.
- Local player: black cinema layout, minimal controls, tools menu retains speed/repeat/playlist/sleep/PiP/subtitles/EQ/compatibility/background audio, single/double tap distinction.
- Clipboard: legacy permanent overlay stopped on launch; explicit share and quick settings tile, foreground-only link chip expires after 10s. Android API29+ limits clipboard to focused app or IME. No background clipboard monitoring.
- Media: direct audio/video links avoid unnecessary extractor, one-link share captions, default audio selection for music source when available, duplicate check, retries limited and actionable errors, no raw URLs in errors.
- Transfer: reject unexpected content encoding, publication verifies copied byte count; storage errors and revoked folder grants explained. Existing X8 validators, SHA256 and resume checks retained.
## Verification required
- Exact RC5 commit: JVM tests (including encoding, share, diagnostic tests), 24 instrumentation tests on each API29/30/35, APK signature and size checks.
- Physical OPPO F11 and Huawei nova 7: installation/upgrade, RTL and landscape, theme/font size, actual playback, Quick Settings tile, share, gesture/PiP, downloads on mobile/Wi-Fi, storage revoke/restore, background battery restrictions.
- Authorized real-world media URLs, including the specific failing YouTube Music link (not supplied). No guarantee where source denies access or offers no authorized file; use official service for protected content.
- Competitive benchmark on same device/network; no measured 8x claim.
- Full static audit of every source file and independent security review not complete; targeted audit only. Optional native modules, SFTP/QUIC, full codec parity, persistent signing and production release still pending.
