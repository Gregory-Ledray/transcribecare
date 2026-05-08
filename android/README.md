# TranscribeCare Android

Android app for TranscribeCare — an accessible healthcare transcription assistant built with Kotlin and Jetpack Compose.

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 35

## Run Locally

```bash
./gradlew assembleDebug
```

Install on a connected device or emulator:

```bash
./gradlew installDebug
```

## Run Tests

```bash
./gradlew test
```

## Deploy to Google Play Store

### 1. Generate a signing key

Create a release keystore (one-time setup):

```bash
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias transcribecare
```

Keep this file safe — you cannot recover it if lost.

### 2. Configure signing

Copy the example properties file and fill in your values:

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=release-keystore.jks
storePassword=your_store_password
keyAlias=transcribecare
keyPassword=your_key_password
```

> **Do not commit `keystore.properties` or `.jks` files to version control.**

### 3. Build the release bundle

Google Play requires the Android App Bundle (AAB) format:

```bash
./gradlew bundleRelease
```

The output AAB is located at:

```
app/build/outputs/bundle/release/app-release.aab
```

### 4. Verify the bundle (optional)

Use Google's `bundletool` to validate locally:

```bash
bundletool validate --bundle=app/build/outputs/bundle/release/app-release.aab
```

### 5. Create a Google Play Developer account

- Sign up at [play.google.com/console](https://play.google.com/console) ($25 one-time fee)
- Create a new app and select **Medical** as the category

### 6. Complete the store listing

Provide the following in Play Console:

| Item | Requirement |
|------|-------------|
| App name | TranscribeCare |
| Short description | Up to 80 characters |
| Full description | Up to 4000 characters |
| App icon | 512×512 PNG |
| Feature graphic | 1024×500 PNG |
| Phone screenshots | Minimum 2, recommended 4–8 |
| 7-inch tablet screenshots | Minimum 1 |

### 7. Complete the Data Safety form

Declare the following in Play Console → Policy → App content → Data safety:

| Data type | Collected | Shared | Purpose |
|-----------|-----------|--------|---------|
| Audio recordings | Yes (local only) | No | App functionality |
| Health data (transcriptions) | Yes (local only) | No | App functionality |

- No data is transmitted off-device unless the user enables optional Gemini AI features.
- Microphone permission is used only during active recording sessions.

### 8. Host and link the privacy policy

The privacy policy is hosted at:

```
https://transcribecare.com/PRIVACY_POLICY.md
```

Enter this URL in Play Console → Policy → App content → Privacy policy.

### 9. Complete the content rating questionnaire

In Play Console → Policy → App content → Content rating, complete the IARC questionnaire. TranscribeCare contains no violent, sexual, or gambling content — expect an **Everyone** rating.

### 10. Set up Play App Signing

When uploading your first AAB, enroll in [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756). Google manages the app signing key; you keep the upload key (your `release-keystore.jks`).

### 11. Upload and release

1. Go to Play Console → Release → Production → Create new release
2. Upload `app-release.aab`
3. Add release notes
4. Review and roll out

### Updating the app

For subsequent releases:

1. Increment `versionCode` and update `versionName` in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "1.1.0"
   ```
2. Build the bundle:
   ```bash
   ./gradlew bundleRelease
   ```
3. Upload the new AAB in Play Console and roll out

### Staged rollouts

Use staged rollouts to limit risk:

```
Production → Create new release → Roll out to 10% of users
```

Monitor crash reports and ANRs in Play Console before expanding to 100%.

## Project Structure

```
app/src/main/java/com/transcribecare/app/
├── data/          — Room database, DAOs, entities
├── model/         — Domain models
├── service/       — Audio, speech, sharing services
├── ui/
│   ├── navigation/ — Navigation graph
│   ├── screens/    — Composable screens
│   └── theme/      — Material 3 theming
└── viewmodel/     — ViewModels (MVVM)
```
