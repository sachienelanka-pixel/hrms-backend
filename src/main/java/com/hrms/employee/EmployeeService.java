package com.hrms.employee;

import com.hrms.department.DepartmentRepository;
import com.hrms.department.DesignationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EmployeeService {

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private com.hrms.user.UserRepository userRepository;

    // ─── Validation Patterns ─────────────────────────────────────────────────
    // NIC: 10-digit ending in V/X, or 12-digit numeric
    private static final Pattern NIC_PATTERN =
            Pattern.compile("^(\\d{9}[VXvx]|\\d{12})$");
    // N-Series Passport: capital N + 8 digits (9 chars total)
    private static final Pattern PASSPORT_N_PATTERN =
            Pattern.compile("^N\\d{8}$");
    // P-Series Passport: capital P + 7 digits (8 chars total)
    private static final Pattern PASSPORT_P_PATTERN =
            Pattern.compile("^P\\d{7}$");
    // Phone: starts with 94, no leading zero, 11 digits total (94 + 9 digits)
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^94\\d{9}$");
    // Z-Score: decimal number (e.g. 1.876 or -0.5)
    private static final Pattern ZSCORE_PATTERN =
            Pattern.compile("^-?\\d+(\\.\\d+)?$");
    // Email: standard regex format (user@domain.com)
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /**
     * Validate NIC/Passport/Driving License number against type.
     * Returns null if valid, error message string if invalid.
     */
    public String validateIdentityDocument(String nicType, String value) {
        String val = value != null ? value : nicType;
        if (val == null || val.isBlank()) return null;
        String cleanVal = val.trim().toUpperCase();

        if (cleanVal.startsWith("N")) {
            if (!PASSPORT_N_PATTERN.matcher(cleanVal).matches())
                return "N-Series Passport must be exactly 9 characters: capital N followed by 8 digits.";
        } else if (cleanVal.startsWith("P")) {
            if (!PASSPORT_P_PATTERN.matcher(cleanVal).matches())
                return "P-Series Passport must be exactly 8 characters: capital P followed by 7 digits.";
        } else {
            if (!NIC_PATTERN.matcher(cleanVal).matches())
                return "NIC must be 10 characters (9 digits + V/X) or 12 digits numeric.";
        }
        return null;
    }

    /**
     * Validate phone number: must be a valid international phone number.
     * Enforces the presence of a country code. If the number does not start with '+',
     * we prepend '+' before parsing so that libphonenumber can identify the country code.
     */
    public String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String cleaned = phone.trim();
        String parseable = cleaned;
        if (!parseable.startsWith("+")) {
            parseable = "+" + parseable;
        }
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber numberProto = phoneUtil.parse(parseable, null);
            if (!phoneUtil.isValidNumber(numberProto)) {
                return "Phone number is not a valid international number. Please ensure it includes a valid country code (e.g. +94771234567).";
            }
        } catch (Exception e) {
            return "Invalid phone number format. Please ensure it includes a valid country code (e.g. +94771234567).";
        }
        return null;
    }

    /**
     * Validate date of birth: employee must be >= 18 years old.
     */
    public String validateDOB(LocalDate dob) {
        if (dob == null) return null;
        if (dob.isAfter(LocalDate.now()))
            return "Date of birth cannot be in the future.";
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < 18)
            return "Employee must be at least 18 years old.";
        return null;
    }

    /**
     * Validate Z-Score format.
     */
    public String validateZScore(String zScore) {
        if (zScore == null || zScore.isBlank()) return null;
        if (!ZSCORE_PATTERN.matcher(zScore.trim()).matches())
            return "Z-Score must be a numerical decimal (e.g. 1.876).";
        try {
            double val = Double.parseDouble(zScore.trim());
            if (val >= 3.0) {
                return "Z-Score must be lesser than 3.0.";
            }
        } catch (Exception e) {
            return "Z-Score must be a numerical decimal (e.g. 1.876).";
        }
        return null;
    }

    /**
     * Validate email format.
     */
    public String validateEmail(String email) {
        if (email == null || email.isBlank()) return null;
        if (!EMAIL_PATTERN.matcher(email.trim()).matches())
            return "Invalid email address format (e.g. user@example.com).";
        return null;
    }

    /**
     * Validate all fields in the request body map.
     * Returns null if valid, or the validation error message if invalid.
     */
    public String validateEmployeeMap(Map<String, Object> body) {
        if (body.get("personalMobile") != null) {
            String val = body.get("personalMobile").toString();
            if (!val.isBlank()) {
                String err = validatePhone(val);
                if (err != null) return "Personal Mobile: " + err;
            }
        }
        if (body.get("homeTelephone") != null) {
            String val = body.get("homeTelephone").toString();
            if (!val.isBlank()) {
                String err = validatePhone(val);
                if (err != null) return "Home Telephone: " + err;
            }
        }
        if (body.get("emergencyContactPhone") != null) {
            String val = body.get("emergencyContactPhone").toString();
            if (!val.isBlank()) {
                String err = validatePhone(val);
                if (err != null) return "Emergency Contact Phone: " + err;
            }
        }
        if (body.get("nicPassportNumber") != null) {
            String val = body.get("nicPassportNumber").toString();
            if (!val.isBlank()) {
                String err = validateIdentityDocument(null, val);
                if (err != null) return err;
            }
        }
        if (body.get("dateOfBirth") != null) {
            String val = body.get("dateOfBirth").toString();
            if (!val.isBlank()) {
                try {
                    LocalDate dob = LocalDate.parse(val);
                    String err = validateDOB(dob);
                    if (err != null) return err;
                } catch (Exception e) {
                    return "Invalid date of birth format.";
                }
            }
        }
        if (body.get("alZScore") != null) {
            String val = body.get("alZScore").toString();
            if (!val.isBlank()) {
                String err = validateZScore(val);
                if (err != null) return err;
            }
        }
        if (body.get("personalEmail") != null) {
            String val = body.get("personalEmail").toString();
            if (!val.isBlank()) {
                String err = validateEmail(val);
                if (err != null) return "Personal Email: " + err;
            }
        }
        if (body.get("email") != null) {
            String val = body.get("email").toString();
            if (!val.isBlank()) {
                String err = validateEmail(val);
                if (err != null) return "Email: " + err;
            }
        }
        if (body.get("gradYear") != null) {
            String val = body.get("gradYear").toString().trim();
            if (!val.isBlank()) {
                try {
                    int year = Integer.parseInt(val);
                    if (year < 1970 || year > 2100) {
                        return "Graduation Year must be a valid calendar year between 1970 and 2100.";
                    }
                } catch (Exception e) {
                    return "Graduation Year must be a valid integer year.";
                }
            }
        }
        if (body.get("workLocation") != null) {
            String val = body.get("workLocation").toString().trim().toLowerCase();
            if (!val.isBlank() && !val.equals("local branch") && !val.equals("international branch")) {
                return "Work Location must be one of the following: local branch, international branch";
            }
        }
        if (body.get("workMode") != null) {
            String val = body.get("workMode").toString().trim().toLowerCase();
            if (!val.isBlank() && !val.equals("online") && !val.equals("physical modes")) {
                return "Work Mode must be one of the following: online, physical modes";
            }
        }
        return null;
    }

    public Employee updateEmployeeFromMap(Employee emp, Map<String, Object> body) {
        if (body.get("firstName") != null) emp.setFirstName(body.get("firstName").toString());
        if (body.get("middleName") != null) emp.setMiddleName(body.get("middleName").toString());
        if (body.get("lastName") != null) emp.setLastName(body.get("lastName").toString());
        if (body.get("email") != null && emp.getUser() != null) {
            emp.getUser().setEmail(body.get("email").toString());
            userRepository.save(emp.getUser());
        } else if (body.get("personalEmail") != null && emp.getUser() != null) {
            emp.getUser().setEmail(body.get("personalEmail").toString());
            userRepository.save(emp.getUser());
        }
        if (body.get("phoneNumber") != null) emp.setPhoneNumber(body.get("phoneNumber").toString());
        if (body.get("address") != null) emp.setAddress(body.get("address").toString());
        if (body.get("gender") != null) emp.setGender(body.get("gender").toString());
        if (body.get("employmentType") != null) emp.setEmploymentType(body.get("employmentType").toString());
        if (body.get("status") != null) emp.setStatus(body.get("status").toString());
        
        if (body.get("joiningDate") != null && !body.get("joiningDate").toString().isBlank()) {
            emp.setJoiningDate(LocalDate.parse(body.get("joiningDate").toString()));
        }
        if (body.get("dateOfBirth") != null && !body.get("dateOfBirth").toString().isBlank()) {
            LocalDate dob = LocalDate.parse(body.get("dateOfBirth").toString());
            emp.setDateOfBirth(dob);
        }

        if (body.get("profilePhotoUrl") != null) emp.setProfilePhotoUrl(body.get("profilePhotoUrl").toString());

        // --- Personal Details ---
        if (body.get("fullName") != null) emp.setFullName(body.get("fullName").toString());
        if (body.get("preferredName") != null) emp.setPreferredName(body.get("preferredName").toString());
        if (body.get("maritalStatus") != null) emp.setMaritalStatus(body.get("maritalStatus").toString());
        if (body.containsKey("nationality")) {
            Object natObj = body.get("nationality");
            String natStr = natObj != null ? natObj.toString().trim() : "";
            if (natStr.isEmpty()) {
                emp.setNationality("Sri Lanka");
            } else {
                emp.setNationality(natStr);
            }
        } else if (emp.getNationality() == null || emp.getNationality().isBlank()) {
            emp.setNationality("Sri Lanka");
        }
        if (body.get("nicPassportNumber") != null) {
            String val = body.get("nicPassportNumber").toString().trim().toUpperCase();
            emp.setNicPassportNumber(val);
            if (val.startsWith("N")) {
                emp.setNicType("N_PASSPORT");
            } else if (val.startsWith("P")) {
                emp.setNicType("P_PASSPORT");
            } else {
                emp.setNicType("NIC");
            }
        } else if (body.get("nicType") != null) {
            emp.setNicType(body.get("nicType").toString());
        }
        if (body.get("drivingLicenseNumber") != null) emp.setDrivingLicenseNumber(body.get("drivingLicenseNumber").toString());
        // hometown stores "Province|District|City" composite
        if (body.get("hometown") != null) emp.setHometown(body.get("hometown").toString());
        if (body.get("personalEmail") != null) {
            String pEmail = body.get("personalEmail").toString();
            emp.setPersonalEmail(pEmail);
            if (emp.getUser() != null) {
                emp.getUser().setEmail(pEmail);
                userRepository.save(emp.getUser());
            }
        }
        if (body.get("personalMobile") != null) emp.setPersonalMobile(body.get("personalMobile").toString());
        if (body.get("permanentAddress") != null) emp.setPermanentAddress(body.get("permanentAddress").toString());
        if (body.get("currentAddress") != null) emp.setCurrentAddress(body.get("currentAddress").toString());
        if (body.get("homeTelephone") != null) emp.setHomeTelephone(body.get("homeTelephone").toString());

        // --- Family Details ---
        if (body.get("motherName") != null) emp.setMotherName(body.get("motherName").toString());
        if (body.get("fatherName") != null) emp.setFatherName(body.get("fatherName").toString());
        if (body.get("siblingCount") != null && !body.get("siblingCount").toString().isBlank()) {
            emp.setSiblingCount(Integer.parseInt(body.get("siblingCount").toString()));
        }

        // --- Bank Details ---
        if (body.get("bankName") != null) emp.setBankName(body.get("bankName").toString());
        if (body.get("branchName") != null) emp.setBranchName(body.get("branchName").toString());
        if (body.get("branchCode") != null) emp.setBranchCode(body.get("branchCode").toString());
        if (body.get("accountNumber") != null) emp.setAccountNumber(body.get("accountNumber").toString());

        // --- Emergency Contacts ---
        if (body.get("emergencyContactName") != null) emp.setEmergencyContactName(body.get("emergencyContactName").toString());
        if (body.get("emergencyContactPhone") != null) emp.setEmergencyContactPhone(body.get("emergencyContactPhone").toString());
        if (body.get("emergencyRelationship") != null) emp.setEmergencyRelationship(body.get("emergencyRelationship").toString());
        if (body.get("emergencyAddress") != null) emp.setEmergencyAddress(body.get("emergencyAddress").toString());

        // --- School Education ---
        if (body.get("schoolName") != null) emp.setSchoolName(body.get("schoolName").toString());
        if (body.get("schoolSports") != null) emp.setSchoolSports(body.get("schoolSports").toString());
        if (body.get("schoolClubs") != null) emp.setSchoolClubs(body.get("schoolClubs").toString());
        if (body.get("seniorPrefect") != null && !body.get("seniorPrefect").toString().isBlank()) {
            emp.setSeniorPrefect(Boolean.parseBoolean(body.get("seniorPrefect").toString()));
        }
        if (body.get("schoolColours") != null && !body.get("schoolColours").toString().isBlank()) {
            emp.setSchoolColours(Boolean.parseBoolean(body.get("schoolColours").toString()));
        }
        if (body.get("olResults") != null) emp.setOlResults(body.get("olResults").toString());
        if (body.get("alResults") != null) emp.setAlResults(body.get("alResults").toString());
        if (body.get("alZScore") != null) emp.setAlZScore(body.get("alZScore").toString());

        // --- Undergraduate ---
        if (body.get("uniName") != null) emp.setUniName(body.get("uniName").toString());
        if (body.get("degreeTitle") != null) emp.setDegreeTitle(body.get("degreeTitle").toString());
        if (body.get("studyYear") != null && !body.get("studyYear").toString().isBlank()) {
            emp.setStudyYear(Integer.parseInt(body.get("studyYear").toString()));
        }
        if (body.get("gradYear") != null && !body.get("gradYear").toString().isBlank()) {
            emp.setGradYear(Integer.parseInt(body.get("gradYear").toString()));
        }
        if (body.get("cgpa") != null && !body.get("cgpa").toString().isBlank()) {
            emp.setCgpa(Double.parseDouble(body.get("cgpa").toString()));
        }
        if (body.get("wgpa") != null && !body.get("wgpa").toString().isBlank()) {
            emp.setWgpa(Double.parseDouble(body.get("wgpa").toString()));
        }
        // preferredAreas stored as JSON array string (replaces preferredSubject)
        if (body.get("preferredAreas") != null) emp.setPreferredAreas(body.get("preferredAreas").toString());

        // --- Employment ---
        if (body.get("employeeCustomId") != null) emp.setEmployeeCustomId(body.get("employeeCustomId").toString());
        if (body.get("employmentStatus") != null) {
            String status = body.get("employmentStatus").toString().trim();
            if (status.isEmpty()) {
                emp.setEmploymentStatus("Active");
            } else {
                emp.setEmploymentStatus(status);
            }
        } else {
            if (emp.getEmploymentStatus() == null || emp.getEmploymentStatus().trim().isEmpty()) {
                emp.setEmploymentStatus("Active");
            }
        }
        if (body.get("epfNumber") != null) emp.setEpfNumber(body.get("epfNumber").toString());
        if (body.get("etfNumber") != null) emp.setEtfNumber(body.get("etfNumber").toString());
        if (body.get("workLocation") != null) {
            String val = body.get("workLocation").toString().trim().toLowerCase();
            if (val.isEmpty()) {
                emp.setWorkLocation(null);
            } else {
                emp.setWorkLocation(val);
            }
        }
        if (body.get("workMode") != null) {
            String val = body.get("workMode").toString().trim().toLowerCase();
            if (val.isEmpty()) {
                emp.setWorkMode(null);
            } else {
                emp.setWorkMode(val);
            }
        }
        if (body.get("probationPeriod") != null) emp.setProbationPeriod(body.get("probationPeriod").toString());
        if (body.get("tinNumber") != null) emp.setTinNumber(body.get("tinNumber").toString());

        // --- Documents ---
        if (body.get("nicCopyUrl") != null) emp.setNicCopyUrl(body.get("nicCopyUrl").toString());
        if (body.get("birthCertificateUrl") != null) emp.setBirthCertificateUrl(body.get("birthCertificateUrl").toString());
        if (body.get("educationCertificatesUrl") != null) emp.setEducationCertificatesUrl(body.get("educationCertificatesUrl").toString());
        if (body.get("professionalQualificationsUrl") != null) emp.setProfessionalQualificationsUrl(body.get("professionalQualificationsUrl").toString());
        if (body.get("degreeDocumentUrl") != null) emp.setDegreeDocumentUrl(body.get("degreeDocumentUrl").toString());

        // --- Relations ---
        Object deptObj = body.get("department");
        if (deptObj instanceof Map) {
            Object deptId = ((Map<?,?>) deptObj).get("id");
            if (deptId != null && !deptId.toString().isEmpty()) {
                departmentRepository.findById(Long.parseLong(deptId.toString())).ifPresent(emp::setDepartment);
            }
        } else if (body.get("departmentId") != null && !body.get("departmentId").toString().isEmpty()) {
            departmentRepository.findById(Long.parseLong(body.get("departmentId").toString())).ifPresent(emp::setDepartment);
        }

        Object desigObj = body.get("designation");
        if (desigObj instanceof Map) {
            Object desigId = ((Map<?,?>) desigObj).get("id");
            if (desigId != null && !desigId.toString().isEmpty()) {
                designationRepository.findById(Long.parseLong(desigId.toString())).ifPresent(emp::setDesignation);
            }
        } else if (body.get("designationId") != null && !body.get("designationId").toString().isEmpty()) {
            designationRepository.findById(Long.parseLong(body.get("designationId").toString())).ifPresent(emp::setDesignation);
        }

        if (body.containsKey("managerId")) {
            Object managerId = body.get("managerId");
            if (managerId != null && !managerId.toString().isEmpty()) {
                employeeRepository.findById(Long.parseLong(managerId.toString())).ifPresent(emp::setManager);
            } else {
                emp.setManager(null);
            }
        }

        return emp;
    }
}
