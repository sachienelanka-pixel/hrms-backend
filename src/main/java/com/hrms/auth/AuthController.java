package com.hrms.auth;
import com.hrms.user.UserRepository;
import com.hrms.employee.EmployeeRepository;
import com.hrms.attendance.AttendanceRepository;
import com.hrms.attendance.Attendance;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    AttendanceRepository attendanceRepository;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();    
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        // Auto check-in upon login
        employeeRepository.findByUserId(userDetails.getId()).ifPresent(emp -> {
            LocalDate today = LocalDate.now();
            boolean alreadyCheckedIn = attendanceRepository.findByEmployeeIdAndWorkDate(emp.getId(), today).isPresent();
            if (!alreadyCheckedIn) {
                Attendance att = new Attendance();
                att.setEmployee(emp);
                att.setWorkDate(today);
                LocalTime checkInTime = LocalTime.now();
                att.setCheckInTime(checkInTime);
                boolean late = checkInTime.isAfter(LocalTime.of(9, 0));
                att.setLate(late);
                att.setStatus(late ? "LATE" : "PRESENT");
                attendanceRepository.save(att);
            }
        });

        return ResponseEntity.ok(new JwtResponse(jwt, 
                                                 userDetails.getId(), 
                                                 userDetails.getUsername(), 
                                                 roles));
    }
}
