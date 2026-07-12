package com.hrms.attendance;

import com.hrms.employee.Employee;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance")
@Data
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    private String status; // PRESENT, ABSENT, LATE, HALF_DAY, REMOTE

    @Column(name = "is_late")
    private boolean isLate = false;

    @Column(name = "overtime_hours")
    private double overtimeHours = 0.0;
}
