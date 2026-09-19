package com.manarah.student.pass;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** Renders text as a QR code PNG - used where the browser cannot draw it for us, i.e. in an email. */
public final class QrCodeImages {
    private static final int DARK = 0xFF0C4A6E;  // same brand colour the on-screen QR uses
    private static final int LIGHT = 0xFFFFFFFF;

    private QrCodeImages() {}

    public static byte[] png(String text, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++)
                for (int y = 0; y < matrix.getHeight(); y++)
                    image.setRGB(x, y, matrix.get(x, y) ? DARK : LIGHT);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Could not render QR code", e);
        }
    }
}
