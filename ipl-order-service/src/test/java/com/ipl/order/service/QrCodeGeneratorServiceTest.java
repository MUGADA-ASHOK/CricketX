package com.ipl.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorServiceTest {

    private QrCodeGeneratorService qrCodeGeneratorService;

    @BeforeEach
    void setUp() {
        qrCodeGeneratorService = new QrCodeGeneratorService();
    }

    @Test
    @DisplayName("Should generate authentic signed payload and verify successfully")
    void testGenerateAndVerifySignedPayload_Success() {
        String ticketNumber = "TKT-2026-CSK-RCB-001";
        Long matchId = 1L;
        String seatNumber = "VIP-A-1";

        String payload = qrCodeGeneratorService.generateSignedPayload(ticketNumber, matchId, seatNumber);

        assertThat(payload).isNotNull();
        assertThat(payload).contains("IPL2026|" + ticketNumber + "|1|" + seatNumber);
        assertThat(payload).contains("|SIG:");

        boolean isValid = qrCodeGeneratorService.verifyPayload(payload);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should detect tampered or forged QR payloads and reject")
    void testTamperedPayload_Rejected() {
        String ticketNumber = "TKT-2026-CSK-RCB-001";
        String payload = qrCodeGeneratorService.generateSignedPayload(ticketNumber, 1L, "VIP-A-1");

        // Tamper with ticket number in payload
        String tamperedPayload = payload.replace("CSK-RCB", "KKR-MI");

        boolean isValid = qrCodeGeneratorService.verifyPayload(tamperedPayload);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject malformed QR payload without HMAC signature")
    void testMissingSignature_Rejected() {
        String malformed = "IPL2026|TKT-FAKE|1|VIP-A-1";
        boolean isValid = qrCodeGeneratorService.verifyPayload(malformed);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should render Base64 PNG image for valid QR payload")
    void testGenerateQrCodeBase64_NotEmpty() {
        String payload = qrCodeGeneratorService.generateSignedPayload("TKT-123", 1L, "PAV-B-5");
        String base64Image = qrCodeGeneratorService.generateQrCodeBase64(payload);

        assertThat(base64Image).startsWith("data:image/png;base64,");
        assertThat(base64Image.length()).isGreaterThan(100);
    }
}

