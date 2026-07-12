package com.hrms.config;

import com.hrms.auth.UserDetailsImpl;
import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("employeeSecurity")
public class EmployeeSecurity {

    @Autowired
    private EmployeeRepository employeeRepository;

    public boolean isSameEmployee(Authentication authentication, Long employeeId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return false;
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepository.findByUserId(userDetails.getId())
                .map(employee -> employee.getId().equals(employeeId))
                .orElse(false);
    }

    public boolean canViewOrManageEmployee(Authentication authentication, Long employeeId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return false;
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // 1. Super Admin, Admin, and HR Manager have full system-wide access
        boolean hasFullAccess = userDetails.getAuthorities().stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                a.getAuthority().equals("ROLE_ADMIN") ||
                a.getAuthority().equals("ROLE_HR_MANAGER")
        );
        if (hasFullAccess) {
            return true;
        }

        // Find current user's employee profile
        Employee currentEmp = employeeRepository.findByUserId(userDetails.getId()).orElse(null);
        if (currentEmp == null) {
            return false;
        }

        // 2. Employees have access to their own profile
        if (currentEmp.getId().equals(employeeId)) {
            return true;
        }

        // 3. Supervisors/Managers have access to their assigned team members
        Employee targetEmp = employeeRepository.findById(employeeId).orElse(null);
        if (targetEmp != null && targetEmp.getManager() != null && targetEmp.getManager().getId().equals(currentEmp.getId())) {
            // Check if reviewer is a supervisor/manager
            return userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || 
                                   a.getAuthority().equals("ROLE_HR_MANAGER") || 
                                   a.getAuthority().equals("ROLE_MANAGER"));
        }

        return false;
    }
}
