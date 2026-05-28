package com.example.quanlibaixesv.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class RefreshTokenHashService {

    @Value("${app.jwt.secret}")
    private String secret;

    private static final String HMAC_ALGORITHM = "HmacSHA256";


    public String hash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Refresh Token không được để trống.");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);

            byte[] hashBytes = mac.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Không thể hash Refresh Token.", e);
        }
    }

    public boolean matches(String rawRefreshToken, String storedHash) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank() || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        String rawHash = hash(rawRefreshToken);
        return MessageDigest.isEqual(
                rawHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
