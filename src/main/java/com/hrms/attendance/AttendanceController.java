package com.hrms.attendance;

import com.hrms.auth.UserDetailsImpl;
import com.hrms.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired private AttendanceRepository attendanceRepo;
    @Autowired private EmployeeRepository employeeRepo;

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(Authentication authentication, @RequestParam(required = false) String time) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepo.findByUserId(userDetails.getId()).map(emp -> {
            LocalDate today = LocalDate.now();
            Optional<Attendance> existing = attendanceRepo.findByEmployeeIdAndWorkDate(emp.getId(), today);
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Already checked in today"));
            }
            Attendance att = new Attendance();
            att.setEmployee(emp);
            att.setWorkDate(today);
            LocalTime checkInTime = time != null && !time.isEmpty() ? LocalTime.parse(time) : LocalTime.now();
            att.setCheckInTime(checkInTime);
            boolean late = checkInTime.isAfter(LocalTime.of(9, 0));
            att.setLate(late);
            att.setStatus(late ? "LATE" : "PRESENT");
            return ResponseEntity.ok(attendanceRepo.save(att));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Employee profile not found for this user")));
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(Authentication authentication, @RequestParam(required = false) String time) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepo.findByUserId(userDetails.getId()).map(emp -> {
            LocalDate today = LocalDate.now();
            Optional<Attendance> existing = attendanceRepo.findByEmployeeIdAndWorkDate(emp.getId(), today);
            if (existing.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "You have not checked in today"));
            }
            Attendance att = existing.get();
            if (att.getCheckOutTime() != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Already checked out today"));
            }
            LocalTime checkOutTime = time != null && !time.isEmpty() ? LocalTime.parse(time) : LocalTime.now();
            att.setCheckOutTime(checkOutTime);
            return ResponseEntity.ok(attendanceRepo.save(att));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Employee profile not found")));
    }

    @GetMapping("/today")
    public ResponseEntity<?> getTodayAttendance(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepo.findByUserId(userDetails.getId()).map(emp -> {
            Optional<Attendance> att = attendanceRepo.findByEmployeeIdAndWorkDate(emp.getId(), LocalDate.now());
            if (att.isPresent()) {
                return ResponseEntity.ok(att.get());
            } else {
                // Return explicit "not checked in" response instead of empty Optional
                Map<String, Object> notCheckedIn = new HashMap<>();
                notCheckedIn.put("checkedIn", false);
                notCheckedIn.put("message", "Not checked in today");
                return ResponseEntity.ok(notCheckedIn);
            }
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Employee profile not found")));
    }

    @GetMapping("/my-attendance")
    public ResponseEntity<?> getMyAttendance(
            Authentication authentication,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return employeeRepo.findByUserId(userDetails.getId()).map(emp -> {
            if (month != null && year != null) {
                LocalDate from = LocalDate.of(year, month, 1);
                LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
                return ResponseEntity.ok(attendanceRepo.findByEmployeeIdAndWorkDateBetween(emp.getId(), from, to));
            }
            return ResponseEntity.ok(attendanceRepo.findByEmployeeId(emp.getId()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or ( (hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER')) and @employeeSecurity.canViewOrManageEmployee(authentication, #id) )")
    public ResponseEntity<?> getEmployeeAttendance(
            @PathVariable Long id,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        if (month != null && year != null) {
            LocalDate from = LocalDate.of(year, month, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            return ResponseEntity.ok(attendanceRepo.findByEmployeeIdAndWorkDateBetween(id, from, to));
        }
        return ResponseEntity.ok(attendanceRepo.findByEmployeeId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public ResponseEntity<?> updateAttendance(@PathVariable Long id, @RequestBody Attendance details, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        com.hrms.employee.Employee reviewer = employeeRepo.findByUserId(userDetails.getId()).orElse(null);

        return attendanceRepo.findById(id).map(att -> {
            if (!isSuperAdmin) {
                if (reviewer == null || att.getEmployee() == null || att.getEmployee().getManager() == null || 
                    !att.getEmployee().getManager().getId().equals(reviewer.getId())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: employee does not report to you"));
                }
            }

            att.setStatus(details.getStatus());
            att.setCheckInTime(details.getCheckInTime());
            att.setCheckOutTime(details.getCheckOutTime());
            att.setOvertimeHours(details.getOvertimeHours());
            return ResponseEntity.ok(attendanceRepo.save(att));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Attendance> getAllAttendance() {
        return attendanceRepo.findAll();
    }
}
