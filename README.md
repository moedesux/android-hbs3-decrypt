# HBS Decrypt

HBS Decrypt is an offline Android utility for recovering data from supported
HBS-encrypted backup objects. It can decrypt a single file or recursively
process an encrypted folder using Android's system file picker.

The app is independent of QNAP and is not affiliated with, endorsed by, or
supported by QNAP.

## Current compatibility

Phase 1 supports only uncompressed, non-QuDedup backup objects whose content
starts with the `Salted__` envelope. Compatibility was verified against a
disposable fixture produced by HBS 3 `26.4.4.788` on QTS `5.2.7.3297`; this is
not a guarantee of compatibility with every HBS/QTS installation or format.

The supported legacy envelope uses AES-256-CBC with OpenSSL's historical MD5
key derivation. It has no authenticated integrity, so valid padding is not
proof that the password or recovered data is correct. Unsupported inputs are
rejected rather than guessed at.

The following are not supported yet:

- Compressed backup payloads.
- QuDedup `.qdff` containers.
- HBS Sync envelopes.
- QENC v1 and v2 formats.

See the [Phase 2 backlog](docs/PHASE_2.md) for the planned compatibility work.

## Requirements

To build from source, install:

- JDK 17.
- Android SDK Platform 35.
- Android SDK Build-Tools, including `apksigner` if you intend to inspect a
  signed APK.
- `adb` if you want to install the APK on a connected device or emulator.

The included Gradle wrapper downloads Gradle 8.9 on its first run. Android
Studio can also import the project and use the wrapper automatically.

The app runs on Android 10 (API 29) and newer. It requests no network or
broad-storage permissions; files and folders are selected through Android's
system document picker.

## Build an APK directly from the source

Clone the repository and enter its directory:

```sh
git clone <repository-url>
cd android-hbs3-decrypt
```

If the Android SDK is not already configured, set `ANDROID_HOME` or
`ANDROID_SDK_ROOT` to its location, or create a `local.properties` file with
the SDK path. For example:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Run the unit tests and lint checks:

```sh
./gradlew test lint
```

Build the installable debug APK:

```sh
./gradlew assembleDebug
```

The result is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To run the instrumented Android tests, start an API 29-or-newer emulator or
connect a device with USB debugging enabled, then run:

```sh
./gradlew connectedAndroidTest
```

## Build a release APK

A release build can be produced without putting signing credentials in the
repository:

```sh
./gradlew assembleRelease
```

Without local signing configuration, the APK is unsigned and is written to:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

For an APK that Android can install normally, sign it with a keystore you
control. Generate a keystore once outside the repository:

```sh
keytool -genkeypair \
  -keystore /secure/path/hbs-decrypt-release.jks \
  -alias hbs-decrypt \
  -keyalg RSA -keysize 4096 -validity 10000
```

Copy the template and fill in the keystore details locally. Never commit the
resulting file or the keystore:

```sh
cp signing.properties.example signing.properties
${EDITOR:-vi} signing.properties
```

Then build the signed release APK:

```sh
./gradlew assembleRelease
```

When `signing.properties` is present and valid, the output is:

```text
app/build/outputs/apk/release/app-release.apk
```

Verify the signature before sharing the APK:

```sh
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Keep the same keystore for future updates. Android will not treat an APK
signed with a different key as an update to an existing installation; uninstall
the old app first, or use a distinct application identity.

The repository's owner-controlled release and checksum procedure is documented
in [docs/RELEASE.md](docs/RELEASE.md).

## Using the app

1. Choose an encrypted file, or choose an encrypted folder for recursive
   recovery.
2. Choose an output folder.
3. Enter the HBS password.
4. Choose whether existing output files should be skipped (the default) or
   replaced.
5. Tap **Start**.

The password is cleared after each recovery attempt. A cancelled or failed
operation may leave no final output for the item being processed; folder
recovery uses temporary `.part` files while writing.

## Repository layout

```text
app/src/main/        Android application and decryption/recovery code
app/src/test/        JVM unit tests
app/src/androidTest/ Instrumented Android and Compose tests
assets/              Sample encrypted and decrypted data
docs/                Release notes and compatibility backlog
```

## Acknowledgements

This project acknowledges the reverse-engineered prior art in
[Mikiya83/hbs_decipher](https://github.com/Mikiya83/hbs_decipher) and
[LoadingByte/qnap-hbs-decryptor](https://github.com/LoadingByte/qnap-hbs-decryptor).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
