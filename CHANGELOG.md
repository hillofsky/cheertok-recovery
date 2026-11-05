# Changelog

## 0.6 — Initial public release

- Recover mouse movement, scrolling and consumer controls through BLE and virtual HID.
- Route Home and Back buttons and distinguish touch taps by timing.
- Provide an Android switch UI backed by a Shizuku UserService.
- Suppress duplicate native HID input while recovery is active; request restoration on stop.
- Document the tested Shizuku-Next offline startup procedure and hardware limitations.
- Add portable SDK builds, dependency checksum checks and recorded-timing replay tests.
- Reduce default worker output by omitting raw report payloads and connection addresses.

Hardware verification was performed with the pre-publication 0.6 build. The public build
uses the same recovery logic with source formatting and reduced default logging.
