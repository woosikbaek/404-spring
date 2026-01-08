package com.example.chat_service.repository;

import com.example.chat_service.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    Optional<Employee> findByEmployeeNumber(String employeeNumber);

    // [병가 1개 차감] 0보다 클 때만 -1
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Employee e SET e.sickLeave = e.sickLeave - 1 WHERE e.id = :id AND e.sickLeave > 0")
    int decrementSickLeave(@Param("id") Long id);

    // [병가 1개 복구] +1
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Employee e SET e.sickLeave = e.sickLeave + 1 WHERE e.id = :id")
    int incrementSickLeave(@Param("id") Long id);

    // [연차 차감] Double 타입 사용 (1.0 또는 0.5)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Employee e SET e.annualLeave = e.annualLeave - :amount WHERE e.id = :id AND e.annualLeave >= :amount")
    int decrementAnnualLeave(@Param("id") Long id, @Param("amount") Double amount);

    // [연차 복구] Double 타입 사용
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Employee e SET e.annualLeave = e.annualLeave + :amount WHERE e.id = :id")
    int incrementAnnualLeave(@Param("id") Long id, @Param("amount") Double amount);
}