package com.manarah.security.auth;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.channel.SmtpEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.*;

@Service
public class EmailCodeService {
    private final EmailCodeStore store;
    private final SmtpEmailSender mail;
    private final UserRepository users;
    private final boolean requireRegistrationProof;
    public EmailCodeService(EmailCodeStore store, SmtpEmailSender mail, UserRepository users,
                           @Value("${manarah.security.email-code.require-registration-verification:false}") boolean required) {
        this.store = store; this.mail = mail; this.users = users; this.requireRegistrationProof = required;
    }
    public record RequestResult(String challengeId, long expiresInSeconds, int retryAfterSeconds, String message) {}
    public boolean available() { return mail.isDeliverable(); }
    public boolean registrationVerificationRequired() { return requireRegistrationProof; }

    public RequestResult request(String emailIn, String purpose) {
        String email = normalize(emailIn); purpose(purpose); requireDelivery();
        // Staff access cannot be opened with a student email code. Linked seats use their primary account.
        User user = "LOGIN".equals(purpose) ? student(email) : null;
        String code = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
        var row = store.issue(email, purpose, user == null ? null : user.getId(), code);
        // Same request response and delivery for existing/unknown addresses; account details are never disclosed.
        boolean delivered = mail.send(email, "كود الدخول إلى دروس",
                "كود التحقق الخاص بك على منصة دروس:\n\n" + code
                        + "\n\nالكود صالح لمدة ١٠ دقائق ويُستخدم مرة واحدة. لا تشاركه مع أي شخص."
                        + "\nلو مش إنت اللي طلبت الكود، تجاهل الرسالة.");
        store.delivery(row.getId(), delivered);
        if (!delivered) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "تعذّر إرسال الكود. حاول مرة أخرى بعد دقيقة");
        return new RequestResult(row.getId(), EmailCodeStore.TTL.toSeconds(), EmailCodeStore.RETRY_SECONDS,
                "الكود اتبعت على الإيميل. راجع الوارد والرسائل غير المرغوب فيها");
    }
    public EmailCodeStore.Verified verify(String id, String emailIn, String purpose, String code) {
        String email = normalize(emailIn); purpose(purpose);
        if (id == null || !id.matches("[a-fA-F0-9-]{36}") || code == null || !code.matches("[0-9]{6}"))
            throw new BadRequestException("اكتب الكود المكوّن من ٦ أرقام");
        var result = store.verify(id, email, purpose, code);
        if (result == null) throw new UnauthorizedException("الكود غير صحيح أو انتهت صلاحيته. اطلب كود جديد عند الحاجة");
        return result;
    }
    /** Called inside the registration transaction, before it can create a session or a student seat. */
    public boolean registrationProof(String email, String proof) {
        if (proof == null || proof.isBlank()) {
            if (requireRegistrationProof) throw new BadRequestException("أكد الإيميل بالكود الأول");
            return false;
        }
        if (!proof.matches("[a-zA-Z0-9_-]{43}")) throw new BadRequestException("تأكيد الإيميل غير صالح");
        store.consumeProof(normalize(email), proof);
        return true;
    }
    private User student(String email) {
        return users.findAllByEmailIgnoreCase(email).stream()
                .filter(u -> u.getPrimaryUserId() == null && u.getRole() == Role.STUDENT && "ACTIVE".equals(u.getStatus()))
                .max(Comparator.comparing(User::getLastLoginAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(User::getId)).orElse(null);
    }
    private void requireDelivery() {
        if (!available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "خدمة الإيميل غير متاحة حالياً. استخدم كلمة المرور أو تواصل مع الإدارة");
    }
    private static void purpose(String purpose) {
        if (!Set.of("LOGIN", "REGISTER").contains(Objects.toString(purpose, ""))) throw new BadRequestException("طلب الكود غير صالح");
    }
    static String normalize(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$") || value.endsWith(".local"))
            throw new BadRequestException("اكتب بريد إلكتروني صحيح");
        return value;
    }
}
