package com.example.chat_service.service;

import com.example.chat_service.dto.AttendanceLogResponse;
import com.example.chat_service.entity.AttendanceLog;
import com.example.chat_service.entity.Employee;
import com.example.chat_service.repository.AttendanceLogRepository;
import com.example.chat_service.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAdminService {

    private final AttendanceLogRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Map<String, Object> updateAttendanceStatus(Long employeeId, LocalDate date, String status) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("사원을 찾을 수 없습니다."));

        AttendanceLog logData = attendanceRepository.findByEmployeeAndWorkDate(employee, date)
                .orElse(AttendanceLog.builder().employee(employee).workDate(date).build());

        // 1. 기존 상태 복구
        if (logData.getStatus() != null) {
            String old = logData.getStatus();
            if (old.equals(status)) return Map.of("message", "이미 해당 상태입니다.", "employeeId", employeeId);
            
            if ("연차".equals(old)) employeeRepository.incrementAnnualLeave(employeeId, 1.0);
            else if ("반차".equals(old)) employeeRepository.incrementAnnualLeave(employeeId, 0.5);
            else if ("병가".equals(old)) employeeRepository.incrementSickLeave(employeeId);
        }

        // 하루 풀타임 기준 시간 (8시간 = 480분)
        int fullDayMins = 480;
        int halfDayMins = 240;

        // 2. 새 상태 차감 및 일당(Wage) 계산
        switch (status) {
            case "연차":
                if (employeeRepository.decrementAnnualLeave(employeeId, 1.0) > 0) {
                    // 연차는 유급이므로 8시간치 일당 부여
                    setLogData(logData, "연차", fullDayMins, calculateWage(fullDayMins, employee.getHourlyRate()));
                } else throw new RuntimeException("연차 부족");
                break;
                
            case "반차":
                if (employeeRepository.decrementAnnualLeave(employeeId, 0.5) > 0) {
                    // 반차는 4시간치 일당 부여
                    setLogData(logData, "반차", halfDayMins, calculateWage(halfDayMins, employee.getHourlyRate()));
                } else throw new RuntimeException("연차 부족");
                break;

            case "병가":
                if (employeeRepository.decrementSickLeave(employeeId) > 0) {
                    // 유급 병가: 8시간치 일당 부여
                    setLogData(logData, "병가", fullDayMins, calculateWage(fullDayMins, employee.getHourlyRate()));
                } else {
                    // 병가 소진 시 무급 처리
                    setLogData(logData, "병가(무급)", 0, 0);
                }
                break;

            case "정상근무":
                setLogData(logData, "정상근무", fullDayMins, calculateWage(fullDayMins, employee.getHourlyRate()));
                break;

            case "결근":
            case "조퇴":
                setLogData(logData, status, 0, 0);
                break;

            default:
                logData.setStatus(status);
                break;
        }

        attendanceRepository.saveAndFlush(logData);
        
        // 3. 최신 데이터 로드 및 월급 재계산
        Employee updatedEmployee = employeeRepository.findById(employeeId).get();
        long newSalary = calculateMonthlySalary(employeeId, date.getYear(), date.getMonthValue());
        
        // 4. 웹소켓 전송
        sendWebSocketUpdate(employeeId, date, logData, updatedEmployee, newSalary);

        return Map.of("message", "업데이트 완료", "employeeId", employeeId, "status", logData.getStatus());
    }

    @Transactional
    public Map<String, Object> cancelAttendance(Long employeeId, LocalDate date) {
        AttendanceLog logData = attendanceRepository.findByEmployeeIdAndWorkDate(employeeId, date)
                .orElseThrow(() -> new RuntimeException("기록 없음"));

        String status = logData.getStatus();
        if ("연차".equals(status)) employeeRepository.incrementAnnualLeave(employeeId, 1.0);
        else if ("반차".equals(status)) employeeRepository.incrementAnnualLeave(employeeId, 0.5);
        else if ("병가".equals(status)) employeeRepository.incrementSickLeave(employeeId);

        attendanceRepository.delete(logData);
        
        Employee updated = employeeRepository.findById(employeeId).get();
        long newSalary = calculateMonthlySalary(employeeId, date.getYear(), date.getMonthValue());
        
        sendWebSocketUpdate(employeeId, date, logData, updated, newSalary);

        return Map.of("message", "취소 완료", "employeeId", employeeId);
    }

    private void setLogData(AttendanceLog log, String status, int mins, int wage) {
        log.setStatus(status);
        log.setWorkingMinutes(mins);
        log.setDailyWage(wage);
    }

    private int calculateWage(long mins, int hourlyRate) {
        return (int) Math.floor(mins * (hourlyRate / 60.0));
    }

    private void sendWebSocketUpdate(Long employeeId, LocalDate date, AttendanceLog log, Employee employee, long salary) {
        LocalDate start = LocalDate.of(date.getYear(), date.getMonthValue(), 1);
        List<AttendanceLogResponse> monthlyLogs = attendanceRepository.findByEmployeeIdAndWorkDateBetween(employeeId, start, start.withDayOfMonth(start.lengthOfMonth()))
                .stream().map(AttendanceLogResponse::new).collect(Collectors.toList());

        Map<String, Object> p1 = new HashMap<>();
        p1.put("type", "ADMIN_UPDATE");
        p1.put("employeeId", employeeId);
        p1.put("date", date.toString());
        p1.put("status", log.getStatus());
        p1.put("remainingLeave", employee.getAnnualLeave());
        p1.put("remainingSickLeave", employee.getSickLeave());
        p1.put("monthlyLogs", monthlyLogs);
        
        this.messagingTemplate.convertAndSend("/topic/attendance/" + employeeId, (Object) p1);
        this.messagingTemplate.convertAndSend("/topic/attendance/admin", (Object) p1);
        
        Map<String, Object> p2 = new HashMap<>();
        p2.put("type", "SALARY_UPDATE");
        p2.put("employeeId", employeeId);
        p2.put("newTotalSalary", salary);
        this.messagingTemplate.convertAndSend("/topic/attendance/admin", (Object) p2);
    }

    public long calculateMonthlySalary(Long employeeId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        return attendanceRepository.findByEmployeeIdAndWorkDateBetween(employeeId, start, start.withDayOfMonth(start.lengthOfMonth()))
                .stream().mapToLong(l -> l.getDailyWage() != null ? l.getDailyWage().longValue() : 0L).sum();
    }
}