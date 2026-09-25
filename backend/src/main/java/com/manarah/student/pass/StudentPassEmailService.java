package com.manarah.student.pass;

import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.channel.SmtpEmailSender;
import com.manarah.student.InstitutionNameResolver;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emails a student their QR pass right after they register: the QR image plus their own details, so
 * they have it even before opening the app. The QR encodes the public verification URL, which shows
 * only the safe public profile.
 *
 * <p>Deliberately left out of the email: the password (never sent anywhere) and the national id and
 * phone number - email is not a private channel, and the student already knows them.
 *
 * <p>Needs {@code manarah.public-app-url} to build an absolute link (a QR pointing at a relative path
 * is useless from an inbox) and working SMTP; without either it logs and does nothing.
 */
@Service
public class StudentPassEmailService {
    private static final Logger log = LoggerFactory.getLogger(StudentPassEmailService.class);
    private static final String QR_CID = "student-qr";

    private final StudentRepository students;
    private final UserRepository users;
    private final StudentPassTokens passTokens;
    private final InstitutionNameResolver institutions;
    private final SmtpEmailSender mail;
    private final String appUrl;

    public StudentPassEmailService(StudentRepository students, UserRepository users, StudentPassTokens passTokens,
                                   InstitutionNameResolver institutions, SmtpEmailSender mail,
                                   @Value("${manarah.public-app-url:}") String appUrl) {
        this.students = students;
        this.users = users;
        this.passTokens = passTokens;
        this.institutions = institutions;
        this.mail = mail;
        this.appUrl = appUrl == null ? "" : appUrl.trim().replaceAll("/+$", "");
    }

    /** Whether a registration email can actually be delivered in this deployment. */
    public boolean isConfigured() {
        return !appUrl.isEmpty() && mail.isDeliverable();
    }

    /** Sends the pass to the student's account email. Returns whether a message was delivered. */
    public boolean sendPass(Long tenantId, Long studentId) {
        if (!isConfigured()) {
            log.info("student_pass_email outcome=skipped reason=email_or_public_url_not_configured studentId={}", studentId);
            return false;
        }
        Student student = students.findByTenantIdAndId(tenantId, studentId).orElse(null);
        String email = student == null || student.getUserId() == null ? null
                : users.findById(student.getUserId()).map(u -> u.getEmail()).orElse(null);
        if (email == null || email.isBlank()) {
            log.info("student_pass_email outcome=skipped reason=no_email studentId={}", studentId);
            return false;
        }

        String verifyUrl = appUrl + "/student/verify/" + passTokens.ensure(student).getPassToken();
        String institution = institutions.nameOf(tenantId).orElse("دروس");
        Map<String, String> details = details(student, email, institution);

        boolean delivered = mail.sendHtml(email, "كود الـ QR الخاص بك — " + institution,
                plainText(student, details, verifyUrl), html(student, details, verifyUrl, institution),
                new SmtpEmailSender.InlineImage(QR_CID, QrCodeImages.png(verifyUrl, 360)));
        log.info("student_pass_email outcome={} studentId={}", delivered ? "sent" : "failed", studentId);
        return delivered;
    }

    private static Map<String, String> details(Student s, String email, String institution) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("الاسم", s.getFullName());
        rows.put("كود الطالب", s.getCode());
        putIfPresent(rows, "الصف", s.getGrade());
        putIfPresent(rows, "المرحلة", s.getGradeLevel());
        putIfPresent(rows, "نظام التعليم", s.getEducationType());
        rows.put("الجهة", institution);
        rows.put("البريد الإلكتروني للدخول", email);
        return rows;
    }

    private static void putIfPresent(Map<String, String> rows, String label, String value) {
        if (value != null && !value.isBlank()) rows.put(label, value);
    }

    private static String plainText(Student s, Map<String, String> details, String verifyUrl) {
        StringBuilder text = new StringBuilder("أهلاً ").append(s.getFullName())
                .append("،\nتم إنشاء حسابك بنجاح. دي بياناتك:\n\n");
        details.forEach((label, value) -> text.append(label).append(": ").append(value).append('\n'));
        return text.append("\nكود الـ QR الخاص بك (افتحه من الموبايل أو اعرضه عند الحاجة):\n").append(verifyUrl)
                .append("\n\nلو ماسجلتش أنت الحساب ده، تجاهل الرسالة.\n").toString();
    }

    private static String html(Student s, Map<String, String> details, String verifyUrl, String institution) {
        StringBuilder rows = new StringBuilder();
        details.forEach((label, value) -> rows.append("<tr><td style=\"padding:6px 0;color:#64748b\">")
                .append(HtmlUtils.htmlEscape(label)).append("</td><td style=\"padding:6px 0;font-weight:700;color:#0f172a\">")
                .append(HtmlUtils.htmlEscape(value)).append("</td></tr>"));
        return "<div dir=\"rtl\" style=\"font-family:Tahoma,Arial,sans-serif;max-width:480px;margin:auto;padding:24px;"
                + "border:1px solid #e2e8f0;border-radius:16px;text-align:right\">"
                + "<h2 style=\"margin:0 0 4px;color:#0c4a6e\">أهلاً " + HtmlUtils.htmlEscape(s.getFullName()) + "</h2>"
                + "<p style=\"margin:0 0 16px;color:#64748b\">تم إنشاء حسابك في " + HtmlUtils.htmlEscape(institution) + " بنجاح.</p>"
                + "<table style=\"width:100%;border-collapse:collapse;font-size:14px\">" + rows + "</table>"
                + "<div style=\"text-align:center;margin:24px 0 8px\">"
                + "<img src=\"cid:" + QR_CID + "\" alt=\"QR\" width=\"220\" height=\"220\" style=\"border:1px solid #e2e8f0;border-radius:12px\">"
                + "<p style=\"font-size:12px;color:#64748b\">امسح الكود ده أو اعرضه عند الحاجة لتأكيد هويتك</p>"
                + "<a href=\"" + HtmlUtils.htmlEscape(verifyUrl) + "\" style=\"color:#0369a1;font-size:13px\">فتح صفحة التحقق</a></div>"
                + "<p style=\"font-size:12px;color:#94a3b8;margin-top:20px\">لو ماسجلتش أنت الحساب ده، تجاهل الرسالة.</p></div>";
    }
}
