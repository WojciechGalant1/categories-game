package com.example.panstwamiasta.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class PlayerSessionTokenService {

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    public PlayerSessionTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(resolveKeyBytes(jwtProperties.getSecret()));
    }

    public String issueToken(UUID playerId, String roomCode, String nick, boolean hostHint) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getExpiryHours() * 3600L);

        return Jwts.builder()
                .subject(playerId.toString())
                .claim("room", roomCode)
                .claim("host", hostHint)
                .claim("nick", nick)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public PlayerPrincipal parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID playerId = UUID.fromString(claims.getSubject());
            String roomCode = claims.get("room", String.class);
            String nick = claims.get("nick", String.class);
            Boolean hostHint = claims.get("host", Boolean.class);

            if (roomCode == null || roomCode.isBlank()) {
                throw new InvalidPlayerTokenException("Missing room claim");
            }

            return new PlayerPrincipal(
                    playerId,
                    roomCode,
                    nick != null ? nick : "",
                    Boolean.TRUE.equals(hostHint));
        } catch (ExpiredJwtException e) {
            throw new InvalidPlayerTokenException("Token expired");
        } catch (SignatureException | MalformedJwtException | IllegalArgumentException e) {
            throw new InvalidPlayerTokenException("Invalid token");
        }
    }

    private static byte[] resolveKeyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be empty");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to UTF-8 bytes
        }
        byte[] utf8 = secret.getBytes(StandardCharsets.UTF_8);
        if (utf8.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits");
        }
        return utf8;
    }
}
