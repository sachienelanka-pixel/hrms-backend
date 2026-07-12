package com.hrms.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeId(Long employeeId);
    List<LeaveRequest> findByStatus(String status);

    @Query("SELECT r FROM LeaveRequest r WHERE r.employee.manager.id = :managerId")
    List<LeaveRequest> findByManagerId(@Param("managerId") Long managerId);
}
