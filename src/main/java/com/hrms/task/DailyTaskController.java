package com.hrms.task;

import com.hrms.auth.UserDetailsImpl;
import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import com.hrms.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class DailyTaskController {

    @Autowired private DailyTaskRepository taskRepo;
    @Autowired private EmployeeRepository empRepo;
    @Autowired private S3Service s3Service;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    // ── Submit daily task (all authenticated users) ──────────────────────────
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> submitTask(
            @RequestParam("taskDate") String taskDateStr,
            @RequestParam("taskDescription") String taskDescription,
            @RequestParam(value = "lateReason", required = false) String lateReason,
            @RequestParam("evidence1") MultipartFile evidence1,
            @RequestParam("evidence2") MultipartFile evidence2,
            Authentication authentication) {

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Employee employee = empRepo.findByUserId(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Employee profile not found for this user"));

        if (taskDescription == null || taskDescription.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task description is required"));
        }
        if (evidence1 == null || evidence1.isEmpty() || evidence2 == null || evidence2.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both evidence screenshots are required"));
        }

        LocalDate taskDate = LocalDate.parse(taskDateStr);
        boolean isLate = taskDate.isBefore(LocalDate.now());

        if (isLate && (lateReason == null || lateReason.isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A reason is required for late submissions (past date)"));
        }

        // Save uploaded evidence files
        try {
            String folder = "tasks/" + employee.getId();
            String fileUrl1 = s3Service.uploadFile(evidence1, folder);
            String fileUrl2 = s3Service.uploadFile(evidence2, folder);

            DailyTask task = new DailyTask();
            task.setEmployee(employee);
            task.setTaskDate(taskDate);
            task.setTaskDescription(taskDescription);
            task.setEvidence1Url(fileUrl1);
            task.setEvidence2Url(fileUrl2);
            task.setLate(isLate);
            task.setLateReason(lateReason);
            task.setStatus("PENDING");

            taskRepo.save(task);

            return ResponseEntity.ok(Map.of(
                    "message", "Daily task submitted successfully!",
                    "taskId", task.getId(),
                    "date", taskDate.toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to save evidence files: " + e.getMessage()));
        }
    }

    // ── My own tasks — NO evidence shown ─────────────────────────────────────
    @GetMapping("/my")
    public List<Map<String, Object>> getMyTasks(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return empRepo.findByUserId(userDetails.getId())
                .map(emp -> taskRepo.findByEmployeeIdOrderByTaskDateDesc(emp.getId())
                        .stream()
                        .map(this::toSafeDTO)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    // ── Team tasks for Manager/HR/Admin — WITH evidence ──────────────────────
    @GetMapping("/team")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER') or hasRole('HR_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<?> getTeamTasks(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        List<DailyTask> tasks;
        if (isSuperAdmin) {
            tasks = taskRepo.findAll().stream()
                    .sorted(Comparator.comparing(DailyTask::getTaskDate).reversed())
                    .collect(Collectors.toList());
        } else {
            tasks = empRepo.findByUserId(userDetails.getId())
                    .map(mgr -> taskRepo.findByManagerId(mgr.getId()))
                    .orElse(Collections.emptyList());
        }

        return ResponseEntity.ok(tasks.stream().map(this::toFullDTO).collect(Collectors.toList()));
    }

    // ── Approve or Reject a task ──────────────────────────────────────────────
    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER') or hasRole('HR_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<?> reviewTask(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String action = body.get("action");  // "APPROVED" or "REJECTED"
        String remarks = body.getOrDefault("remarks", "");

        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Action must be APPROVED or REJECTED"));
        }

        boolean isSuperAdmin = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        Employee reviewer = empRepo.findByUserId(userDetails.getId()).orElse(null);

        return taskRepo.findById(id).map(task -> {
            if (!isSuperAdmin) {
                if (reviewer == null || task.getEmployee() == null || task.getEmployee().getManager() == null || 
                    !task.getEmployee().getManager().getId().equals(reviewer.getId())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: employee does not report to you"));
                }
            }

            task.setStatus(action);
            task.setRemarks(remarks);
            task.setReviewedAt(LocalDateTime.now());
            if (reviewer != null) {
                task.setReviewedBy(reviewer);
            }

            // ── Incremental Duration Logic (GAP 4) ──────────────────────────
            if ("REJECTED".equals(action)) {
                String extStr = body.getOrDefault("extensionDays", "0");
                double ext = 0;
                try { ext = Double.parseDouble(extStr); } catch (NumberFormatException ignored) {}
                if (ext > 0) {
                    task.setExtensionDays(ext);
                    // Accumulate on employee
                    com.hrms.employee.Employee taskEmp = task.getEmployee();
                    if (taskEmp != null) {
                        double current = taskEmp.getTotalExtensionDays() == null ? 0 : taskEmp.getTotalExtensionDays();
                        taskEmp.setTotalExtensionDays(current + ext);
                        empRepo.save(taskEmp);
                    }
                }
            }

            taskRepo.save(task);
            return ResponseEntity.ok(Map.of("message", "Task " + action.toLowerCase() + " successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER') or hasRole('HR_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteTask(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdminOrStaff = userDetails.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                a.getAuthority().equals("ROLE_ADMIN") ||
                a.getAuthority().equals("ROLE_HR_MANAGER")
        );
        Employee reviewer = empRepo.findByUserId(userDetails.getId()).orElse(null);

        return taskRepo.findById(id).map(task -> {
            if (!isSuperAdminOrStaff) {
                if (reviewer == null || task.getEmployee() == null || task.getEmployee().getManager() == null ||
                    !task.getEmployee().getManager().getId().equals(reviewer.getId())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: employee does not report to you"));
                }
            }

            // Deduct extension days from employee if the task was REJECTED with extension days
            if ("REJECTED".equals(task.getStatus()) && task.getExtensionDays() != null && task.getExtensionDays() > 0) {
                Employee taskEmp = task.getEmployee();
                if (taskEmp != null) {
                    double current = taskEmp.getTotalExtensionDays() == null ? 0 : taskEmp.getTotalExtensionDays();
                    taskEmp.setTotalExtensionDays(Math.max(0, current - task.getExtensionDays()));
                    empRepo.save(taskEmp);
                }
            }

            taskRepo.delete(task);
            return ResponseEntity.ok(Map.of("message", "Task deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /** Employee view — evidence URLs excluded */
    private Map<String, Object> toSafeDTO(DailyTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskDate", t.getTaskDate() != null ? t.getTaskDate().toString() : null);
        m.put("taskDescription", t.getTaskDescription());
        m.put("submittedAt", t.getSubmittedAt() != null
                ? t.getSubmittedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
        m.put("status", t.getStatus());
        m.put("remarks", t.getRemarks());
        m.put("late", t.isLate());
        m.put("lateReason", t.getLateReason());
        // Evidence intentionally excluded
        return m;
    }

    /** Manager view — includes employee name + evidence URLs */
    private Map<String, Object> toFullDTO(DailyTask t) {
        Map<String, Object> m = toSafeDTO(t);
        m.put("evidence1Url", t.getEvidence1Url());
        m.put("evidence2Url", t.getEvidence2Url());
        m.put("extensionDays", t.getExtensionDays());
        if (t.getEmployee() != null) {
            m.put("employeeName", t.getEmployee().getFirstName() + " " + t.getEmployee().getLastName());
            m.put("employeeId", t.getEmployee().getId());
            m.put("employeeTotalExtension", t.getEmployee().getTotalExtensionDays());
        }
        if (t.getReviewedBy() != null) {
            m.put("reviewedByName", t.getReviewedBy().getFirstName() + " " + t.getReviewedBy().getLastName());
        }
        m.put("reviewedAt", t.getReviewedAt() != null
                ? t.getReviewedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
        return m;
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
