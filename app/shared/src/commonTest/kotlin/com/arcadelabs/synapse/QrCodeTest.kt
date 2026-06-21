package com.arcadelabs.synapse

import kotlin.test.Test
import qrcode.QRCode
import qrcode.raw.QRCodeProcessor

class QrCodeTest {
    @Test
    fun testQr() {
        val qr = QRCode.ofSquares().build("test-device-id")
        println("QR: $qr")
    }
}
