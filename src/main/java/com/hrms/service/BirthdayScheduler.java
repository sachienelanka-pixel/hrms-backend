package com.hrms.service;

import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class BirthdayScheduler {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmailService emailService;

    // Run at 8:00 AM every day
    @Scheduled(cron = "0 0 8 * * *")
    public void checkAndSendBirthdayWishes() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();

        List<Employee> celebrants = employeeRepository.findByBirthMonthAndDay(month, day);
        for (Employee emp : celebrants) {
            String email = emp.getPersonalEmail();
            if (email == null || email.isBlank()) {
                if (emp.getUser() != null) {
                    email = emp.getUser().getEmail();
                }
            }
            if (email != null && !email.isBlank()) {
                String fullName = emp.getFirstName() + " " + emp.getLastName();
                emailService.sendBirthdayWishEmail(email, fullName, emp.getProfilePhotoUrl());
            }
        }
    }
}
