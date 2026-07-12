package com.hrms.leave;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "leave_types")
@Data
public class LeaveType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "is_paid")
    private boolean paid = true;

    @Column(name = "document_required")
    private boolean documentRequired = false;

    @Column(name = "carry_forward_allowed")
    private boolean carryForwardAllowed = false;

    @Column(name = "max_carry_forward_days")
    private int maxCarryForwardDays = 0;

    @Column(name = "half_day_allowed")
    private boolean halfDayAllowed = true;

    @Column(name = "days_per_year")
    private int daysPerYear = 0;
}
