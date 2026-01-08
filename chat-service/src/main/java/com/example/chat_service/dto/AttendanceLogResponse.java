package com.example.chat_service.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import com.example.chat_service.entity.AttendanceLog;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceLogResponse {
    private LocalDate workDate;
    private LocalTime checkIn;
    private LocalTime checkOut;
    private String status;
    private int workingMinutes; // primitive int
    private int dailyWage;      // primitive int

    // 생성자 (Entity -> DTO 변환용)
    public AttendanceLogResponse(AttendanceLog log) {
        this.workDate = log.getWorkDate();
        this.checkIn = log.getCheckIn();
        this.checkOut = log.getCheckOut();
        this.status = log.getStatus();

        // 🔥 중요: 아래처럼 바로 "null 체크"를 한 결과값만 대입해야 합니다.
        // 기존에 있던 "this.workingMinutes = log.getWorkingMinutes();" 줄은 반드시 삭제하세요!
        this.workingMinutes = (log.getWorkingMinutes() != null) ? log.getWorkingMinutes() : 0;
        this.dailyWage = (log.getDailyWage() != null) ? log.getDailyWage() : 0;
    }
}