# Open Media

Lightweight Android media player. Package: `com.openmedia.app`

* Kotlin + plain Android views + Media3 ExoPlayer (HLS/DASH included)
* minSdk 21, targetSdk 34
* Build: push to GitHub -> Actions -> **Android CI** -> download the APK artifact
  (`OpenMedia-release` is the small, minified one; `OpenMedia-debug` is for debugging).

Before publishing, change `FEEDBACK_EMAIL` in `app/src/main/java/com/openmedia/app/Core.kt`.
