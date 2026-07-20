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
import java.time.Period;
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

        // ── Intern / Associate Official Leave: compute tenure-based quota (DB always stores 0) ──
        boolean isOfficialLeave = lt.getName().toLowerCase().contains("official");
        String empDesig = emp.getDesignation() != null ? emp.getDesignation().getTitle().toLowerCase() : "";
        String empType  = emp.getEmploymentType() != null ? emp.getEmploymentType().toLowerCase() : "";
        boolean empIsInternOrAssociate = empDesig.contains("intern") || empDesig.contains("associate")
                || empType.contains("intern");

        if (isOfficialLeave && empIsInternOrAssociate) {
            // Compute months since joining (same logic as frontend)
            int monthsSinceJoin = 0;
            if (emp.getJoiningDate() != null) {
                Period period = Period.between(emp.getJoiningDate(), LocalDate.now());
                monthsSinceJoin = period.getYears() * 12 + period.getMonths();
                // Subtract 1 if today's day-of-month is before the joining day-of-month
                if (LocalDate.now().getDayOfMonth() < emp.getJoiningDate().getDayOfMonth()) {
                    monthsSinceJoin--;
                }
            }

            // Tenure-based Official Leave quota (mirrors frontend getOfficialLeaveAllowedDays)
            double allowed;
            if (monthsSinceJoin < 1) {
                allowed = 0;
            } else if (monthsSinceJoin <= 5) {
                allowed = 2;          // months 2–6: flat 2 days total
            } else {
                allowed = monthsSinceJoin - 5;  // month 7+: 1 day per month earned
            }

            // Count already-used Official Leave (PENDING + APPROVED)
            double usedOfficial = existingRequests.stream()
                    .filter(r -> "PENDING".equals(r.getStatus()) || "APPROVED".equals(r.getStatus()))
                    .filter(r -> r.getLeaveType() != null
                            && r.getLeaveType().getName().toLowerCase().contains("official"))
                    .mapToDouble(LeaveRequest::getLeaveDays)
                    .sum();

            if (usedOfficial + request.getLeaveDays() > allowed) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Insufficient Official Leave balance. Earned: " + (int) allowed
                                + " day(s), already used/pending: " + usedOfficial + " day(s)."));
            }
            // Quota satisfied — skip the normal balance check below
        } else if (lt.isPaid()) {
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
