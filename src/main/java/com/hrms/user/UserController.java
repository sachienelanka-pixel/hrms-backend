package com.hrms.user;

import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import com.hrms.department.DepartmentRepository;
import com.hrms.department.DesignationRepository;
import com.hrms.leave.*;
import com.hrms.auth.UserDetailsImpl;
import com.hrms.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserRepository userRepo;
    @Autowired private RoleRepository roleRepo;
    @Autowired private EmployeeRepository empRepo;
    @Autowired private DepartmentRepository deptRepo;
    @Autowired private DesignationRepository desigRepo;
    @Autowired private LeaveTypeRepository leaveTypeRepo;
    @Autowired private LeaveBalanceRepository leaveBalanceRepo;
    @Autowired private PasswordEncoder encoder;
    @Autowired private com.hrms.employee.EmployeeService employeeService;
    @Autowired private EmailService emailService;

    @Value("${app.default-supervisor-id:1}")
    private Long defaultSupervisorId;

    // ─── Register new employee (Admin only) ────────────────────────────────────
    @PostMapping("/register")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public ResponseEntity<?> registerNewEmployee(@RequestBody Map<String, Object> body, Authentication authentication) {
        String firstName  = (String) body.getOrDefault("firstName", "");
        String middleName = (String) body.getOrDefault("middleName", "");
        String lastName   = (String) body.getOrDefault("lastName", "");
        String roleName   = (String) body.getOrDefault("role", "ROLE_EMPLOYEE");

        if ("ROLE_SUPER_ADMIN".equals(roleName)) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            boolean isSuperAdmin = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            if (!isSuperAdmin) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied: only Super Admin can assign the Super Admin role"));
            }
        }

        if (firstName.isBlank() || lastName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "First name and last name are required"));
        }

        String validationErr = employeeService.validateEmployeeMap(body);
        if (validationErr != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationErr));
        }

        // ── Auto-generate username: firstName.middleInitial.lastName or firstName.lastName
        String baseUsername = generateUsername(firstName, middleName, lastName);
        String username = resolveUniqueUsername(baseUsername);

        // ── Auto-generate a secure random 10-character alphanumeric password
        String rawPassword = generateSecurePassword(10);

        // Check if username was manually provided
        String manualUsername = (String) body.get("username");
        if (manualUsername != null && !manualUsername.isBlank()) {
            username = manualUsername.toLowerCase().trim();
            if (userRepo.existsByUsername(username)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username '" + username + "' is already taken"));
            }
        }
        String manualPassword = (String) body.get("password");
        if (manualPassword != null && !manualPassword.isBlank()) {
            rawPassword = manualPassword;
        }

        // Create User account
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(rawPassword));
        user.setRole(roleRepo.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName)));
        String userEmail = (String) body.get("personalEmail");
        if (userEmail == null || userEmail.isBlank()) {
            userEmail = (String) body.getOrDefault("email", "");
        }
        user.setEmail(userEmail);
        userRepo.save(user);

        // Create Employee profile
        Employee emp = new Employee();
        emp.setUser(user);
        emp.setFirstName(firstName);
        emp.setLastName(lastName);
        emp.setGender((String) body.getOrDefault("gender", "N/A"));
        emp.setEmploymentType((String) body.getOrDefault("employmentType", "FULL_TIME"));
        emp.setStatus("ACTIVE");
        emp.setEmploymentStatus("Active");
        emp.setJoiningDate(LocalDate.now());

        // Update all other details from body map
        employeeService.updateEmployeeFromMap(emp, body);
        empRepo.save(emp);

        // ── Set default supervisor (Ms. Sachi) if none was specified ──────────
        if (emp.getManager() == null) {
            empRepo.findById(defaultSupervisorId).ifPresent(defaultMgr -> {
                emp.setManager(defaultMgr);
                empRepo.save(emp);
            });
        }

        // ── Send credentials email to the employee's personal email ──────────
        String personalEmail = emp.getPersonalEmail();
        // Fallback to the userEmail captured above if personalEmail wasn't set on the employee record
        String emailToSend = (personalEmail != null && !personalEmail.isBlank()) ? personalEmail : userEmail;
        if (emailToSend != null && !emailToSend.isBlank()) {
            String fullName = firstName + " " + lastName;
            emailService.sendCredentialsEmail(emailToSend, fullName, username, rawPassword);
        }

        // Auto-create leave balances
        int year = Year.now().getValue();
        leaveTypeRepo.findAll().forEach(lt -> {
            boolean exists = leaveBalanceRepo
                    .findByEmployeeIdAndLeaveTypeIdAndYear(emp.getId(), lt.getId(), year).isPresent();
            if (!exists) {
                LeaveBalance lb = new LeaveBalance();
                lb.setEmployee(emp);
                lb.setLeaveType(lt);
                lb.setYear(year);
                lb.setAllocatedDays(lt.getDaysPerYear());
                leaveBalanceRepo.save(lb);
            }
        });

        // ── Return generated credentials clearly to admin
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("employeeId", emp.getId());
        result.put("generatedUsername", username);
        result.put("generatedPassword", rawPassword);    // plain text — shown ONCE to admin
        result.put("employeeName", firstName + " " + lastName);
        result.put("emailSent", emp.getPersonalEmail() != null && !emp.getPersonalEmail().isBlank());
        result.put("message", "User account and employee profile created successfully!");
        return ResponseEntity.ok(result);
    }

    // ─── Import Employees from Excel (Admin only) ───────────────────────────
    @PostMapping(value = "/import-excel", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public ResponseEntity<?> importEmployeesExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload a valid Excel file."));
        }

        int totalProcessed = 0;
        int successful = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<Map<String, String>> successList = new ArrayList<>();

        try (java.io.InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "The uploaded Excel sheet is empty."));
            }

            int firstNameIdx = -1;
            int middleNameIdx = -1;
            int lastNameIdx = -1;
            int emailIdx = -1;
            int dateOfJoinIdx = -1;

            for (Cell cell : headerRow) {
                if (cell.getCellType() != CellType.STRING) continue;
                String headerText = cell.getStringCellValue().trim().toLowerCase();
                if (headerText.contains("first name") || headerText.equals("firstname")) {
                    firstNameIdx = cell.getColumnIndex();
                } else if (headerText.contains("middle name") || headerText.equals("middlename")) {
                    middleNameIdx = cell.getColumnIndex();
                } else if (headerText.contains("last name") || headerText.equals("lastname")) {
                    lastNameIdx = cell.getColumnIndex();
                } else if (headerText.contains("email") || headerText.contains("personal email")) {
                    emailIdx = cell.getColumnIndex();
                } else if (headerText.contains("date of join") || headerText.contains("joining date") || headerText.contains("date of joining") || headerText.contains("join date")) {
                    dateOfJoinIdx = cell.getColumnIndex();
                }
            }

            if (firstNameIdx == -1 || lastNameIdx == -1 || emailIdx == -1 || dateOfJoinIdx == -1) {
                return ResponseEntity.badRequest().body(Map.of("error", 
                    "Excel is missing required headers. Found: " + 
                    "First Name=" + (firstNameIdx != -1) + 
                    ", Last Name=" + (lastNameIdx != -1) + 
                    ", Personal Email=" + (emailIdx != -1) + 
                    ", Date of Join=" + (dateOfJoinIdx != -1)));
            }

            Set<String> excelProcessedEmails = new HashSet<>();
            Role role = roleRepo.findByName("ROLE_EMPLOYEE").orElseThrow(() -> new RuntimeException("ROLE_EMPLOYEE not found"));

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Check if the row is completely empty
                boolean isEmptyRow = true;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        isEmptyRow = false;
                        break;
                    }
                }
                if (isEmptyRow) continue;

                totalProcessed++;

                String firstName = getCellValueAsString(row.getCell(firstNameIdx)).trim();
                String middleName = middleNameIdx != -1 ? getCellValueAsString(row.getCell(middleNameIdx)).trim() : "";
                String lastName = getCellValueAsString(row.getCell(lastNameIdx)).trim();
                String email = getCellValueAsString(row.getCell(emailIdx)).trim().toLowerCase();
                String dateOfJoinStr = getCellValueAsString(row.getCell(dateOfJoinIdx)).trim();

                // Validate required fields
                if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || dateOfJoinStr.isEmpty()) {
                    failed++;
                    errors.add("Row " + (r + 1) + ": Required fields (First Name, Last Name, Personal Email, Date of Join) are missing.");
                    continue;
                }

                // Check for duplicates within Excel
                if (excelProcessedEmails.contains(email)) {
                    failed++;
                    duplicates.add("Row " + (r + 1) + ": Duplicate email '" + email + "' found within the uploaded Excel sheet.");
                    continue;
                }
                excelProcessedEmails.add(email);

                // Check for duplicates in DB
                if (userRepo.existsByEmail(email) || empRepo.existsByPersonalEmail(email)) {
                    failed++;
                    duplicates.add("Row " + (r + 1) + ": Email '" + email + "' is already registered in the system.");
                    continue;
                }

                LocalDate dateOfJoin = parseLocalDate(dateOfJoinStr);
                if (dateOfJoin == null) {
                    failed++;
                    errors.add("Row " + (r + 1) + ": Invalid date of join format '" + dateOfJoinStr + "'. Please use YYYY-MM-DD or standard formats.");
                    continue;
                }

                // Generate Username
                String baseUsername = generateUsername(firstName, middleName, lastName);
                String username = resolveUniqueUsername(baseUsername);

                // Generate secure random temporary password (min 8 chars, containing upper, lower, number, special)
                String rawPassword = generateSecurePassword(8);

                try {
                    // Create User account
                    User user = new User();
                    user.setUsername(username);
                    user.setPassword(encoder.encode(rawPassword));
                    user.setRole(role);
                    user.setEmail(email);
                    userRepo.save(user);

                    // Create Employee Profile
                    Employee emp = new Employee();
                    emp.setUser(user);
                    emp.setFirstName(firstName);
                    emp.setMiddleName(middleName);
                    emp.setLastName(lastName);
                    emp.setPersonalEmail(email);
                    emp.setGender("N/A");
                    emp.setEmploymentType("FULL_TIME");
                    emp.setStatus("ACTIVE");
                    emp.setEmploymentStatus("Active");
                    emp.setJoiningDate(dateOfJoin);
                    emp.setFullName(firstName + (middleName.isEmpty() ? "" : " " + middleName) + " " + lastName);

                    empRepo.save(emp);

                    // Set default supervisor
                    empRepo.findById(defaultSupervisorId).ifPresent(defaultMgr -> {
                        emp.setManager(defaultMgr);
                        empRepo.save(emp);
                    });

                    // Set default leave balances
                    int year = Year.now().getValue();
                    leaveTypeRepo.findAll().forEach(lt -> {
                        LeaveBalance lb = new LeaveBalance();
                        lb.setEmployee(emp);
                        lb.setLeaveType(lt);
                        lb.setYear(year);
                        lb.setAllocatedDays(lt.getDaysPerYear());
                        leaveBalanceRepo.save(lb);
                    });

                    // Send email notification
                    emailService.sendExcelImportCredentialsEmail(email, firstName, username, rawPassword);

                    successful++;
                    Map<String, String> successRecord = new HashMap<>();
                    successRecord.put("row", String.valueOf(r + 1));
                    successRecord.put("name", firstName + " " + lastName);
                    successRecord.put("username", username);
                    successRecord.put("email", email);
                    successList.add(successRecord);

                } catch (Exception ex) {
                    failed++;
                    errors.add("Row " + (r + 1) + ": Database insertion error: " + ex.getMessage());
                }
            }

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error reading Excel file: " + ex.getMessage()));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalProcessed", totalProcessed);
        response.put("successful", successful);
        response.put("failed", failed);
        response.put("errors", errors);
        response.put("duplicates", duplicates);
        response.put("successList", successList);
        return ResponseEntity.ok(response);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } catch (Exception e) {
                        return "";
                    }
                }
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    private LocalDate parseLocalDate(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        String val = str.trim();
        try {
            return LocalDate.parse(val);
        } catch (Exception e) {}
        try {
            return LocalDate.parse(val, DateTimeFormatter.ofPattern("M/d/yyyy"));
        } catch (Exception e) {}
        try {
            return LocalDate.parse(val, DateTimeFormatter.ofPattern("d/M/yyyy"));
        } catch (Exception e) {}
        try {
            return LocalDate.parse(val, DateTimeFormatter.ofPattern("yyyy/M/d"));
        } catch (Exception e) {}
        try {
            return LocalDate.parse(val, DateTimeFormatter.ofPattern("d-M-yyyy"));
        } catch (Exception e) {}
        return null;
    }

    // ─── Preview generated username before saving ───────────────────────────
    @GetMapping("/preview-username")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public ResponseEntity<?> previewUsername(
            @RequestParam String firstName,
            @RequestParam(required = false) String middleName,
            @RequestParam String lastName) {
        String base = generateUsername(firstName, middleName, lastName);
        String unique = resolveUniqueUsername(base);
        // Generate a preview password (non-persistent — actual password is generated at registration time)
        String defaultPwd = generateSecurePassword(10);
        return ResponseEntity.ok(Map.of(
                "username", unique,
                "defaultPassword", defaultPwd,
                "base", base,
                "taken", !unique.equals(base)));
    }


    // ─── Change own credentials (any logged-in user) ────────────────────────
    @PutMapping("/change-credentials")
    public ResponseEntity<?> changeCredentials(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepo.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newUsername  = body.get("newUsername");
        String currentPwd   = body.get("currentPassword");
        String newPwd       = body.get("newPassword");

        // Verify current password
        if (currentPwd == null || !encoder.matches(currentPwd, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        // Update username if provided and not taken
        if (newUsername != null && !newUsername.isBlank()) {
            String trimmed = newUsername.toLowerCase().trim();
            if (!trimmed.equals(user.getUsername()) && userRepo.existsByUsername(trimmed)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username '" + trimmed + "' is already taken"));
            }
            user.setUsername(trimmed);
        }

        // Update password if provided
        if (newPwd != null && !newPwd.isBlank()) {
            if (newPwd.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
            }
            user.setPassword(encoder.encode(newPwd));
        }

        userRepo.save(user);
        return ResponseEntity.ok(Map.of(
                "message", "Credentials updated successfully!",
                "username", user.getUsername()));
    }

    // ─── Admin resets another user's password ───────────────────────────────
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return userRepo.findById(id).map(user -> {
            // Get employee's first name for default password
            String newPwd = (body != null && body.containsKey("password"))
                    ? body.get("password")
                    : empRepo.findByUserId(id)
                        .map(e -> e.getFirstName().toLowerCase() + "@1234")
                        .orElse("password@1234");
            user.setPassword(encoder.encode(newPwd));
            userRepo.save(user);
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successfully",
                    "newPassword", newPwd));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─── List all users (Admin only) ────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public List<Map<String, Object>> getAllUsers() {
        return userRepo.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole().getName());
            m.put("createdAt", u.getCreatedAt());
            empRepo.findByUserId(u.getId()).ifPresent(emp -> {
                m.put("employeeId", emp.getId());
                m.put("employeeName", emp.getFirstName() + " " + emp.getLastName());
                m.put("department", emp.getDepartment() != null ? emp.getDepartment().getName() : null);
                m.put("status", emp.getStatus());
            });
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public List<Role> getAllRoles() {
        return roleRepo.findAll();
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    /**
     * Generates username based on First Name, Middle Name(s), and Last Name.
     */
    private String generateUsername(String firstName, String middleName, String lastName) {
        String first = firstName.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        String last = lastName.trim().toLowerCase().replaceAll("\\s+", "").replaceAll("[^a-z0-9]", "");
        
        String middleLetter = "";
        if (middleName != null && !middleName.trim().isEmpty()) {
            String[] parts = middleName.trim().split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                String firstMiddle = parts[0].toLowerCase().replaceAll("[^a-z0-9]", "");
                if (!firstMiddle.isEmpty()) {
                    middleLetter = String.valueOf(firstMiddle.charAt(0));
                }
            }
        }
        
        if (middleLetter.isEmpty()) {
            return first + "." + last;
        } else {
            return first + "." + middleLetter + "." + last;
        }
    }

    private String resolveUniqueUsername(String base) {
        if (!userRepo.existsByUsername(base)) return base;
        int suffix = 1;
        while (userRepo.existsByUsername(base + suffix)) {
            suffix++;
        }
        return base + suffix;
    }

    /**
     * Generates a secure random password of specified length containing:
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character
     */
    private String generateSecurePassword(int length) {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String special = "@#$*%!";
        String all = upper + lower + digits + special;
        
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        // Force one of each required category
        sb.append(upper.charAt(rnd.nextInt(upper.length())));
        sb.append(lower.charAt(rnd.nextInt(lower.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(special.charAt(rnd.nextInt(special.length())));
        
        // Fill the rest with any allowed characters
        for (int i = 4; i < length; i++) {
            sb.append(all.charAt(rnd.nextInt(all.length())));
        }
        
        // Shuffle the characters to randomize positions
        List<Character> chars = sb.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
        Collections.shuffle(chars, rnd);
        
        StringBuilder shuffled = new StringBuilder();
        for (char c : chars) {
            shuffled.append(c);
        }
        return shuffled.toString();
    }
}
