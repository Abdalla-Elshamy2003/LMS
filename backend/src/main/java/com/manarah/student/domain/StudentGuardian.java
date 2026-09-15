package com.manarah.student.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Link between a student and a guardian (§3). */
@Entity
@Table(name = "student_guardians")
@Getter
@Setter
public class StudentGuardian extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    private String relation;
}
