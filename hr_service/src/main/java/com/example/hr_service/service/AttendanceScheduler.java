package com.example.hr_service.service;

import com.example.hr_service.entity.AttendanceLog;
import com.example.hr_service.repository.AttendanceLogRepository;
import com.example.hr_service.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(cron = "1 0 0 * * *")
    @Transactional
    public void processMissingCheckOut() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<AttendanceLog> missingLogs = attendanceRepository.findByWorkDateAndCheckOutIsNull(yesterday);
        for (AttendanceLog attLog : missingLogs) {
            if ("출근".equals(attLog.getStatus())) attLog.setStatus("미퇴근(결근)");
            else if ("지각".equals(attLog.getStatus())) attLog.setStatus("지각(미퇴근)");
            attLog.setDailyWage(0); attLog.setWorkingMinutes(0);
            attendanceRepository.save(attLog);
        }
    }

    @Scheduled(cron = "0 1 18 * * MON-FRI")
    @Transactional
    public void processAbsenteeism() {
        LocalDate today = LocalDate.now();
        employeeRepository.findAll().forEach(employee -> {
            if (attendanceRepository.findByEmployeeAndWorkDate(employee, today).isEmpty()) {
                AttendanceLog absentLog = AttendanceLog.builder()
                        .employee(employee).workDate(today).status("결근").dailyWage(0).workingMinutes(0).build();
                attendanceRepository.save(absentLog);

                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "ABSENT"); msg.put("status", "결근"); msg.put("date", today.toString());
                messagingTemplate.convertAndSend("/topic/attendance/" + employee.getId(), (Object) msg);
            }
        });
    }
}