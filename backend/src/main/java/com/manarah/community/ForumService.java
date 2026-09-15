package com.manarah.community;

import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.community.ForumDtos.*;
import com.manarah.community.domain.ForumLike;
import com.manarah.community.domain.ForumReply;
import com.manarah.community.domain.ForumTopic;
import com.manarah.community.repo.ForumLikeRepository;
import com.manarah.community.repo.ForumReplyRepository;
import com.manarah.community.repo.ForumTopicRepository;
import com.manarah.course.repo.CourseRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ForumService {

    private final ForumTopicRepository topics;
    private final ForumReplyRepository replies;
    private final ForumLikeRepository likes;
    private final CourseRepository courses;

    public ForumService(ForumTopicRepository topics, ForumReplyRepository replies, ForumLikeRepository likes,
                        CourseRepository courses) {
        this.topics = topics;
        this.replies = replies;
        this.likes = likes;
        this.courses = courses;
    }

    public List<TopicView> list(Long courseId, UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        var rows = courseId != null
                ? topics.findByTenantIdAndCourseIdOrderByPinnedDescLastActivityAtDesc(tenantId, courseId)
                : topics.findByTenantIdOrderByPinnedDescLastActivityAtDesc(tenantId);
        return rows.stream().map(t -> toView(t, actor)).toList();
    }

    public TopicDetail get(Long topicId, UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        ForumTopic t = topics.findByTenantIdAndId(tenantId, topicId).orElseThrow(() -> NotFoundException.of("الموضوع", topicId));
        List<ReplyView> rv = replies.findByTenantIdAndTopicIdOrderByCreatedAtAsc(tenantId, topicId).stream()
                .map(r -> new ReplyView(r.getId(), r.getAuthorUserId(), r.getAuthorName(), r.getBody(), r.getCreatedAt()))
                .toList();
        return new TopicDetail(toView(t, actor), rv);
    }

    @Transactional
    public TopicView createTopic(UserPrincipal actor, CreateTopicRequest req) {
        Long tenantId = TenantContext.require();
        if (req.courseId() != null) {
            courses.findByTenantIdAndId(tenantId, req.courseId())
                    .orElseThrow(() -> NotFoundException.of("الكورس", req.courseId()));
        }
        ForumTopic t = new ForumTopic();
        t.setTenantId(tenantId);
        t.setCourseId(req.courseId());
        t.setAuthorUserId(actor.getId());
        t.setAuthorName(actor.getFullName());
        t.setTitle(req.title().trim());
        t.setBody(req.body().trim());
        topics.save(t);
        return toView(t, actor);
    }

    @Transactional
    public ReplyView addReply(UserPrincipal actor, Long topicId, CreateReplyRequest req) {
        Long tenantId = TenantContext.require();
        ForumTopic t = topics.findByTenantIdAndId(tenantId, topicId).orElseThrow(() -> NotFoundException.of("الموضوع", topicId));
        ForumReply r = new ForumReply();
        r.setTenantId(tenantId);
        r.setTopicId(topicId);
        r.setAuthorUserId(actor.getId());
        r.setAuthorName(actor.getFullName());
        r.setBody(req.body().trim());
        replies.save(r);
        t.setReplyCount(t.getReplyCount() + 1);
        t.setLastActivityAt(Instant.now());
        topics.save(t);
        return new ReplyView(r.getId(), r.getAuthorUserId(), r.getAuthorName(), r.getBody(), r.getCreatedAt());
    }

    /** Toggles the caller's like on a topic — a second call un-likes it, rather than double-counting. */
    @Transactional
    public TopicView toggleLike(UserPrincipal actor, Long topicId) {
        Long tenantId = TenantContext.require();
        ForumTopic t = topics.findByTenantIdAndId(tenantId, topicId).orElseThrow(() -> NotFoundException.of("الموضوع", topicId));
        var existing = likes.findByTenantIdAndTopicIdAndUserId(tenantId, topicId, actor.getId());
        if (existing.isPresent()) {
            likes.delete(existing.get());
            t.setLikeCount(Math.max(0, t.getLikeCount() - 1));
        } else {
            ForumLike l = new ForumLike();
            l.setTenantId(tenantId);
            l.setTopicId(topicId);
            l.setUserId(actor.getId());
            likes.save(l);
            t.setLikeCount(t.getLikeCount() + 1);
        }
        topics.save(t);
        return toView(t, actor);
    }

    private TopicView toView(ForumTopic t, UserPrincipal actor) {
        Long tenantId = t.getTenantId();
        String courseTitle = t.getCourseId() == null ? null
                : courses.findByTenantIdAndId(tenantId, t.getCourseId()).map(c -> c.getTitle()).orElse(null);
        boolean likedByMe = actor != null && likes.findByTenantIdAndTopicIdAndUserId(tenantId, t.getId(), actor.getId()).isPresent();
        return new TopicView(t.getId(), t.getCourseId(), courseTitle, t.getAuthorUserId(), t.getAuthorName(),
                t.getTitle(), t.getBody(), t.isPinned(), t.getReplyCount(), t.getLikeCount(), likedByMe,
                t.getCreatedAt(), t.getLastActivityAt());
    }
}
