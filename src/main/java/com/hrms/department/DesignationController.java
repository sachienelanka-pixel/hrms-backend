package com.hrms.department;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/designations")
public class DesignationController {
    @Autowired
    private DesignationRepository designationRepository;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public List<Designation> getAllDesignations() {
        return designationRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('HR_MANAGER')")
    public Designation createDesignation(@RequestBody Designation designation) {
        return designationRepository.save(designation);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteDesignation(@PathVariable Long id) {
        return designationRepository.findById(id).map(d -> {
            designationRepository.delete(d);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
