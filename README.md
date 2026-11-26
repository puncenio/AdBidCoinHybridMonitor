# AdBidCoin Hybrid Monitor — Android source project

This is an Android Studio source project, not a prebuilt APK. It implements an opt-in Android MediaProjection OCR monitor with a floating overlay, sound, vibration, candidate-code notification, and manual copy.

## Important limitations
- The OCR detector uses a generic alphanumeric heuristic because the official Art Drop code format has not been verified. It may show false positives or miss a different format.
- OCR only detects content visible on the device screen. It does not read AdBidCoin's private APIs, bypass access controls, solve CAPTCHA, or submit claims.
- Android MediaProjection requires the user to approve screen capture each time. Keep Brave visible for best results; Android may pause capture when the app/device changes state.
- Overlay and notification permissions must be granted by the user.
- DOM monitoring in Brave is separate. This project does not inject JavaScript into Brave or guarantee a native bridge between the bookmarklet and this app.
- No automatic refresh, automatic claim, wallet connection, signing, or verification bypass is included.

## Build APK
1. Install Android Studio (recent stable version) on a computer.
2. Open this folder as an existing project.
3. Allow Gradle sync and install Android SDK Platform 35 if prompted.
4. Connect an Android device or use an emulator for testing.
5. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Install the generated debug APK on the Infinix. For distribution, create your own signed release build.

## Use on Infinix Android 15
1. Open the app and grant **Display over other apps** permission.
2. Grant notifications if Android asks.
3. Tap **Buka halaman klaim AdBidCoin** and sign in yourself in Brave if needed.
4. Return to the monitor app and tap **Mulai Live OCR**.
5. Approve Android's screen-capture prompt.
6. Switch to Brave and keep the claim page visible.
7. When text resembling a code is detected, the overlay and notification alert. Verify the code yourself and use the site's normal manual claim flow.
8. Tap **Hentikan monitoring** when finished.

## Privacy
Screen frames are processed locally on-device by ML Kit. This sample does not upload screenshots or codes to a server. Do not capture private messages, passwords, wallet seed phrases, or other sensitive screens.


## Cloud build using GitHub Actions (phone only)
1. Create a new GitHub repository with the name `AdBidCoinHybridMonitor`.
2. Extract this ZIP on your Android phone.
3. In the repository, use **Add file → Upload files** and upload the extracted project contents, including the hidden `.github/workflows/android-apk.yml` workflow file. If the phone's file picker hides dot-folders, use GitHub's **Add file → Create new file** to create `.github/workflows/android-apk.yml`, then paste the workflow from this ZIP's workflow file.
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab, select **Build Android APK**, and run it if it has not started automatically.
6. When the run completes, open it and download the artifact named `AdBidCoin-Hybrid-Monitor-debug`. Extract the artifact ZIP to get `app-debug.apk`, then install it on the phone.

The APK is a debug build for personal testing, not a Play Store release. Android may display an install-from-unknown-apps prompt.
