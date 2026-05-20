package com.example.quanlibaixesv.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CardSignatureService {

    @Value("${app.card.allowed-drift-ms:10000}")
    private long allowedDriftMs;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String generateCardSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateSignature(String cardId, long timestamp, String cardSecret) {
        try {
            String payload = buildPayload(cardId, timestamp);

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(cardSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo chữ ký HMAC", e);
        }
    }

    public boolean verifySignature(String cardId, long timestamp, String signature, String cardSecret) {
        validateTimestamp(timestamp);

        String expectedSignature = generateSignature(cardId, timestamp, cardSecret);

        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    public void validateTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = Math.abs(now - timestamp);

        if (diff > allowedDriftMs) {
            throw new RuntimeException("Yêu cầu quẹt thẻ đã hết hạn hoặc thời gian không hợp lệ.");
        }
    }

    private String buildPayload(String cardId, long timestamp) {
        return cardId + "|" + timestamp;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}