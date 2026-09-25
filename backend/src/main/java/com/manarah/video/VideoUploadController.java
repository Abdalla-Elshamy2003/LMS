package com.manarah.video;

import com.manarah.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/** Direct video uploads: start (one URL per part), send parts (local store only), complete, cancel. */
@RestController
@RequestMapping("/api/videos/uploads")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
public class VideoUploadController {
    private final VideoUploadService uploads;

    public VideoUploadController(VideoUploadService uploads) { this.uploads = uploads; }

    @PostMapping
    public VideoUploadService.StartResult start(@AuthenticationPrincipal UserPrincipal actor, @RequestBody VideoUploadService.StartRequest body) {
        return uploads.start(actor, body);
    }

    @PutMapping("/{id}/parts/{partNumber}")
    public ResponseEntity<Map<String, Object>> part(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                    @PathVariable int partNumber, HttpServletRequest request) throws IOException {
        String etag = uploads.writePart(actor, id, partNumber, request.getInputStream());
        return ResponseEntity.ok().header("ETag", etag).body(Map.of("partNumber", partNumber, "etag", etag));
    }

    @PostMapping("/{id}/complete")
    public VideoUploadService.Completed complete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                 @RequestBody VideoUploadService.CompleteRequest body) {
        return uploads.complete(actor, id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> abort(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        uploads.abort(actor, id);
        return Map.of("id", id, "status", VideoAsset.ABORTED);
    }
}
