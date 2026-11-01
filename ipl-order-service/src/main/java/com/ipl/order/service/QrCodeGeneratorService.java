package com.ipl.order.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class QrCodeGeneratorService {

    private static final String HMAC_SECRET = "IPL_STADIUM_GATE_SECRET_KEY_2026";
    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;

    /**
     * Generates a tamper-proof cryptographically signed ticket payload.
     */
    public String generateSignedPayload(String ticketNumber, Long matchId, String seatNumber) {
        String data = "IPL2026|" + ticketNumber + "|" + matchId + "|" + (seatNumber != null ? seatNumber : "GEN");
        String signature = hmacSha256(data, HMAC_SECRET);
        return data + "|SIG:" + signature.substring(0, 16);
    }

    /**
     * Verifies the authenticity of the QR payload.
     */
    public boolean verifyPayload(String qrCodeData) {
        if (qrCodeData == null || !qrCodeData.contains("|SIG:")) {
            return false;
        }
        int sigIdx = qrCodeData.lastIndexOf("|SIG:");
        String data = qrCodeData.substring(0, sigIdx);
        String receivedSig = qrCodeData.substring(sigIdx + 5);

        String expectedSig = hmacSha256(data, HMAC_SECRET).substring(0, 16);
        return expectedSig.equals(receivedSig);
    }

    /**
     * Generates a Base64-encoded PNG image of the QR Code.
     */
    public String generateQrCodeBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] pngData = outputStream.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            log.error("Failed to render QR Code image: {}", e.getMessage());
            return "";
        }
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}

