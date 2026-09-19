package com.manarah.student;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.manarah.notification.channel.SmtpEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Emails a newly registered student their gate-pass QR together with their profile details.
 *
 * <p>The QR carries the same {@code /app/gate/<token>} link the registration screen shows, so a
 * staff scan resolves through the existing pass lookup; the personal details sit in the email body
 * rather than inside the QR, which keeps them out of anything that gets screenshotted or forwarded.
 * Sending is asynchronous and after-commit: a slow or failing SMTP server can never fail or delay a
 * registration.
 */
@Component
public class StudentPassEmail {

    private static final Logger log = LoggerFactory.getLogger(StudentPassEmail.class);

    public record StudentRegistered(String email, String fullName, String code, String grade, String phone,
                                    String educationType, String academyName, String passToken) {}

    private final SmtpEmailSender mailer;
    private final String publicAppUrl;

    public StudentPassEmail(SmtpEmailSender mailer, @Value("${manarah.public-app-url:}") String publicAppUrl) {
        this.mailer = mailer;
        this.publicAppUrl = publicAppUrl == null ? "" : publicAppUrl.trim().replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener
    public void onRegistered(StudentRegistered e) {
        try {
            String qrText = publicAppUrl.isEmpty() ? e.passToken() : publicAppUrl + "/app/gate/" + e.passToken();
            byte[] png = qrPng(qrText, 480);
            mailer.sendHtmlWithImage(e.email(), "بطاقة دخولك إلى " + e.academyName(), text(e), html(e), "qr", png,
                    "pass-" + e.code() + ".png");
        } catch (Exception ex) {
            log.warn("[EMAIL] could not prepare the pass email for '{}': {}", e.email(), ex.toString());
        }
    }

    static byte[] qrPng(String text, int size) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2,
                        EncodeHintType.CHARACTER_SET, "UTF-8"));
        var image = new java.awt.image.BufferedImage(matrix.getWidth(), matrix.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++)
            for (int y = 0; y < matrix.getHeight(); y++) image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String text(StudentRegistered e) {
        return "أهلاً " + e.fullName() + "،\n\nتم إنشاء حسابك في " + e.academyName() + ".\n"
                + "كود الطالب: " + e.code() + "\n"
                + "الصف: " + dash(e.grade()) + "\n"
                + "الهاتف: " + dash(e.phone()) + "\n"
                + "نظام التعليم: " + dash(e.educationType()) + "\n"
                + "البريد: " + e.email() + "\n\n"
                + "بطاقة الدخول (QR) مرفقة بهذه الرسالة — اعرضها عند البوابة.";
    }

    private static String html(StudentRegistered e) {
        return "<div dir=\"rtl\" style=\"font-family:Tahoma,Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937\">"
                + "<h2 style=\"margin:0 0 8px\">أهلاً " + h(e.fullName()) + "</h2>"
                + "<p style=\"margin:0 0 16px\">تم إنشاء حسابك في <b>" + h(e.academyName()) + "</b>. هذه بطاقة دخولك — اعرض الـ QR عند البوابة.</p>"
                + "<div style=\"text-align:center;margin:16px 0\"><img src=\"cid:qr\" alt=\"QR\" width=\"240\" height=\"240\"></div>"
                + "<table style=\"width:100%;border-collapse:collapse\">"
                + row("كود الطالب", e.code()) + row("الاسم", e.fullName()) + row("الصف", dash(e.grade()))
                + row("الهاتف", dash(e.phone())) + row("نظام التعليم", dash(e.educationType())) + row("البريد", e.email())
                + "</table>"
                + "<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">لا تشارك هذه البطاقة مع أحد؛ من يملك الـ QR يستطيع تسجيل الدخول باسمك.</p>"
                + "</div>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:6px 0;color:#6b7280\">" + label + "</td><td style=\"padding:6px 0;font-weight:bold\">" + h(value) + "</td></tr>";
    }

    private static String dash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private static String h(String v) {
        return HtmlUtils.htmlEscape(v == null ? "" : v);
    }
}
