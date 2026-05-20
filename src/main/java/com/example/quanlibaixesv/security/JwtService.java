package com.example.quanlibaixesv.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(UserDetails userDetails,
                                String role,
                                String cardId,
                                long tokenVersion,
                                String sessionId) {
        var builder = Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("role", role)
                .claim("ver", tokenVersion)
                .claim("sid", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
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
        return extractAllClaims(token).get("sid", String.class);
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

    public boolean isTokenValid(String token, UserDetails userDetails, long currentTokenVersion) {
        String username = extractUsername(token);
        long tokenVersion = extractTokenVersion(token);
        return username.equals(userDetails.getUsername())
                && tokenVersion == currentTokenVersion
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
