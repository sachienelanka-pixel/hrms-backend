package com.hrms.leave;

import com.hrms.auth.UserDetailsImpl;
import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    @Autowired private LeaveRequestRepository leaveRequestRepo;
    @Autowired private LeaveBalanceRepository leaveBalanceRepo;
    @Autowired private LeaveTypeRepository leaveTypeRepo;
    @Autowired private EmployeeRepository employeeRepo;
    @Autowired private WorkingDayCalculator workingDayCalculator;

    @GetMapping("/types")
    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepo.findAll();
    }

    @PostMapping("/types")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public LeaveType createLeaveType(@RequestBody LeaveType leaveType) {
        return leaveTypeRepo.save(leaveType);
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteLeaveType(@PathVariable Long id) {
        return leaveTypeRepo.findById(id).map(lt -> {
            leaveTypeRepo.delete(lt);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getMyBalance(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepo.findByUserId(userDetails.getId())
                .map(emp -> ResponseEntity.ok(leaveBalanceRepo.findByEmployeeId(emp.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Calculate working days between two dates (excludes weekends + SL holidays) */
    @GetMapping("/calculate-days")
    public ResponseEntity<?> calculateDays(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end   = LocalDate.parse(endDate);
            if (end.isBefore(start)) {
                return ResponseEntity.badRequest().body(Map.of("error", "End date cannot be before start date"));
            }
            double days = workingDayCalculator.calculateWorkingDays(start, end);
            return ResponseEntity.ok(Map.of("workingDays", days));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyLeave(@RequestBody LeaveRequest request, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Employee emp = employeeRepo.findByUserId(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LeaveType lt = leaveTypeRepo.findById(request.getLeaveType().getId())
                .orElseThrow(() -> new RuntimeException("Leave type not found"));

        if (request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Start date and end date are required"));
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            return ResponseEntity.badRequest().body(Map.of("error", "End date cannot be before start date"));
        }

        // Validate overlap with existing PENDING or APPROVED leave requests
        List<LeaveRequest> existingRequests = leaveRequestRepo.findByEmployeeId(emp.getId());
        for (LeaveRequest existing : existingRequests) {
            if ("PENDING".equals(existing.getStatus()) || "APPROVED".equals(existing.getStatus())) {
                if (request.getId() != null && existing.getId().equals(request.getId())) {
                    continue;
                }
                if (existing.getStartDate().compareTo(request.getEndDate()) <= 0 &&
                    existing.getEndDate().compareTo(request.getStartDate()) >= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "Leave request conflicts with an existing pending or approved leave request. Please cancel or modify the conflicting request first."));
                }
            }
        }

        // Auto-compute working days ignoring weekends and public holidays
        if (request.getStartDate() != null && request.getEndDate() != null) {
            double computed = workingDayCalculator.calculateWorkingDays(request.getStartDate(), request.getEndDate());
            if (request.isHalfDay()) computed = 0.5;
            request.setLeaveDays(computed);
        }

        // ── Intern / Associate Leave Quota Rules ─────────────────────────────────
        // Applies to all leave types EXCEPT Duty Leave and Official Leave.
        // Rules (based on months since joining date):
        //   Month 1          → 0 leaves allowed
        //   Months 2–6       → 2 leaves total (cumulative)
        //   Month 7 onwards  → 1 leave per month earned (e.g. month 7 = 1, month 8 = 2, ...)
        String empType = emp.getEmploymentType() != null ? emp.getEmploymentType().toLowerCase() : "";
        String desigTitle = emp.getDesignation() != null && emp.getDesignation().getTitle() != null
                ? emp.getDesignation().getTitle().toLowerCase() : "";
        boolean isInternOrAssociate = empType.contains("intern") || desigTitle.contains("intern") || desigTitle.contains("associate");
        String ltNameLower = lt.getName().toLowerCase();
        boolean isDutyOrOfficial = ltNameLower.contains("duty") || ltNameLower.contains("official");

        if (isInternOrAssociate && !isDutyOrOfficial && emp.getJoiningDate() != null) {
            java.time.LocalDate joinDate = emp.getJoiningDate();
            java.time.LocalDate today = java.time.LocalDate.now();
            long monthsSinceJoin = java.time.temporal.ChronoUnit.MONTHS.between(
                    joinDate.withDayOfMonth(1), today.withDayOfMonth(1));

            // Month 1 (monthsSinceJoin == 0): No leaves allowed
            if (monthsSinceJoin < 1) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "No leaves are allowed during the first month of joining."));
            }

            // Calculate allowed quota
            double allowedDays;
            if (monthsSinceJoin <= 5) {
                // Months 2–6: flat 2 days total
                allowedDays = 2.0;
            } else {
                // Month 7 onwards: 1 day per month since month 7 (monthsSinceJoin - 5 gives months from 7th onward)
                allowedDays = monthsSinceJoin - 5;
            }

            // Get total already used/pending leaves (excluding duty & official)
            double usedDays = leaveRequestRepo.sumUsedLeavesByEmployeeIdExcludingDutyAndOfficial(emp.getId());

            if (usedDays + request.getLeaveDays() > allowedDays) {
                double remaining = Math.max(0, allowedDays - usedDays);
                return ResponseEntity.badRequest().body(Map.of("error",
                        String.format("Leave quota exceeded. You have %.1f day(s) remaining out of your %.1f day allowance.", remaining, allowedDays)));
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

        if (lt.isPaid()) {
            int currentYear = Year.now().getValue();
            var balance = leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(emp.getId(), lt.getId(), currentYear);
            if (balance.isPresent() && balance.get().getRemainingDays() < request.getLeaveDays()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Insufficient leave balance"));
            }
        }

        request.setEmployee(emp);
        request.setStatus("PENDING");
        return ResponseEntity.ok(leaveRequestRepo.save(request));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<?> getMyRequests(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepo.findByUserId(userDetails.getId())
                .map(emp -> ResponseEntity.ok(leaveRequestRepo.findByEmployeeId(emp.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/team-requests")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER') or hasRole('HR_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<?> getTeamRequests(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (isSuperAdmin) {
            return ResponseEntity.ok(leaveRequestRepo.findAll());
        }

        return employeeRepo.findByUserId(userDetails.getId())
                .map(mgr -> ResponseEntity.ok(leaveRequestRepo.findByManagerId(mgr.getId())))
                .orElse(ResponseEntity.ok(java.util.Collections.emptyList()));
    }

    @GetMapping("/all-requests")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<LeaveRequest> getAllRequests() {
        return leaveRequestRepo.findAll();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER')")
    public ResponseEntity<?> approveLeave(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        Employee reviewer = employeeRepo.findByUserId(userDetails.getId()).orElse(null);

        return leaveRequestRepo.findById(id).map(req -> {
            if (!isSuperAdmin) {
                if (reviewer == null || req.getEmployee() == null || req.getEmployee().getManager() == null || 
                    !req.getEmployee().getManager().getId().equals(reviewer.getId())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: employee does not report to you"));
                }
            }

            req.setStatus("APPROVED");
            req.setApprovalDate(LocalDateTime.now());
            if (reviewer != null) {
                req.setApprovedBy(reviewer);
            }
            
            // Deduct balance
            int year = req.getStartDate().getYear();
            leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(
                    req.getEmployee().getId(), req.getLeaveType().getId(), year)
                .ifPresent(balance -> {
                    balance.setUsedDays(balance.getUsedDays() + req.getLeaveDays());
                    leaveBalanceRepo.save(balance);
                });

            return ResponseEntity.ok(leaveRequestRepo.save(req));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER')")
    public ResponseEntity<?> rejectLeave(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        Employee reviewer = employeeRepo.findByUserId(userDetails.getId()).orElse(null);

        return leaveRequestRepo.findById(id).map(req -> {
            if (!isSuperAdmin) {
                if (reviewer == null || req.getEmployee() == null || req.getEmployee().getManager() == null || 
                    !req.getEmployee().getManager().getId().equals(reviewer.getId())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: employee does not report to you"));
                }
            }

            req.setStatus("REJECTED");
            req.setRejectionReason(body.getOrDefault("reason", ""));
            return ResponseEntity.ok(leaveRequestRepo.save(req));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelLeave(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Employee emp = employeeRepo.findByUserId(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return leaveRequestRepo.findById(id).map(req -> {
            if (req.getEmployee() == null || !req.getEmployee().getId().equals(emp.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied: you can only cancel your own leave requests"));
            }

            if ("CANCELLED".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Leave request is already cancelled"));
            }
            if ("REJECTED".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot cancel a rejected leave request"));
            }

            // Refund balance if it was APPROVED
            if ("APPROVED".equals(req.getStatus())) {
                int year = req.getStartDate().getYear();
                leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(
                        req.getEmployee().getId(), req.getLeaveType().getId(), year)
                    .ifPresent(balance -> {
                        balance.setUsedDays(Math.max(0.0, balance.getUsedDays() - req.getLeaveDays()));
                        leaveBalanceRepo.save(balance);
                    });
            }

            req.setStatus("CANCELLED");
            return ResponseEntity.ok(leaveRequestRepo.save(req));
        }).orElse(ResponseEntity.notFound().build());
    }
}
