package com.hrms.user;

import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import com.hrms.service.EmailService;
import com.hrms.service.BirthdayScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/broadcasts")
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
public class BroadcastController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BirthdayScheduler birthdayScheduler;

    @PostMapping("/send")
    public ResponseEntity<?> sendBroadcast(@RequestBody Map<String, String> payload) {
        String type = payload.getOrDefault("type", "CUSTOM").toUpperCase();
        String subject = payload.getOrDefault("subject", "HR Announcement");
        String messageText = payload.getOrDefault("message", "");

        if (messageText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message content cannot be blank."));
        }

        List<Employee> activeEmployees = employeeRepository.findAll();
        int count = 0;
        for (Employee emp : activeEmployees) {
            String email = emp.getPersonalEmail();
            if (email == null || email.isBlank()) {
                if (emp.getUser() != null) {
                    email = emp.getUser().getEmail();
                }
            }
            if (email != null && !email.isBlank()) {
                emailService.sendBroadcastEmail(email, type, subject, messageText);
                count++;
            }
        }

        return ResponseEntity.ok(Map.of("message", "Broadcast sent successfully to " + count + " employees."));
    }

    @PostMapping("/trigger-birthday-test")
    public ResponseEntity<?> triggerBirthdayTest(@RequestBody Map<String, String> payload) {
        String testEmail = payload.get("testEmail");
        if (testEmail != null && !testEmail.isBlank()) {
            emailService.sendBirthdayWishEmail(testEmail, "Test User", null);
            return ResponseEntity.ok(Map.of("message", "Test birthday email sent to " + testEmail));
        }

        birthdayScheduler.checkAndSendBirthdayWishes();
        return ResponseEntity.ok(Map.of("message", "Birthday check task run completed."));
    }
}
