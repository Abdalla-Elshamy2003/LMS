package com.manarah.community;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public class ForumDtos {

    public record TopicView(Long id, Long courseId, String courseTitle, Long authorUserId, String authorName,
                            String title, String body, boolean pinned, int replyCount, int likeCount,
                            boolean likedByMe, Instant createdAt, Instant lastActivityAt) {
    }

    public record ReplyView(Long id, Long authorUserId, String authorName, String body, Instant createdAt) {
    }

    public record TopicDetail(TopicView topic, List<ReplyView> replies) {
    }

    public record CreateTopicRequest(Long courseId, @NotBlank String title, @NotBlank String body) {
    }

    public record CreateReplyRequest(@NotBlank String body) {
    }
}
