package com.manarah.gamification.domain;

import com.manarah.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A badge definition (§30). */
@Entity
@Table(name = "badges")
@Getter
@Setter
public class Badge extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String icon;
    private String description;
}
