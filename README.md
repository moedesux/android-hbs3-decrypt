# HBS Decrypt

An offline Android utility for recovering HBS-encrypted data.

## Phase-1 compatibility and security boundary

Phase 1 accepts only uncompressed, non-QuDedup backup objects with the
`Salted__` envelope. Compatibility is verified against a disposable fixture
produced by HBS 3 `26.4.4.788` on QTS `5.2.7.3297`; this does **not** promise
compatibility with every installation of either version or with other HBS
formats. Unsupported inputs are rejected instead of guessed at.

The verified legacy envelope uses AES-256-CBC with OpenSSL's historical MD5
key derivation. It has no authenticated integrity: valid padding after
decryption is not cryptographic proof that the password or recovered data is
correct. This compatibility support is not a recommendation for new encryption.

This project is independent and is not affiliated with, endorsed by, or
supported by QNAP. It acknowledges the reverse-engineered prior art in
[Mikiya83/hbs_decipher](https://github.com/Mikiya83/hbs_decipher) and
[LoadingByte/qnap-hbs-decryptor](https://github.com/LoadingByte/qnap-hbs-decryptor).

Planned formats and execution work are tracked in the
[phase-2 backlog](docs/PHASE_2.md).

## Requirements

- Android 10 (API 29) or newer
- JDK 17 to build locally

## Build and verify

```sh
./gradlew test lint
```

The first invocation downloads the pinned Gradle distribution into the Gradle
user home. The app has no network or broad-storage permissions.

## Owner release

Release credentials stay outside source control and CI. The reproducible local
workflow for verifying source, signing, checking, and checksumming
`hbs-decrypt-v0.1.0.apk` is in [docs/RELEASE.md](docs/RELEASE.md).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
