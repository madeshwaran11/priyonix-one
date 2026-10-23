package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtServiceTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("JWT_SECRET", SECRET);
        jwtService = new JwtService(environment);
    }

    private SecretKey signingKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(SECRET);
        } catch (io.jsonwebtoken.io.DecodingException ex) {
            keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Test
    void generatesTokenWithCorrectClaims() {
        String token = jwtService.generateToken(
                "admin@example.com", "ADMIN");

        Claims claims = jwtService.validateToken(token);

        assertEquals("admin@example.com", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void rejectsExpiredToken() {
        Date now = new Date();
        String expiredToken = Jwts.builder()
                .subject("admin@example.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date(now.getTime() - 10 * 60 * 1000L))
                .expiration(new Date(now.getTime() - 5 * 60 * 1000L))
                .signWith(signingKey())
                .compact();

        assertThrows(ExpiredJwtException.class,
                () -> jwtService.validateToken(expiredToken));
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(JwtException.class,
                () -> jwtService.validateToken("not.a-token"));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-completely-different-32-byte-secret-key!!"
                        .getBytes(StandardCharsets.UTF_8));

        String forgedToken = Jwts.builder()
                .subject("admin@example.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis() + 15 * 60 * 1000L))
                .signWith(otherKey)
                .compact();

        assertThrows(JwtException.class,
                () -> jwtService.validateToken(forgedToken));
    }

    @Test
    void startupFailsFastWhenSecretMissing() {
        MockEnvironment environment = new MockEnvironment();

        assertThrows(IllegalStateException.class,
                () -> new JwtService(environment));
    }

    @Test
    void startupFailsWhenSecretTooShort() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("JWT_SECRET", "c2hvcnQ="); // "short"

        assertThrows(IllegalStateException.class,
                () -> new JwtService(environment));
    }
}
