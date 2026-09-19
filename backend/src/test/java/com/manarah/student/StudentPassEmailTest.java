package com.manarah.student;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.manarah.notification.channel.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StudentPassEmailTest {

    @Test
    void registrationEmailCarriesDetailsAndAScannableGateLink() throws Exception {
        SmtpEmailSender mailer = mock(SmtpEmailSender.class);
        var listener = new StudentPassEmail(mailer, "https://lms.example.com/");
        listener.onRegistered(new StudentPassEmail.StudentRegistered("ali@example.com", "علي <b>محمد</b>", "STD-00042",
                "الصف الثالث الثانوي", "01012345678", "عادي", "أكاديمية الرياضيات", "abc123token"));

        var png = ArgumentCaptor.forClass(byte[].class);
        var html = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendHtmlWithImage(anyString(), anyString(), anyString(), html.capture(), anyString(), png.capture(), anyString());

        assertThat(html.getValue()).contains("STD-00042", "01012345678", "ali@example.com", "cid:qr")
                .doesNotContain("<b>محمد</b>").contains("&lt;b&gt;");

        var image = ImageIO.read(new ByteArrayInputStream(png.getValue()));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels))));
        assertThat(decoded.getText()).isEqualTo("https://lms.example.com/app/gate/abc123token");
    }

    @Test
    void mailFailureNeverPropagatesToTheCaller() {
        SmtpEmailSender mailer = mock(SmtpEmailSender.class);
        org.mockito.Mockito.when(mailer.sendHtmlWithImage(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("smtp down"));
        new StudentPassEmail(mailer, "").onRegistered(new StudentPassEmail.StudentRegistered("a@b.co", "س", "STD-1", null, null, null, "م", "t"));
    }
}
