package com.hrms.task;

import com.hrms.employee.Employee;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_tasks")
@Data
public class DailyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"manager", "user"})
    private Employee employee;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "task_description", columnDefinition = "TEXT", nullable = false)
    private String taskDescription;

    // Evidence files — only returned to managers (stripped in safe DTO)
    @Column(name = "evidence1_url")
    private String evidence1Url;

    @Column(name = "evidence2_url")
    private String evidence2Url;

    @Column(name = "is_late")
    private boolean late = false;

    @Column(name = "late_reason", columnDefinition = "TEXT")
    private String lateReason;

    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    /** Days added to internship contract when this task is rejected (0, 0.5, or 1.0) */
    @Column(name = "extension_days")
    private Double extensionDays = 0.0;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    @JsonIgnoreProperties({"manager", "user"})
    private Employee reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }
}
