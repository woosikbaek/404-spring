package com.example.hr_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee") // Flask의 __tablename__과 일치
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_number", unique = true, nullable = false, length = 8)
    private String employeeNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(nullable = false, length = 50)
    private String position;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "monthly_salary", nullable = false)
    private Integer monthlySalary = 0;

    @Column(name = "hourly_rate", nullable = false)
    private Integer hourlyRate = 0;

    @Column(name = "annual_leave")
    private Double annualLeave = 2.0;

    @Column(name = "sick_leave")
    private Integer sickLeave = 2;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Flask의 onupdate=datetime.now 기능을 대신함
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}