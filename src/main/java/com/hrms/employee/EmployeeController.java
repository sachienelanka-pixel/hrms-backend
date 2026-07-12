package com.hrms.employee;

import com.hrms.auth.UserDetailsImpl;
import com.hrms.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.hrms.user.RoleRepository roleRepository;
    @Autowired private com.hrms.leave.LeaveBalanceRepository leaveBalanceRepo;
    @Autowired private com.hrms.leave.LeaveRequestRepository leaveRequestRepo;
    @Autowired private com.hrms.attendance.AttendanceRepository attendanceRepo;
    @Autowired private EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER')")
    public List<EmployeeDTO> getAllEmployees(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean hasFullAccess = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
                               a.getAuthority().equals("ROLE_ADMIN") || 
                               a.getAuthority().equals("ROLE_HR_MANAGER"));

        if (hasFullAccess) {
            return employeeRepository.findAll().stream()
                    .map(EmployeeDTO::from)
                    .collect(Collectors.toList());
        }

        // Return current supervisor's profile + all employees reporting to them
        return employeeRepository.findByUserId(userDetails.getId()).map(currentEmp -> {
            List<Employee> team = employeeRepository.findByManagerId(currentEmp.getId());
            List<EmployeeDTO> dtos = team.stream().map(EmployeeDTO::from).collect(Collectors.toList());
            dtos.add(0, EmployeeDTO.from(currentEmp)); // Include self
            return dtos;
        }).orElse(java.util.Collections.emptyList());
    }

    @GetMapping("/my-profile")
    public ResponseEntity<EmployeeDTO> getMyProfile(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepository.findByUserId(userDetails.getId())
                .map(emp -> ResponseEntity.ok(EmployeeDTO.from(emp)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@employeeSecurity.canViewOrManageEmployee(authentication, #id)")
    public ResponseEntity<EmployeeDTO> getEmployeeById(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .map(emp -> ResponseEntity.ok(EmployeeDTO.from(emp)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public ResponseEntity<?> createEmployee(@RequestBody Map<String, Object> body) {
        String validationErr = employeeService.validateEmployeeMap(body);
        if (validationErr != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationErr));
        }
        Employee employee = employeeService.updateEmployeeFromMap(new Employee(), body);
        return ResponseEntity.ok(EmployeeDTO.from(employeeRepository.save(employee)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @employeeSecurity.isSameEmployee(authentication, #id) or ( (hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER')) and @employeeSecurity.canViewOrManageEmployee(authentication, #id) )")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean isStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
                               a.getAuthority().equals("ROLE_ADMIN") || 
                               a.getAuthority().equals("ROLE_HR_MANAGER"));

        return employeeRepository.findById(id).map(employee -> {
            if (!isStaff) {
                body.remove("joiningDate");
                body.remove("employmentStatus");
                body.remove("workLocation");
                body.remove("workMode");
                body.remove("probationPeriod");
                body.remove("managerId");
                body.remove("status");
                body.remove("employeeCustomId");
                body.remove("role");
                body.remove("email");
                body.remove("totalExtensionDays");
                body.remove("departmentId");
                body.remove("designationId");
                body.remove("employmentType");
            }
            String validationErr = employeeService.validateEmployeeMap(body);
            if (validationErr != null) {
                return ResponseEntity.badRequest().body(Map.of("error", validationErr));
            }
            employeeService.updateEmployeeFromMap(employee, body);

            // Flexible Role Assignment: Update user role if specified in payload
            Object roleNameObj = body.get("role");
            if (roleNameObj != null && employee.getUser() != null) {
                String roleName = roleNameObj.toString();
                if ("ROLE_SUPER_ADMIN".equals(roleName) && !isSuperAdmin) {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: only Super Admin can assign the Super Admin role"));
                }
                com.hrms.user.Role role = roleRepository.findByName(roleName).orElse(null);
                if (role != null) {
                    employee.getUser().setRole(role);
                    userRepository.save(employee.getUser());
                }
            }

            return ResponseEntity.ok(EmployeeDTO.from(employeeRepository.save(employee)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
        return employeeRepository.findById(id).map(employee -> {
            try {
                // 1. Detach employees who report to this person (clear manager_id FK)
                employeeRepository.findByManagerId(id).forEach(sub -> {
                    sub.setManager(null);
                    employeeRepository.save(sub);
                });

                // 2. Delete leave balances for this employee
                leaveBalanceRepo.deleteAll(leaveBalanceRepo.findByEmployeeId(id));

                // 3. Delete leave requests where this employee is the requester
                leaveRequestRepo.deleteAll(leaveRequestRepo.findByEmployeeId(id));

                // 4. Null out approvedBy references pointing to this employee in other leave requests
                leaveRequestRepo.findAll().stream()
                    .filter(r -> r.getApprovedBy() != null && r.getApprovedBy().getId().equals(id))
                    .forEach(r -> { r.setApprovedBy(null); leaveRequestRepo.save(r); });

                // 5. Delete attendance records
                attendanceRepo.deleteAll(attendanceRepo.findByEmployeeId(id));

                // 6. Save userId before deleting employee
                Long userId = employee.getUser() != null ? employee.getUser().getId() : null;

                // 7. Delete the employee
                employeeRepository.delete(employee);
                employeeRepository.flush();

                // 8. Delete the linked user account
                if (userId != null) {
                    userRepository.deleteById(userId);
                }

                return ResponseEntity.ok(Map.of("message", "Employee and user account deleted successfully"));
            } catch (Exception ex) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Delete failed: " + ex.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
