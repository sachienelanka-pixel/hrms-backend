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

    /** Sum of leave days for an employee, excluding Duty and Official leave types, for PENDING or APPROVED requests */
    @Query("SELECT COALESCE(SUM(r.leaveDays), 0) FROM LeaveRequest r WHERE r.employee.id = :empId " +
           "AND (r.status = 'PENDING' OR r.status = 'APPROVED') " +
           "AND LOWER(r.leaveType.name) NOT LIKE '%duty%' " +
           "AND LOWER(r.leaveType.name) NOT LIKE '%official%'")
    double sumUsedLeavesByEmployeeIdExcludingDutyAndOfficial(@Param("empId") Long empId);
}
