# Android release signing

Until a release key is configured, release APKs are signed with a **debug** key. On CI that key is regenerated on every run, so:
- users cannot install an update over a previous version (the signature differs), and
- Google Play rejects debug-signed apps.

Set this up once. It takes about 5 minutes.

## 1. Create the key (once, on your own machine)

```bash
keytool -genkeypair -v \
  -keystore synapse-release.jks \
  -alias synapse \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong passwords and **back up `synapse-release.jks` and both passwords somewhere safe** (e.g. a password manager). If you lose them, you can never ship an update to existing installs.

**Never commit the keystore or passwords to the repository.**

## 2. Add GitHub secrets

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Output of `base64 -w0 synapse-release.jks` (macOS: `base64 -i synapse-release.jks`; Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("synapse-release.jks"))`) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `synapse` (or whatever you used) |
| `ANDROID_KEY_PASSWORD` | Key password |

The release workflow decodes the key, signs the APK with it, and deletes nothing from your repo. If the secrets are missing, the build still succeeds but shows the warning **"Android release is debug-signed"**.

## 3. Optional: sign local release builds

Add this to `~/.gradle/gradle.properties` (your user folder, **not** the project):

```properties
synapse.keystore.file=/absolute/path/to/synapse-release.jks
synapse.keystore.password=…
synapse.key.alias=synapse
synapse.key.password=…
```

Then `./gradlew :app:androidApp:assembleRelease` produces a properly signed APK.

## Note for existing users

Apps already installed from earlier debug-signed releases must be **uninstalled once** before installing the first properly signed version. After that, updates install normally.
