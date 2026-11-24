package com.pockettheater.ui.screens

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * The camera screen of the QR scanner (see QrScanner). It only exists as its own class so the manifest can let it
 * follow the way the phone is held, instead of the scanner library's fixed sideways screen.
 */
class ScanActivity : CaptureActivity()
