package com.hrms.leave;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "leave_policies")
@Data
public class LeavePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "days_allowed")
    private int daysAllowed;

    @Column(name = "carry_forward_allowed")
    private boolean carryForwardAllowed = false;

    @Column(name = "max_carry_forward_days")
    private int maxCarryForwardDays = 0;
}
