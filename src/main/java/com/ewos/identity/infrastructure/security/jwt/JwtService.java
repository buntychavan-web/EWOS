package com.ewos.identity.infrastructure.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Foundation JWT service. Issues and validates HS256-signed access/refresh tokens. No
 * authentication endpoint is wired up yet — Sprint 1 provides the primitives only.
 */
@Service
public final class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser =
                Jwts.parser().verifyWith(signingKey).requireIssuer(properties.issuer()).build();
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        return build(subject, claims, properties.accessTokenTtl());
    }

    public Jws<Claims> parse(String token) {
        return parser.parseSignedClaims(token);
    }

    private String build(String subject, Map<String, Object> claims, java.time.Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
