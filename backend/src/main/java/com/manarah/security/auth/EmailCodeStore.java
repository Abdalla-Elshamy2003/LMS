package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Short transactions; delivery runs outside them. Row locks make verification/consumption single-use. */
@Service
public class EmailCodeStore {
    public static final Duration TTL = Duration.ofMinutes(10);
    public static final int RETRY_SECONDS = 60;
    private final EmailLoginCodeRepository codes;
    private final String secret;
    public EmailCodeStore(EmailLoginCodeRepository codes, @Value("${manarah.security.jwt.secret}") String secret) {
        this.codes = codes; this.secret = secret;
    }

    @Transactional
    public EmailLoginCode issue(String email, String purpose, Long userId, String code) {
        // Same email, even across purposes: concurrent requests cannot evade the resend/mail limits.
        codes.lockEmail(ByteBuffer.wrap(hashBytes("email-code-rate:" + email)).getLong());
        Instant now = Instant.now();
        var latest = codes.findFirstByEmailOrderByCreatedAtDesc(email);
        if (latest.isPresent() && latest.get().getCreatedAt().plusSeconds(RETRY_SECONDS).isAfter(now))
            throw new TooManyRequestsException("استنى دقيقة قبل ما تطلب كود جديد");
        if (codes.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1))) >= 5)
            throw new TooManyRequestsException("طلبات كود كثيرة. حاول مرة أخرى بعد ساعة");
        latest.ifPresent(previous -> { previous.setUsedAt(now); codes.save(previous); });
        var row = new EmailLoginCode();
        row.setId(UUID.randomUUID().toString()); row.setEmail(email); row.setPurpose(purpose);
        row.setUserId(userId); row.setCreatedAt(now); row.setExpiresAt(now.plus(TTL));
        row.setCodeHash(codeHash(row.getId(), email, purpose, code));
        return codes.save(row);
    }

    @Transactional
    public void delivery(String id, boolean accepted) {
        codes.locked(id).ifPresent(row -> {
            if (accepted) row.setDeliveredAt(Instant.now()); else row.setUsedAt(Instant.now());
            codes.save(row);
        });
    }

    public record Verified(Long userId, String verificationToken) {}

    /** Failure is returned, not thrown here: rejected attempts must still commit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Verified verify(String id, String email, String purpose, String code) {
        var row = codes.locked(id).orElse(null);
        Instant now = Instant.now();
        if (row == null || !row.getEmail().equals(email) || !row.getPurpose().equals(purpose)
                || row.getUsedAt() != null || row.getVerifiedAt() != null || row.getDeliveredAt() == null
                || !row.getExpiresAt().isAfter(now) || row.getAttempts() >= 5) return null;
        row.setAttempts(row.getAttempts() + 1);
        boolean valid = MessageDigest.isEqual(row.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                codeHash(id, email, purpose, code).getBytes(StandardCharsets.US_ASCII));
        if (!valid) { codes.save(row); return null; }
        row.setVerifiedAt(now);
        String proof = null;
        if ("REGISTER".equals(purpose)) {
            byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
            proof = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            row.setProofHash(hash(proof));
        } else row.setUsedAt(now);
        codes.save(row);
        return new Verified(row.getUserId(), proof);
    }

    /** Joins the registration transaction: a failed signup doesn't destroy its verified proof. */
    @Transactional
    public void consumeProof(String email, String token) {
        var row = codes.proof(hash(token)).orElse(null);
        Instant now = Instant.now();
        if (row == null || !row.getEmail().equals(email) || !"REGISTER".equals(row.getPurpose())
                || row.getVerifiedAt() == null || row.getUsedAt() != null || !row.getExpiresAt().isAfter(now))
            throw new BadRequestException("تأكيد الإيميل انتهى أو اتستخدم. اطلب كود جديد");
        row.setUsedAt(now); codes.save(row);
    }

    private String codeHash(String id, String email, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("email-code:" + id + ":" + email + ":" + purpose + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Email code hashing unavailable", e); }
    }
    private static byte[] hashBytes(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String hash(String value) { return HexFormat.of().formatHex(hashBytes(value)); }
}
