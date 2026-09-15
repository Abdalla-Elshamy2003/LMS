package com.manarah.community;

import com.manarah.community.ForumDtos.*;
import com.manarah.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private final ForumService service;

    public ForumController(ForumService service) {
        this.service = service;
    }

    @GetMapping("/topics")
    public List<TopicView> list(@AuthenticationPrincipal UserPrincipal actor,
                                @RequestParam(required = false) Long courseId) {
        return service.list(courseId, actor);
    }

    @GetMapping("/topics/{id}")
    public TopicDetail get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return service.get(id, actor);
    }

    @PostMapping("/topics")
    public TopicView createTopic(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateTopicRequest request) {
        return service.createTopic(actor, request);
    }

    @PostMapping("/topics/{id}/replies")
    public ReplyView addReply(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                              @Valid @RequestBody CreateReplyRequest request) {
        return service.addReply(actor, id, request);
    }

    @PostMapping("/topics/{id}/like")
    public TopicView toggleLike(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return service.toggleLike(actor, id);
    }
}
