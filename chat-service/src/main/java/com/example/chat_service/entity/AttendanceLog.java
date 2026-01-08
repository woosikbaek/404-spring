package com.example.chat_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사원 테이블과 연결 (N:1)
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate workDate; // 오늘 날짜
    private LocalTime checkIn; // 출근 시간
    private LocalTime checkOut; // 퇴근 시간
    private String status; // 출근, 지각, 결근, 연차 등

    public void markCheckIn(LocalTime time) {
        this.checkIn = time;
        // 09:00:00 부터는 무조건 지각 (9시 0분 0초 포함)
        if (time.isBefore(LocalTime.of(9, 0, 0))) {
            this.status = "출근";
        } else {
            this.status = "지각";
        }
    }

    // ... 기존 필드들 (id, employee, workDate, checkIn, checkOut, status 등)

    @Column(name = "working_minutes")
    private Integer workingMinutes; // 실제 근무 시간 (분 단위)

    @Column(name = "daily_wage")
    private Integer dailyWage; // 오늘 하루 번 돈

    // 퇴근 시간을 세팅하기 위한 Setter가 없다면 추가 (또는 클래스 상단에 @Setter)
    public void setCheckOut(LocalTime checkOut) {
        this.checkOut = checkOut;
    }

    public void setWorkingMinutes(Integer workingMinutes) {
        this.workingMinutes = workingMinutes;
    }

    public void setDailyWage(Integer dailyWage) {
        this.dailyWage = dailyWage;
    }

}
