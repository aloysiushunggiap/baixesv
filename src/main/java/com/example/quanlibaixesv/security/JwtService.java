package com.example.quanlibaixesv.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    // Access Token sống ngắn. Đơn vị: milliseconds. Ví dụ 300000 = 5 phút.
    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    // Refresh Token sống 10 phút theo yêu cầu.
    @Value("${app.jwt.refresh-expiration:600000}")
    private long refreshTokenExpiration;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public long getJwtExpirationMillis() {
        return jwtExpiration;
    }

    public long getRefreshTokenExpirationMillis() {
        return refreshTokenExpiration;
    }

    public Date generateExpirationDate() {
        return new Date(System.currentTimeMillis() + jwtExpiration);
    }

    public LocalDateTime generateRefreshExpirationDateTime() {
        return LocalDateTime.now().plusNanos(refreshTokenExpiration * 1_000_000L);
    }

    public LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    public String generateToken(UserDetails userDetails,
                                String role,
                                String cardId,
                                long tokenVersion,
                                String sessionId,
                                Date expirationDate) {
        var builder = Jwts.builder()
                .setId(sessionId) // jti = sessionId
                .setSubject(userDetails.getUsername())
                .claim("role", role)
                .claim("ver", tokenVersion)
                .claim("sid", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .signWith(getSignKey(), SignatureAlgorithm.HS256);

        if (cardId != null) {
            builder.claim("cardId", cardId);
        }

        return builder.compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractCardId(String token) {
        return extractAllClaims(token).get("cardId", String.class);
    }

    public String extractSessionId(String token) {
        Claims claims = extractAllClaims(token);
        String sessionId = claims.get("sid", String.class);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = claims.getId();
        }
        return sessionId;
    }

    public long extractTokenVersion(String token) {
        Object value = extractAllClaims(token).get("ver");
        if (value == null) {
            return -1L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token, UserDetails userDetails, long currentTokenVersion) {
        String username = extractUsername(token);
        long tokenVersion = extractTokenVersion(token);
        return username.equals(userDetails.getUsername())
                && tokenVersion == currentTokenVersion
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
