package com.hrms.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DailyTaskRepository extends JpaRepository<DailyTask, Long> {

    List<DailyTask> findByEmployeeIdOrderByTaskDateDesc(Long employeeId);

    // Manager sees tasks of employees who report to them
    @Query("SELECT t FROM DailyTask t WHERE t.employee.manager.id = :managerId ORDER BY t.taskDate DESC")
    List<DailyTask> findByManagerId(@Param("managerId") Long managerId);

    // Count pending tasks for dashboard
    long countByStatus(String status);
}
