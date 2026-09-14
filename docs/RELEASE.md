# Owner release workflow

This repository never contains release credentials, and GitHub Actions only
tests and lints the project. The owner signs a release locally with an offline
keystore. Keep that keystore outside this repository, maintain an encrypted
offline backup, and store its credentials in a password manager.

## Prepare the local signer

Install JDK 17 and the Android SDK build tools, including `apksigner`. Create a
release keystore once, outside this checkout, and retain it for every future
update:

```sh
keytool -genkeypair -keystore /secure/path/hbs-decrypt-release.jks \
  -alias hbs-decrypt -keyalg RSA -keysize 4096 -validity 10000
```

Copy the non-secret template and replace its placeholder values. Do not commit
the resulting file.

```sh
cp signing.properties.example signing.properties
${EDITOR:-vi} signing.properties
```

`storeFile` can be an absolute path or a path relative to the repository root.
The Gradle release variant uses this configuration only when
`signing.properties` exists; without it, CI continues to build and verify
without release credentials.

## Build v0.1.0 from verified source

Before release, obtain the intended signed tag from the canonical remote and
verify it with the owner’s trusted signing key. The tag verification step is
only meaningful after that key has been imported and trusted locally.

```sh
git fetch --tags origin
git verify-tag v0.1.0
git checkout --detach v0.1.0
./gradlew test lint
./gradlew assembleRelease
```

Then verify the APK signature, copy the artifact under its release name, and
write its checksum:

```sh
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
mkdir -p release
cp app/build/outputs/apk/release/app-release.apk release/hbs-decrypt-v0.1.0.apk
sha256sum release/hbs-decrypt-v0.1.0.apk > release/hbs-decrypt-v0.1.0.apk.sha256
```

Publish `release/hbs-decrypt-v0.1.0.apk` and its adjacent SHA-256 file in the
`v0.1.0` GitHub Release. Verify the printed certificate identity against the
owner’s recorded certificate fingerprint before publishing.
