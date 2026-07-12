package com.hrms.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUserId(Long userId);
    List<Employee> findByDepartmentId(Long departmentId);

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId")
    List<Employee> findByManagerId(@Param("managerId") Long managerId);

    boolean existsByPersonalEmail(String personalEmail);

    @Query("SELECT e FROM Employee e WHERE e.dateOfBirth IS NOT NULL AND MONTH(e.dateOfBirth) = :month AND DAY(e.dateOfBirth) = :day")
    List<Employee> findByBirthMonthAndDay(@Param("month") int month, @Param("day") int day);
}

