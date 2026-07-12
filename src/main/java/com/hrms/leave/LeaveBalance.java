package com.hrms.leave;

import com.hrms.employee.Employee;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "leave_balances")
@Data
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year")
    private int year;

    @Column(name = "allocated_days")
    private double allocatedDays;

    @Column(name = "used_days")
    private double usedDays = 0.0;

    @Column(name = "carried_forward_days")
    private double carriedForwardDays = 0.0;

    public double getRemainingDays() {
        return (allocatedDays + carriedForwardDays) - usedDays;
    }
}
