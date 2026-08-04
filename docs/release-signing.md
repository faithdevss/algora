# Release signing

The keystore and its passwords are **not** committed and never should be. The build reads them at
configuration time the same way it reads the AdMob ids, so `./gradlew bundleRelease` produces a
signed AAB on a machine that has them and an unsigned one everywhere else — no branching, no edit
to a tracked file before a release.

> **Losing the keystore is unrecoverable.** Play refuses updates signed by a different key, and the
> only escape is a key reset request that Google may decline. Back up the `.jks` file and its
> passwords somewhere that survives losing this laptop, before the first upload.

---

## Configure it

Add to `local.properties` (already gitignored, never commit it):

```properties
signing.storeFile=/absolute/path/to/algora-upload.jks
signing.storePassword=…
signing.keyAlias=upload
signing.keyPassword=…
```

`signing.keyPassword` may be omitted when the key password matches the store password — the common
case for a keystore created by Android Studio.

A relative `signing.storeFile` is resolved against the repo root. Keeping the keystore outside the
repo entirely is safer: nothing to accidentally `git add -f`.

Then:

```
./gradlew bundleRelease
```

`app/build/outputs/bundle/release/app-release.aab` is the Play upload artifact.

### Other ways to pass them

Resolved in this order, first hit wins:

1. `local.properties` — `signing.storeFile`, `signing.storePassword`, `signing.keyAlias`, `signing.keyPassword`
2. Gradle properties — `./gradlew bundleRelease -Psigning.storeFile=… -Psigning.storePassword=…`
3. Environment — `ALGORA_STORE_FILE`, `ALGORA_STORE_PASSWORD`, `ALGORA_KEY_ALIAS`, `ALGORA_KEY_PASSWORD`

Option 2 puts the password in your shell history and in the process list; prefer 1 locally and 3 on
CI.

---

## Creating a keystore

Only needed once, and only if you do not already have one:

```
keytool -genkeypair -v \
  -keystore algora-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

`-validity 10000` (~27 years) is Play's recommendation — a key that expires during the app's life
cannot sign updates.

The `.der` files under `certificates/` are public certificate exports of such a key, written by
Android Studio's signed-bundle wizard. They are not secrets and are not needed to build; Play uses
them when registering or rotating an upload certificate.

---

## Verifying what you are about to upload

```
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

An APK named `app-release-unsigned.apk` means the build found no keystore. Every configuration run
says so explicitly:

```
Signing: no release keystore configured — release artifacts will be unsigned
(docs/release-signing.md).
```

If the path is set but wrong, the message names the path instead — a typo in `signing.storeFile`
reads the same as no configuration at all otherwise.

---

## Play App Signing

Play re-signs the app with a key Google holds; the key configured here is the **upload** key that
proves the upload is yours. They are the same key only if you opted out of Play App Signing, which
is no longer offered for new apps.

The R8 mapping file rides along inside the AAB at
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`, so Play deobfuscates crash
reports automatically — there is no separate `mapping.txt` upload step for the bundle. Uploading a
bare APK instead would need it uploaded by hand.

---

## Related

- `docs/admob-setup.md` — the ad ids, same lookup mechanism
- `docs/store/listing.md` — listing copy and the rest of the pre-upload checklist
