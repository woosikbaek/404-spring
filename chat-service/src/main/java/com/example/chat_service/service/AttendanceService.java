package com.example.chat_service.service;

import com.example.chat_service.entity.AttendanceLog;
import com.example.chat_service.entity.Employee;
import com.example.chat_service.repository.AttendanceLogRepository;
import com.example.chat_service.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j // 이 어노테이션이 있어야 log.info 사용 가능
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceLogRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public AttendanceLog checkIn(Long id) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new RuntimeException("사원 없음"));
        if (attendanceRepository.findByEmployeeAndWorkDate(employee, LocalDate.now()).isPresent()) {
            throw new RuntimeException("이미 오늘 기록이 존재합니다.");
        }

        LocalTime now = LocalTime.now();
        String status = now.isAfter(LocalTime.of(9, 0)) ? "지각" : "출근";

        AttendanceLog attendance = AttendanceLog.builder()
                .employee(employee).workDate(LocalDate.now()).checkIn(now).status(status).build();

        AttendanceLog saved = attendanceRepository.save(attendance);
        sendWebSocketUpdate(id, "CHECK_IN", saved.getStatus(), now.toString(), 0, 0);
        return saved;
    }

    @Transactional
    public AttendanceLog checkOut(Long id) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new RuntimeException("사원 없음"));
        AttendanceLog attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, LocalDate.now())
                .orElseThrow(() -> new RuntimeException("출근 기록 없음"));

        if (attendance.getCheckOut() != null) throw new RuntimeException("이미 퇴근 처리됨");

        LocalTime now = LocalTime.now();
        attendance.setCheckOut(now);

        long totalMins = Duration.between(attendance.getCheckIn(), now).toMinutes();
        long workingMins = Math.max(0, totalMins - 60);
        int wageToday = (int) Math.floor(workingMins * (employee.getHourlyRate() / 60.0));

        attendance.setWorkingMinutes((int) workingMins);
        attendance.setDailyWage(wageToday);
        
        if ("지각".equals(attendance.getStatus())) {
            attendance.setStatus("지각/퇴근");
        } else if ("출근".equals(attendance.getStatus()) && !now.isBefore(LocalTime.of(18, 0))) {
            attendance.setStatus("퇴근");
        }

        attendanceRepository.save(attendance);
        sendWebSocketUpdate(id, "CHECK_OUT", attendance.getStatus(), now.toString(), wageToday, workingMins);
        return attendance;
    }

    @Transactional
    public void updateAttendanceStatus(Long logId, String newStatus) {
        // 변수명을 log -> attLog로 변경하여 @Slf4j의 log와 충돌 방지
        AttendanceLog attLog = attendanceRepository.findById(logId).orElseThrow(() -> new RuntimeException("로그 없음"));
        Employee employee = attLog.getEmployee();
        String oldStatus = attLog.getStatus();

        // 1. [복구]
        if ("연차".equals(oldStatus)) employee.setAnnualLeave(employee.getAnnualLeave() + 1.0);
        else if ("반차".equals(oldStatus)) employee.setAnnualLeave(employee.getAnnualLeave() + 0.5);
        else if ("병가".equals(oldStatus)) employee.setSickLeave(employee.getSickLeave() + 1);

        // 2. [차감]
        if ("연차".equals(newStatus)) {
            employee.setAnnualLeave(employee.getAnnualLeave() - 1.0);
            attLog.setDailyWage(0);
            attLog.setStatus("연차");
        } else if ("반차".equals(newStatus)) {
            employee.setAnnualLeave(employee.getAnnualLeave() - 0.5);
            attLog.setStatus("반차");
        } else if ("병가".equals(newStatus)) {
            if (employee.getSickLeave() > 0) {
                employee.setSickLeave(employee.getSickLeave() - 1);
                attLog.setStatus("병가");
            } else {
                attLog.setStatus("병가(무급)");
                attLog.setDailyWage(0);
            }
        } else if ("휴가".equals(newStatus)) {
            attLog.setStatus("휴가");
        } else {
            attLog.setStatus(newStatus);
        }

        // 3. [강제 반영]
        employeeRepository.saveAndFlush(employee);
        attendanceRepository.saveAndFlush(attLog);

        // 로거 사용 (빨간줄 해결)
        log.info("사원 {} 상태 변경: {} -> {}, 남은 병가: {}", 
                employee.getName(), oldStatus, newStatus, employee.getSickLeave());

        // 4. 웹소켓 전송
        Map<String, Object> data = new HashMap<>();
        data.put("type", "LEAVE_UPDATE");
        data.put("status", attLog.getStatus());
        data.put("remainingLeave", employee.getAnnualLeave());
        data.put("remainingSickLeave", employee.getSickLeave());
        messagingTemplate.convertAndSend("/topic/attendance/" + employee.getId(), (Object) data);
    }

    private void sendWebSocketUpdate(Long id, String type, String status, String time, int wage, long mins) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("status", status);
        data.put("time", time);
        if ("CHECK_OUT".equals(type)) {
            data.put("dailyWage", wage);
            data.put("workingMinutes", mins);
        }
        messagingTemplate.convertAndSend("/topic/attendance/" + id, (Object) data);
    }
}