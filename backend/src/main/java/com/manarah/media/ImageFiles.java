package com.manarah.media;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * The one check every public image upload goes through: at most 3 MB, a real PNG or JPEG once decoded (not just by
 * its name or declared type), and not so many pixels that decoding it could exhaust memory.
 */
public final class ImageFiles {
    private ImageFiles() {}

    public static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;

    /** The file's real content type, "image/png" or "image/jpeg"; anything else is refused. */
    public static String validatedType(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw new BadRequestException("اختر صورة أصغر من 3 ميجابايت");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("ملف الصورة غير صالح");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) throw new BadRequestException("أبعاد الصورة كبيرة جداً");
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new BadRequestException("الصورة يجب أن تكون PNG أو JPG");
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally {
                reader.dispose();
            }
        }
    }
}
