# HBS Decrypt

An offline Android utility for recovering HBS-encrypted data. This repository
currently provides the privacy-conscious Android application shell; recovery
work follows in later slices.

## Requirements

- Android 10 (API 29) or newer
- JDK 17 to build locally

## Build and verify

```sh
./gradlew test lint
```

The first invocation downloads the pinned Gradle distribution into the Gradle
user home. The app has no network or broad-storage permissions.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
