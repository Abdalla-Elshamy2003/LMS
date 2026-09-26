package com.manarah.center;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/** The secrets a center hands out: the token inside a card's QR and the code of a book reservation. */
@Component
public class CenterCodes {
    public static final String RESERVATION_PREFIX = "BK-";
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CenterStudentRepository students;
    private final CenterBookReservationRepository reservations;

    public CenterCodes(CenterStudentRepository students, CenterBookReservationRepository reservations) {
        this.students = students; this.reservations = reservations;
    }

    /** 128 random bits as 32 hex characters — unguessable, so the card's page needs no login. */
    public String cardToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (!students.existsByToken(token)) return token;
        }
        throw new IllegalStateException("could not generate a unique card token");
    }

    public static boolean looksLikeToken(String value) { return value != null && value.matches("[0-9a-f]{32}"); }

    /** "BK-7K3Q9M" */
    public String reservationCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(RESERVATION_PREFIX);
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String code = sb.toString();
            if (!reservations.existsByCode(code)) return code;
        }
        throw new IllegalStateException("could not generate a unique reservation code");
    }

    public static String normalizeReservation(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (!v.isEmpty() && !v.startsWith(RESERVATION_PREFIX)) v = RESERVATION_PREFIX + v.replaceFirst("^BK", "");
        return v;
    }
}
