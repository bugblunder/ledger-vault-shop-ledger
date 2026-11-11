package com.khatavision.backend.security.jwt;

import com.khatavision.backend.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {
    private final Key signingKey;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtUtils(@Value("${app.jwtSecret}") String secret,
                    @Value("${app.jwtExpirationMs}") long expirationMs) {
        try {
            signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET must be a Base64-encoded key of at least 32 bytes.");
        }
        if (expirationMs <= 0 || expirationMs > 86400000L) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be between 1 and 86400000.");
        }
        this.expirationMs = expirationMs;
        parser = Jwts.parserBuilder().setSigningKey(signingKey).build();
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();
        Date now = new Date();
        return Jwts.builder().setSubject(principal.getId().toString()).claim("ver", 2).setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    public Long getUserIdFromJwtToken(String token) {
        return Long.valueOf(parser.parseClaimsJws(token).getBody().getSubject());
    }

    public boolean validateJwtToken(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            String subject = claims.getSubject();
            return Integer.valueOf(2).equals(claims.get("ver", Integer.class))
                    && claims.getExpiration() != null && subject != null && Long.parseLong(subject) > 0;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
