package com.manarah.media;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cover images for courses and video lessons. Teachers and content staff upload; anyone can view,
 * because the image appears on the public teacher page. Every upload is decoded and re-checked, so a
 * renamed script or a decompression bomb is rejected rather than stored.
 */
@RestController
@Tag(name = "Images")
public class ImageController {

    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;

    private final PublicImageRepository images;

    public ImageController(PublicImageRepository images) {
        this.images = images;
    }

    @PostMapping("/api/images")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','CONTENT_MANAGER')")
    @Transactional
    public Map<String, Object> upload(@AuthenticationPrincipal UserPrincipal actor, @RequestParam MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw new BadRequestException("اختر صورة أصغر من 3 ميجابايت");
        String type;
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("ملف الصورة غير صالح");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) throw new BadRequestException("أبعاد الصورة كبيرة جداً");
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new BadRequestException("الصورة يجب أن تكون PNG أو JPG");
                type = format.equals("png") ? "image/png" : "image/jpeg";
            } finally {
                reader.dispose();
            }
        }
        var image = new PublicImage();
        image.setTenantId(TenantContext.require());
        image.setContentType(type);
        image.setData(Base64.getEncoder().encodeToString(file.getBytes()));
        image.setCreatedBy(actor.getId());
        images.save(image);
        return Map.of("id", image.getId(), "url", "/api/public/images/" + image.getId());
    }

    @GetMapping("/api/public/images/{id}")
    public ResponseEntity<byte[]> view(@PathVariable Long id) {
        var image = images.findById(id).orElseThrow(() -> NotFoundException.of("الصورة", id));
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(Base64.getDecoder().decode(image.getData()));
    }
}
