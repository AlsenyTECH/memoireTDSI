package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.tdsi.memotheque.util.Totp;

/** TOTP : vecteurs officiels de la RFC 6238 (annexe B) et Base32 de la RFC 4648. */
class TotpTest {

    private static final byte[] SECRET_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void vecteursRfc6238() {
        assertEquals("94287082", Totp.code(SECRET_RFC, Totp.pas(59), 8));
        assertEquals("07081804", Totp.code(SECRET_RFC, Totp.pas(1111111109L), 8));
        assertEquals("14050471", Totp.code(SECRET_RFC, Totp.pas(1111111111L), 8));
        assertEquals("89005924", Totp.code(SECRET_RFC, Totp.pas(1234567890L), 8));
        assertEquals("69279037", Totp.code(SECRET_RFC, Totp.pas(2000000000L), 8));
        assertEquals("65353130", Totp.code(SECRET_RFC, Totp.pas(20000000000L), 8));
        assertEquals("287082", Totp.code(SECRET_RFC, Totp.pas(59)));
    }

    @Test
    void fenetreDeToleranceDUnPas() {
        long t = 1_700_000_000L;
        String code = Totp.code(SECRET_RFC, Totp.pas(t));
        assertEquals(Totp.pas(t), Totp.verifier(SECRET_RFC, code, t));
        assertEquals(Totp.pas(t), Totp.verifier(SECRET_RFC, code, t + 30), "téléphone en retard de 30 s");
        assertEquals(Totp.pas(t), Totp.verifier(SECRET_RFC, code.substring(0, 3) + " " + code.substring(3), t));
        assertEquals(-1, Totp.verifier(SECRET_RFC, code, t + 90), "trop ancien");
        assertEquals(-1, Totp.verifier(SECRET_RFC, "12345", t));
        assertEquals(-1, Totp.verifier(SECRET_RFC, "abcdef", t));
        assertEquals(-1, Totp.verifier(SECRET_RFC, null, t));
    }

    @Test
    void base32Rfc4648() {
        assertEquals("", Totp.base32(new byte[0]));
        assertEquals("MY", Totp.base32("f".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("MZXW6", Totp.base32("foo".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("MZXW6YTBOI", Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII)));
        assertArrayEquals("foobar".getBytes(StandardCharsets.US_ASCII), Totp.depuisBase32("mzxw 6ytb oi======"));
        byte[] s = Totp.nouveauSecret();
        assertEquals(20, s.length);
        assertArrayEquals(s, Totp.depuisBase32(Totp.lisible(s)));
    }

    @Test
    void lienDEnrolement() {
        String uri = Totp.uri("MémoTDSI UCAD", "admin@mcs", "foobar".getBytes(StandardCharsets.US_ASCII));
        assertTrue(uri.startsWith("otpauth://totp/M%C3%A9moTDSI%20UCAD:admin%40mcs?secret=MZXW6YTBOI"), uri);
        assertTrue(uri.contains("&issuer=M%C3%A9moTDSI%20UCAD"));
        assertTrue(uri.contains("digits=6") && uri.contains("period=30"));
    }
}
