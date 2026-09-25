package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.PasswordPolicy;
import com.manarah.notification.channel.ExternalMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Self-service password reset. Deliberately unauthenticated on both ends: the token in the link is
 * the only credential, which is why it is 256 bits of {@link SecureRandom}, stored hashed,
 * single-use, and short-lived.
 *
 * <p><b>Who this can actually reach:</b> a reset link needs somewhere to be sent. Students who
 * signed up themselves have a real email; staff seeded with @manarah.io addresses do too. Accounts
 * a teacher created from the academy screen get a synthetic {@code <uuid>@accounts.local} address
 * and usually no phone — there is nowhere to send their link, so for them the request is accepted
 * and quietly does nothing, and the message shown tells them to ask their teacher. That is a
 * property of how those accounts are created, not something this service can work around.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    /** Synthetic domain used by academy-created logins — never a real inbox. */
    private static final String PLACEHOLDER_DOMAIN = "@accounts.local";

    /** One message for every outcome — unknown user, no channel, or a link actually sent — so the
     *  endpoint cannot be used to find out which email addresses have accounts. */
    private static final String GENERIC =
            "لو الحساب موجود، هيوصلك رابط إعادة تعيين كلمة المرور خلال دقائق. "
            + "لو حسابك اتعمل من المستر مباشرة (من غير بريد إلكتروني)، كلّم المستر أو الإدارة لإعادة التعيين.";

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final Map<String, ExternalMessageSender> senders;
    private final String appUrl;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwords, List<ExternalMessageSender> senderList,
                                @Value("${manarah.public-app-url:}") String appUrl) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.senders = senderList.stream().collect(Collectors.toMap(ExternalMessageSender::channel, Function.identity()));
        this.appUrl = appUrl == null ? "" : appUrl.replaceAll("/+$", "");
    }

    public record ForgotRequest(String identifier) {}

    public record ResetRequest(String token, String password) {}

    public record GenericResult(String message) {}

    /**
     * Always succeeds from the caller's point of view — see {@link #GENERIC}.
     *
     * <p>Deliberately not {@code @Transactional}: sending the message means an SMTP handshake that
     * can take seconds, and holding a write
     * transaction open across that call would let one slow mail server stall every other write in
     * the app, so the token row is committed first and delivery happens after.
     */
    public GenericResult request(ForgotRequest req) {
        String id = req.identifier() == null ? "" : req.identifier().trim();
        if (id.isEmpty()) throw new BadRequestException("اكتب البريد الإلكتروني أو اسم المستخدم");

        // Email is unique per teacher space only; of the accounts sharing an address, reset the one last used.
        // Linked rows (a student's seat with another teacher) have no password of their own and are never picked.
        List<User> found = id.contains("@") ? users.findAllByEmailIgnoreCase(id)
                : users.findByUsernameIgnoreCase(id).map(List::of).orElse(List.of());
        User user = found.stream().filter(u -> u.getPrimaryUserId() == null && "ACTIVE".equals(u.getStatus()))
                .max(java.util.Comparator.comparing(User::getLastLoginAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
        if (user == null) return new GenericResult(GENERIC);

        // Any link issued earlier dies the moment a new one is asked for, so a forwarded old
        // message cannot be used after the fact.
        var previous = tokens.findByUserIdAndUsedAtIsNull(user.getId());
        previous.forEach(t -> t.setUsedAt(Instant.now()));
        tokens.saveAll(previous);

        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordResetToken row = new PasswordResetToken();
        row.setTenantId(user.getTenantId());
        row.setUserId(user.getId());
        row.setTokenHash(sha256(token));
        row.setExpiresAt(Instant.now().plus(TTL));
        row = tokens.save(row);

        // Committed first, then delivered — see the note on this method. The channel is recorded
        // afterwards because it isn't known until the send either succeeds or falls through.
        String channel = deliver(user, token);
        if (channel != null) {
            row.setChannel(channel);
            tokens.save(row);
        }
        return new GenericResult(GENERIC);
    }

    @Transactional
    public GenericResult reset(ResetRequest req) {
        PasswordPolicy.requireStrong(req.password());
        if (req.token() == null || req.token().isBlank())
            throw new BadRequestException("رابط إعادة التعيين غير صالح");

        PasswordResetToken row = tokens.findByTokenHash(sha256(req.token().trim()))
                .orElseThrow(() -> new BadRequestException("رابط إعادة التعيين غير صالح أو تم استخدامه من قبل"));
        if (row.getUsedAt() != null)
            throw new BadRequestException("تم استخدام هذا الرابط بالفعل — اطلب رابطاً جديداً");
        if (row.getExpiresAt().isBefore(Instant.now()))
            throw new BadRequestException("انتهت صلاحية الرابط — اطلب رابطاً جديداً");

        User user = users.findById(row.getUserId())
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new BadRequestException("الحساب غير متاح"));
        user.setPasswordHash(passwords.encode(req.password()));
        users.save(user);

        row.setUsedAt(Instant.now());
        tokens.save(row);
        return new GenericResult("تم تغيير كلمة المرور بنجاح — تقدر تسجّل الدخول دلوقتي.");
    }

    /** Tries email first, then WhatsApp; returns the channel that actually dispatched, or null. */
    private String deliver(User user, String token) {
        String link = appUrl + "/reset-password?token=" + token;
        String title = "إعادة تعيين كلمة المرور — مدارك";
        String body = "مرحباً " + user.getFullName() + "،\n\n"
                + "وصلنا طلب لإعادة تعيين كلمة المرور لحسابك على منصة مدارك.\n"
                + "افتح الرابط ده خلال 30 دقيقة عشان تختار كلمة مرور جديدة:\n\n"
                + link + "\n\n"
                + "لو مش إنت اللي طلبت ده، تجاهل الرسالة — كلمة المرور الحالية هتفضل زي ما هي.";

        String email = user.getEmail();
        if (email != null && !email.toLowerCase(Locale.ROOT).endsWith(PLACEHOLDER_DOMAIN)
                && dispatch("EMAIL", email, title, body)) return "EMAIL";
        if (user.getPhone() != null && !user.getPhone().isBlank()
                && dispatch("WHATSAPP", user.getPhone(), title, body)) return "WHATSAPP";

        // No live channel is configured (or the account has nowhere to receive a message). The link
        // would otherwise be unrecoverable, so it goes to the server log — the only place an
        // operator can still find it. Configure SMTP or WhatsApp and this branch stops being hit.
        // Never log the reset token/link: production log readers must not be able to take over
        // an account. Operators only need the account id and delivery diagnosis.
        log.warn("[PASSWORD RESET] no live delivery channel for user {}", user.getId());
        return null;
    }

    private boolean dispatch(String channel, String to, String title, String body) {
        ExternalMessageSender sender = senders.get(channel);
        try {
            return sender != null && sender.send(to, title, body);
        } catch (RuntimeException e) {
            log.warn("[PASSWORD RESET] {} delivery failed: {}", channel, e.toString());
            return false;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
