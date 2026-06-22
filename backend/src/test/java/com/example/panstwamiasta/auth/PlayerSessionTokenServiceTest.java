package com.example.panstwamiasta.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionTokenServiceTest {

    private PlayerSessionTokenService tokenService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-unit-tests-min-32-chars-long");
        props.setExpiryHours(24);
        tokenService = new PlayerSessionTokenService(props);
        tokenService.init();
    }

    @Test
    void issueAndParseToken() {
        UUID playerId = UUID.randomUUID();
        String token = tokenService.issueToken(playerId, "ABC123", "Marek", true);

        PlayerPrincipal principal = tokenService.parseToken(token);

        assertEquals(playerId, principal.playerId());
        assertEquals("ABC123", principal.roomCode());
        assertEquals("Marek", principal.nick());
        assertTrue(principal.hostHint());
    }

    @Test
    void parseInvalidTokenThrows() {
        assertThrows(InvalidPlayerTokenException.class, () -> tokenService.parseToken("not-a-jwt"));
    }
}
