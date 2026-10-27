# Sara Voice Lock

Say **"Hey Sara, lock my phone"** and the phone locks.
Say **"Hey Sara, unlock my phone"** and the phone wakes and unlocks (see limits below).

## Get the APK (no Android Studio needed)

1. Create a free GitHub account and a new empty repository.
2. Upload everything in this folder to it (drag and drop on the web page works). Keep the `.github` folder.
3. Open the repository's **Actions** tab. The "Build APK" run starts by itself (or click **Run workflow**).
4. After 3 to 5 minutes, open the finished run and download **SaraVoiceLock-apk** from Artifacts. Unzip it to get `app-debug.apk`.
5. Copy the APK to your phone, tap it, and allow "Install unknown apps" when asked.

## Or build with Android Studio

Open this folder in Android Studio, let it sync, then **Build > Build APK(s)**.
The file is `app/build/outputs/apk/debug/app-debug.apk`.

## First-time setup on the phone

Open the app and press the buttons in order:

1. Allow microphone and notifications
2. Enable Device Admin (this is what allows locking)
3. Start listening

Optional: allow background running so the phone does not stop the app to save battery.

## How unlock really works (Android limits)

- **Lock** works on any phone once Device Admin is enabled.
- **Unlock** can only fully unlock if your screen lock is **None or Swipe**.
  If you use a PIN, pattern, password, or fingerprint, Android does not let any app skip it.
  Sara will wake the screen and bring up the normal unlock prompt, and you finish by entering the PIN.
  This is a security rule built into Android.
- Listening while the screen is off depends on the phone brand. Some (Xiaomi, Oppo, Vivo, Samsung power saving) stop background microphone use. If "unlock" is not heard on a locked phone, turn off battery optimization for the app.
- Speech recognition may beep every few seconds on some phones, and the green microphone dot shows while listening. Both are normal for this method.
- Debug build is for testing. It is not for the Play Store.
