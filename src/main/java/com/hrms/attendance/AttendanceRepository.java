package com.hrms.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByEmployeeId(Long employeeId);
    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    List<Attendance> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to);
}
