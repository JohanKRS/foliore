# Releasing Foliore

Every version is published as a GitHub Release with a signed APK and a changelog. Keep these steps the same for every release.

## 1. Prepare the version

1. In `androidApp/build.gradle.kts`, raise `versionCode` by one and set `versionName` (semantic versioning: `MAJOR.MINOR.PATCH`).
2. Add a section to the top of [`CHANGELOG.md`](../CHANGELOG.md): `## [X.Y.Z] - YYYY-MM-DD`, with the changes since the previous release grouped under **Added**, **Changed**, **Fixed** and **Removed** (leave out empty groups). Add the matching link at the bottom of the file.
3. Merge both changes into `main` before building.

## 2. Build the signed APK

Releases are built on the maintainer's computer, in the local clone that holds the signing files (`keystore.properties`, the keystore and `local.properties`, all ignored by git). On a clean `main` at the commit to release:

```sh
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:assembleRelease
```

Check the APK in `androidApp/build/outputs/apk/release/`:

```sh
apksigner verify --print-certs <apk>        # must verify; note the certificate SHA-256
aapt2 dump badging <apk> | head -1          # package, versionCode, versionName
cp <apk> foliore-X.Y.Z.apk && sha256sum foliore-X.Y.Z.apk
```

Don't publish if a test fails or the signature doesn't verify.

## 3. Publish the GitHub Release

- Tag: `vX.Y.Z`, created on the exact commit that was built.
- Title: `Foliore X.Y.Z`.
- Asset: `foliore-X.Y.Z.apk`.
- Notes: the `CHANGELOG.md` section in English, then the same text in Portuguese, then an **Install** note (Android 8.0 or later) and the APK and signing-certificate SHA-256 values.

```sh
gh release create vX.Y.Z foliore-X.Y.Z.apk --repo JohanKRS/foliore \
  --target <commit SHA> --title "Foliore X.Y.Z" --notes-file notes.md --latest
```

The first public release, 0.1.0, lists features only. Every release after it lists the changes.
