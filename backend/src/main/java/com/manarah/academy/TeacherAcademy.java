package com.manarah.academy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name = "teacher_academies") @Getter @Setter
public class TeacherAcademy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long managerTenantId;
    private Long teacherId;
    /** A user outside this academy's tenant (their school staff account) who may also manage it. */
    private Long ownerUserId;
    private String slug;
    private String name;
    private String tagline = "الحكمدار · معلم الرياضيات";
    private String headline = "الرياضيات مش صعبة.. محتاجة تتفهم صح.";
    private String description = "من أول فكرة لحد أصعب مسألة، هنفهم ونطبّق ونراجع سوا. رحلتك في الرياضيات تبدأ هنا، خطوة بخطوة مع مستر محمد سليمان.";
    private String aboutText = "أهلاً بيك! هنا بنحوّل المسائل الكبيرة لخطوات بسيطة. شرح منظّم، تدريب بعد كل درس، ومراجعة تثبّت المعلومة. هدفنا إنك تفهم الفكرة وتعرف تستخدمها بنفسك.";
    private String subject = "الرياضيات";
    private String phone = "";
    /** Manual payment instructions shown at checkout — no payment gateway required. */
    private String instapayNumber = "";
    private String vodafoneCashNumber = "";
    @Column(columnDefinition = "TEXT") private String paymentNote = "";
    private String photoUrl = "/images/teacher-placeholder.svg";
    @JsonIgnore @Column(columnDefinition = "TEXT") private String photoData;
    @JsonIgnore @Column(columnDefinition = "TEXT") private String coverData;
    private boolean demoContent = true;
    private boolean published = true;
    private boolean defaultHome;
    @JsonIgnore @Column(columnDefinition = "TEXT") private String videosJson = "[]";

    public java.util.List<AcademyService.Video> getVideos() {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(videosJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<AcademyService.Video>>() {}); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid stored video playlist", e); }
    }
}
