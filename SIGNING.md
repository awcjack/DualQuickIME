# Release Signing Guide

This guide explains how to sign release APKs for DualQuickIME.

## Overview

Android requires APKs to be signed before installation. Debug builds are automatically signed with a debug key, but release builds need your own keystore.

**Important**: Never commit your keystore file or passwords to git!

## Step 1: Create a Keystore

Run this command to create a new keystore:

```bash
keytool -genkey -v \
  -keystore dualquickime-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias dualquickime
```

You'll be prompted for:
- Keystore password (remember this!)
- Key password (can be same as keystore password)
- Your name, organization, etc.

**Keep this keystore file safe!** If you lose it, you cannot update your app on the Play Store.

## Step 2: Create keystore.properties

Create a file named `keystore.properties` in the project root (NOT in app/):

```properties
storeFile=../dualquickime-release.jks
storePassword=your_keystore_password
keyAlias=dualquickime
keyPassword=your_key_password
```

This file is in `.gitignore` and will NOT be committed.

## Step 3: Build Release APK

### Option A: Using Gradle (Recommended)

```bash
./gradlew assembleRelease
```

The signed APK will be at: `app/build/outputs/apk/release/app-release.apk`

### Option B: Manual signing with apksigner

If you build an unsigned APK:

```bash
# Build unsigned
./gradlew assembleRelease

# Sign with apksigner (from Android SDK build-tools)
apksigner sign \
  --ks dualquickime-release.jks \
  --ks-key-alias dualquickime \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## Step 4: Install on Device

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Or transfer the APK to your phone and install manually.

## CI/CD Signing (GitHub Actions)

For automated builds, store credentials as GitHub Secrets:

1. Go to Repository Settings > Secrets and variables > Actions
2. Add these secrets:
   - `KEYSTORE_BASE64`: Base64-encoded keystore file
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_ALIAS`: Key alias (e.g., "dualquickime")
   - `KEY_PASSWORD`: Key password

To encode your keystore:
```bash
base64 -i dualquickime-release.jks | pbcopy  # macOS
base64 dualquickime-release.jks              # Linux (copy output)
```

## Security Best Practices

1. **Never commit** keystore files or passwords
2. **Backup** your keystore file securely (cloud storage, USB drive)
3. **Use strong passwords** (16+ characters)
4. **Different keystores** for different apps
5. **Environment variables** for CI/CD, not hardcoded values

## Troubleshooting

### "No key with alias found"
- Check that `keyAlias` in keystore.properties matches what you used in keytool

### "Keystore was tampered with, or password was incorrect"
- Double-check your `storePassword`

### APK not installing
- Uninstall existing debug version first (different signatures)
- Check `adb logcat` for detailed errors
