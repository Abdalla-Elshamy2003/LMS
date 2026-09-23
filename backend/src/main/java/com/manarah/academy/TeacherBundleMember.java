package com.manarah.academy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * One teacher inside a package. The academy link is optional: a member can be announced (subject, photo, intro
 * video) before their teacher space exists, and gains a page, videos and courses once it is linked.
 */
@Entity @Table(name = "teacher_bundle_members") @Getter @Setter
public class TeacherBundleMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long bundleId;
    private int position;
    private Long academyId;
    private String displayName = "";
    private String subject;
    private String photoUrl = "";
    private String introVideoUrl = "";
}
