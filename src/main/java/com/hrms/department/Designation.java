package com.hrms.department;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "designations")
@Data
public class Designation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String title;

    private String description;
}
