package com.example.hr_service.repository;

import com.example.hr_service.entity.AttendanceLog;
import com.example.hr_service.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    // 특정 사원의 특정 날짜 기록이 있는지 찾는 기능
    Optional<AttendanceLog> findByEmployeeAndWorkDate(Employee employee, LocalDate workDate);

    List<AttendanceLog> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate start, LocalDate end);

    List<AttendanceLog> findByWorkDateBetween(LocalDate start, LocalDate end);

    List<AttendanceLog> findByWorkDateAndCheckOutIsNull(LocalDate date);

    // AttendanceLogRepository.java에 반드시 추가
    Optional<AttendanceLog> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
}