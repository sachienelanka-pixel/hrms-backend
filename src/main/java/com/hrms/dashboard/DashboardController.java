package com.hrms.dashboard;

import com.hrms.auth.UserDetailsImpl;
import com.hrms.employee.EmployeeRepository;
import com.hrms.leave.LeaveRequestRepository;
import com.hrms.task.DailyTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private EmployeeRepository employeeRepo;
    @Autowired private LeaveRequestRepository leaveRequestRepo;
    @Autowired private DailyTaskRepository taskRepo;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER')")
    public Map<String, Object> getStats(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        boolean isAdminOrHR = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_HR_MANAGER")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        Map<String, Object> stats = new HashMap<>();

        if (isAdminOrHR) {
            stats.put("totalEmployees", employeeRepo.count());
            stats.put("activeEmployees", employeeRepo.findAll().stream()
                    .filter(e -> "ACTIVE".equals(e.getStatus())).count());
            stats.put("pendingLeaves", leaveRequestRepo.findByStatus("PENDING").size());
            stats.put("totalLeaveRequests", leaveRequestRepo.count());
            stats.put("pendingTasks", taskRepo.countByStatus("PENDING"));
        } else if (isManager) {
            employeeRepo.findByUserId(userDetails.getId()).ifPresentOrElse(mgr -> {
                long teamSize = employeeRepo.findByManagerId(mgr.getId()).size();
                long teamPendingLeaves = leaveRequestRepo.findByManagerId(mgr.getId()).stream()
                        .filter(r -> "PENDING".equals(r.getStatus())).count();
                long teamTotalLeaves = leaveRequestRepo.findByManagerId(mgr.getId()).size();
                long teamPendingTasks = taskRepo.findByManagerId(mgr.getId()).stream()
                        .filter(t -> "PENDING".equals(t.getStatus())).count();

                stats.put("totalEmployees", teamSize);
                stats.put("activeEmployees", teamSize);
                stats.put("pendingLeaves", teamPendingLeaves);
                stats.put("totalLeaveRequests", teamTotalLeaves);
                stats.put("pendingTasks", teamPendingTasks);
                stats.put("isTeamStats", true);
            }, () -> {
                stats.put("totalEmployees", 0);
                stats.put("activeEmployees", 0);
                stats.put("pendingLeaves", 0);
                stats.put("totalLeaveRequests", 0);
                stats.put("pendingTasks", 0);
            });
        }

        return stats;
    }
}
