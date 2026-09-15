package com.manarah.org;

import com.manarah.common.tenant.TenantContext;
import com.manarah.org.domain.Branch;
import com.manarah.org.domain.Room;
import com.manarah.org.repo.BranchRepository;
import com.manarah.org.repo.RoomRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/org")
@Tag(name = "Organisation")
public class OrgController {

    private final BranchRepository branches;
    private final RoomRepository rooms;

    public OrgController(BranchRepository branches, RoomRepository rooms) {
        this.branches = branches;
        this.rooms = rooms;
    }

    public record BranchRequest(String name, String address, String phone) {
    }

    public record RoomRequest(Long branchId, String name, Integer capacity, Boolean hasProjector, Boolean hasAc) {
    }

    @GetMapping("/branches")
    public List<Branch> branches() {
        return branches.findByTenantIdOrderByName(TenantContext.require());
    }

    @PostMapping("/branches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN')")
    public Branch createBranch(@RequestBody BranchRequest req) {
        Branch b = new Branch();
        b.setTenantId(TenantContext.require());
        b.setName(req.name());
        b.setAddress(req.address());
        b.setPhone(req.phone());
        b.setCreatedAt(Instant.now());
        return branches.save(b);
    }

    @GetMapping("/rooms")
    public List<Room> rooms() {
        return rooms.findByTenantIdOrderByName(TenantContext.require());
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Room createRoom(@RequestBody RoomRequest req) {
        Room r = new Room();
        r.setTenantId(TenantContext.require());
        r.setBranchId(req.branchId());
        r.setName(req.name());
        r.setCapacity(req.capacity() != null ? req.capacity() : 30);
        r.setHasProjector(Boolean.TRUE.equals(req.hasProjector()));
        r.setHasAc(Boolean.TRUE.equals(req.hasAc()));
        r.setCreatedAt(Instant.now());
        return rooms.save(r);
    }
}
