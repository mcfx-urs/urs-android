package ch.mcfx.urs.settings

import com.journeyapps.barcodescanner.CaptureActivity

// zxing-android-embedded's default CaptureActivity follows the device's
// sensor orientation, which opens landscape-first on most phones. Locking
// this subclass to portrait in the manifest keeps the QR scan UI upright,
// matching the rest of the app.
class PortraitCaptureActivity : CaptureActivity()
