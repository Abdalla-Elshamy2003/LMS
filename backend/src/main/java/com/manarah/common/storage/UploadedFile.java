package com.manarah.common.storage;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "uploaded_files") @Getter @Setter
public class UploadedFile extends BaseEntity {
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "file_key", nullable = false, unique = true) private String fileKey;
}
