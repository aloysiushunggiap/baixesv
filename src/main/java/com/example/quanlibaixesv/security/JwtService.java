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

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_CARD_ID = "cardId";
    private static final String CLAIM_TOKEN_VERSION = "ver";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

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

    public Date generateRefreshExpirationDate() {
        return new Date(System.currentTimeMillis() + refreshTokenExpiration);
    }

    public LocalDateTime generateRefreshExpirationDateTime() {
        return toLocalDateTime(generateRefreshExpirationDate());
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
                .setSubject(userDetails.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_SESSION_ID, sessionId)
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .signWith(getSignKey(), SignatureAlgorithm.HS256);

        if (cardId != null) {
            builder.claim(CLAIM_CARD_ID, cardId);
        }

        return builder.compact();
    }

    public String generateRefreshToken(String username,
                                       long tokenVersion,
                                       String sessionId,
                                       Date expirationDate) {
        return Jwts.builder()
                .setSubject(username)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_SESSION_ID, sessionId)
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get(CLAIM_ROLE, String.class);
    }

    public String extractCardId(String token) {
        return extractAllClaims(token).get(CLAIM_CARD_ID, String.class);
    }

    public String extractSessionId(String token) {
        return extractAllClaims(token).get(CLAIM_SESSION_ID, String.class);
    }

    public long extractTokenVersion(String token) {
        Object value = extractAllClaims(token).get(CLAIM_TOKEN_VERSION);
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

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(extractAllClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(extractAllClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isTokenValid(String token, UserDetails userDetails, long currentTokenVersion) {
        String username = extractUsername(token);
        long tokenVersion = extractTokenVersion(token);
        return username.equals(userDetails.getUsername())
                && tokenVersion == currentTokenVersion
                && isAccessToken(token)
                && !isTokenExpired(token);
    }

    public boolean isRefreshTokenValid(String token, String username, long currentTokenVersion) {
        long tokenVersion = extractTokenVersion(token);
        return username.equals(extractUsername(token))
                && tokenVersion == currentTokenVersion
                && isRefreshToken(token)
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
