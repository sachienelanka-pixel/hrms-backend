package com.hrms.employee;

import com.hrms.department.Department;
import com.hrms.department.Designation;
import com.hrms.user.User;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Data
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String address;
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne
    @JoinColumn(name = "manager_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Employee manager;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "employment_type")
    private String employmentType; // FULL_TIME, PART_TIME, CONTRACT

    private String status = "ACTIVE"; // ACTIVE, INACTIVE, RESIGNED

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    // --- Personal Details ---
    @Column(name = "full_name")
    private String fullName;

    @Column(name = "preferred_name")
    private String preferredName;

    @Column(name = "marital_status")
    private String maritalStatus;

    private String nationality;

    /** NIC type: NIC / N_PASSPORT / P_PASSPORT / DRIVING_LICENSE */
    @Column(name = "nic_type")
    private String nicType;

    @Column(name = "nic_passport_number")
    private String nicPassportNumber;

    @Column(name = "driving_license_number")
    private String drivingLicenseNumber;

    /** Stored as "Province|District|City" composite (replaces free-text hometown) */
    @Column(name = "hometown")
    private String hometown;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(name = "personal_mobile")
    private String personalMobile;

    @Column(name = "permanent_address")
    private String permanentAddress;

    @Column(name = "current_address")
    private String currentAddress;

    @Column(name = "home_telephone")
    private String homeTelephone;

    // --- Family Details ---
    @Column(name = "mother_name")
    private String motherName;

    @Column(name = "father_name")
    private String fatherName;

    @Column(name = "sibling_count")
    private Integer siblingCount;

    // --- Bank Details ---
    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "branch_code")
    private String branchCode;

    @Column(name = "account_number")
    private String accountNumber;

    // --- Emergency Contact Info (Extending existing contact fields) ---
    @Column(name = "emergency_relationship")
    private String emergencyRelationship;

    @Column(name = "emergency_address")
    private String emergencyAddress;

    // --- Educational Details: School ---
    @Column(name = "school_name")
    private String schoolName;

    @Column(name = "school_sports")
    private String schoolSports;

    @Column(name = "school_clubs")
    private String schoolClubs;

    @Column(name = "senior_prefect")
    private Boolean seniorPrefect;

    @Column(name = "school_colours")
    private Boolean schoolColours;

    @Column(name = "ol_results", columnDefinition = "TEXT")
    private String olResults;

    @Column(name = "al_results", columnDefinition = "TEXT")
    private String alResults;

    @Column(name = "al_z_score")
    private String alZScore;

    // --- Educational Details: Undergraduate ---
    @Column(name = "uni_name")
    private String uniName;

    @Column(name = "degree_title")
    private String degreeTitle;

    @Column(name = "study_year")
    private Integer studyYear;

    @Column(name = "grad_year")
    private Integer gradYear;

    private Double cgpa;
    private Double wgpa;

    /** Previously preferredSubject; now stores JSON array of up to 3 preferred areas */
    @Column(name = "preferred_subject", columnDefinition = "TEXT")
    private String preferredAreas;

    // --- Employment Information ---
    @Column(name = "employee_custom_id")
    private String employeeCustomId;

    @Column(name = "employment_status")
    private String employmentStatus = "Active"; // Active / Probation / Confirmed / Resigned / Terminated

    @Column(name = "epf_number")
    private String epfNumber;

    @Column(name = "etf_number")
    private String etfNumber;

    @Column(name = "work_location")
    private String workLocation;

    @Column(name = "work_mode")
    private String workMode;

    @Column(name = "probation_period")
    private String probationPeriod;

    @Column(name = "tin_number")
    private String tinNumber;

    /** Total internship duration extension (days) accumulated from rejected task reviews */
    @Column(name = "total_extension_days")
    private Double totalExtensionDays = 0.0;

    // --- Document Uploads ---
    @Column(name = "nic_copy_url")
    private String nicCopyUrl;

    @Column(name = "birth_certificate_url")
    private String birthCertificateUrl;

    /** JSON array of up to 5 educational qualification PDF URLs */
    @Column(name = "education_certificates_url", columnDefinition = "TEXT")
    private String educationCertificatesUrl;

    /** JSON array of up to 5 professional qualification PDF URLs */
    @Column(name = "professional_qualifications_url", columnDefinition = "TEXT")
    private String professionalQualificationsUrl;

    /** Degree/diploma certificate URL — mandatory (except for Interns) */
    @Column(name = "degree_document_url", columnDefinition = "TEXT")
    private String degreeDocumentUrl;

    /** Retained for backward-compatibility; no longer exposed in UI */
    @Column(name = "daily_tasks_url")
    private String dailyTasksUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getEmployeeCustomId() {
        if (this.id != null) {
            return "EMP-" + this.id;
        }
        return this.employeeCustomId;
    }
}
