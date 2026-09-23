package com.manarah.academy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A teacher package (باقة مدرسين): several teachers presented together on the home page and on their own landing page. */
@Entity @Table(name = "teacher_bundles") @Getter @Setter
public class TeacherBundle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** The head office that manages it; null only for the package shipped by the migration, until an admin saves it. */
    private Long managerTenantId;
    private String slug;
    private String name;
    private String tagline = "";
    @Column(columnDefinition = "TEXT") private String description = "";
    private boolean published = true;
    private int sortOrder;
}
