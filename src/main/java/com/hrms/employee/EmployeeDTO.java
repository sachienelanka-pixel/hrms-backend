package com.hrms.employee;

import lombok.Data;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    private Long id;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String gender;
    private LocalDate dateOfBirth;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Long departmentId;
    private String departmentName;
    private Long designationId;
    private String designationTitle;
    private Long managerId;
    private String managerName;
    private LocalDate joiningDate;
    private String employmentType;
    private String status;
    private String profilePhotoUrl;
    private String role;

    // --- Personal Details ---
    private String fullName;
    private String preferredName;
    private String maritalStatus;
    private String nationality;
    private String nicType;
    private String nicPassportNumber;
    private String drivingLicenseNumber;
    /** Stored as "Province|District|City" composite */
    private String hometown;
    private String personalEmail;
    private String personalMobile;
    private String permanentAddress;
    private String currentAddress;
    private String homeTelephone;

    // --- Family Details ---
    private String motherName;
    private String fatherName;
    private Integer siblingCount;

    // --- Bank Details ---
    private String bankName;
    private String branchName;
    private String branchCode;
    private String accountNumber;

    // --- Emergency Contacts ---
    private String emergencyRelationship;
    private String emergencyAddress;

    // --- School Education ---
    private String schoolName;
    private String schoolSports;
    private String schoolClubs;
    private Boolean seniorPrefect;
    private Boolean schoolColours;
    private String olResults;
    private String alResults;
    private String alZScore;

    // --- Undergraduate ---
    private String uniName;
    private String degreeTitle;
    private Integer studyYear;
    private Integer gradYear;
    private Double cgpa;
    private Double wgpa;
    /** JSON array of up to 3 preferred area strings */
    private String preferredAreas;

    // --- Employment ---
    private String employeeCustomId;
    private String employmentStatus;
    private String epfNumber;
    private String etfNumber;
    private String workLocation;
    private String workMode;
    private String probationPeriod;
    private String tinNumber;
    private Double totalExtensionDays;

    // --- Documents ---
    private String nicCopyUrl;
    private String birthCertificateUrl;
    /** JSON array of up to 5 educational qualification PDF URLs */
    private String educationCertificatesUrl;
    /** JSON array of up to 5 professional qualification PDF URLs */
    private String professionalQualificationsUrl;
    /** Degree/diploma certificate URL */
    private String degreeDocumentUrl;

    public static EmployeeDTO from(Employee e) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(e.getId());
        dto.setFirstName(e.getFirstName());
        dto.setMiddleName(e.getMiddleName());
        dto.setLastName(e.getLastName());
        dto.setEmail(e.getUser() != null ? e.getUser().getEmail() : null);
        dto.setPhoneNumber(e.getPhoneNumber());
        dto.setAddress(e.getAddress());
        dto.setGender(e.getGender());
        dto.setDateOfBirth(e.getDateOfBirth());
        dto.setEmergencyContactName(e.getEmergencyContactName());
        dto.setEmergencyContactPhone(e.getEmergencyContactPhone());
        dto.setJoiningDate(e.getJoiningDate());
        dto.setEmploymentType(e.getEmploymentType());
        dto.setStatus(e.getStatus());
        dto.setProfilePhotoUrl(e.getProfilePhotoUrl());

        // --- Personal Details ---
        dto.setFullName(e.getFullName());
        dto.setPreferredName(e.getPreferredName());
        dto.setMaritalStatus(e.getMaritalStatus());
        dto.setNationality(e.getNationality());
        dto.setNicType(e.getNicType());
        dto.setNicPassportNumber(e.getNicPassportNumber());
        dto.setDrivingLicenseNumber(e.getDrivingLicenseNumber());
        dto.setHometown(e.getHometown());
        dto.setPersonalEmail(e.getPersonalEmail());
        dto.setPersonalMobile(e.getPersonalMobile());
        dto.setPermanentAddress(e.getPermanentAddress());
        dto.setCurrentAddress(e.getCurrentAddress());
        dto.setHomeTelephone(e.getHomeTelephone());

        // --- Family Details ---
        dto.setMotherName(e.getMotherName());
        dto.setFatherName(e.getFatherName());
        dto.setSiblingCount(e.getSiblingCount());

        // --- Bank Details ---
        dto.setBankName(e.getBankName());
        dto.setBranchName(e.getBranchName());
        dto.setBranchCode(e.getBranchCode());
        dto.setAccountNumber(e.getAccountNumber());

        // --- Emergency Contacts ---
        dto.setEmergencyRelationship(e.getEmergencyRelationship());
        dto.setEmergencyAddress(e.getEmergencyAddress());

        // --- School Education ---
        dto.setSchoolName(e.getSchoolName());
        dto.setSchoolSports(e.getSchoolSports());
        dto.setSchoolClubs(e.getSchoolClubs());
        dto.setSeniorPrefect(e.getSeniorPrefect());
        dto.setSchoolColours(e.getSchoolColours());
        dto.setOlResults(e.getOlResults());
        dto.setAlResults(e.getAlResults());
        dto.setAlZScore(e.getAlZScore());

        // --- Undergraduate ---
        dto.setUniName(e.getUniName());
        dto.setDegreeTitle(e.getDegreeTitle());
        dto.setStudyYear(e.getStudyYear());
        dto.setGradYear(e.getGradYear());
        dto.setCgpa(e.getCgpa());
        dto.setWgpa(e.getWgpa());
        dto.setPreferredAreas(e.getPreferredAreas());

        // --- Employment ---
        dto.setEmployeeCustomId(e.getEmployeeCustomId());
        dto.setEmploymentStatus(e.getEmploymentStatus());
        dto.setEpfNumber(e.getEpfNumber());
        dto.setEtfNumber(e.getEtfNumber());
        dto.setWorkLocation(e.getWorkLocation());
        dto.setWorkMode(e.getWorkMode());
        dto.setProbationPeriod(e.getProbationPeriod());
        dto.setTinNumber(e.getTinNumber());
        dto.setTotalExtensionDays(e.getTotalExtensionDays());

        // --- Documents ---
        dto.setNicCopyUrl(e.getNicCopyUrl());
        dto.setBirthCertificateUrl(e.getBirthCertificateUrl());
        dto.setEducationCertificatesUrl(e.getEducationCertificatesUrl());
        dto.setProfessionalQualificationsUrl(e.getProfessionalQualificationsUrl());
        dto.setDegreeDocumentUrl(e.getDegreeDocumentUrl());

        if (e.getUser() != null && e.getUser().getRole() != null) {
            dto.setRole(e.getUser().getRole().getName());
        }
        if (e.getDepartment() != null) {
            dto.setDepartmentId(e.getDepartment().getId());
            dto.setDepartmentName(e.getDepartment().getName());
        }
        if (e.getDesignation() != null) {
            dto.setDesignationId(e.getDesignation().getId());
            dto.setDesignationTitle(e.getDesignation().getTitle());
        }
        if (e.getManager() != null) {
            dto.setManagerId(e.getManager().getId());
            dto.setManagerName(e.getManager().getFirstName() + " " + e.getManager().getLastName());
        }
        return dto;
    }
}
