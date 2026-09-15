package com.manarah.timeline;

import com.manarah.common.tenant.TenantContext;
import com.manarah.common.web.PageResponse;
import com.manarah.timeline.domain.TimelineEntry;
import com.manarah.timeline.repo.TimelineEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TimelineService {

    private final TimelineEntryRepository repo;

    public TimelineService(TimelineEntryRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(Long tenantId, Long studentId, String type, String title, String detail, String icon, Instant when) {
        TimelineEntry e = new TimelineEntry();
        e.setTenantId(tenantId);
        e.setStudentId(studentId);
        e.setType(type);
        e.setTitle(title);
        e.setDetail(detail);
        e.setIcon(icon);
        e.setOccurredAt(when == null ? Instant.now() : when);
        repo.save(e);
    }

    public PageResponse<TimelineEntry> forStudent(Long studentId, int page, int size) {
        return PageResponse.of(
                repo.findByTenantIdAndStudentIdOrderByOccurredAtDesc(TenantContext.require(), studentId, PageRequest.of(page, size)),
                e -> e);
    }
}
