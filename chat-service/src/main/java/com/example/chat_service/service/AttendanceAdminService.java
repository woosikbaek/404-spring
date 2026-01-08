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
import java.util.ArrayList;
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

    /**
     * 1. 일괄 수정 및 실시간 전송
     */
    @Transactional
    public Map<String, Object> updateAttendanceStatusBatch(String id, String status, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = startDate;
        List<LocalDate> dateRange = getDateRange(startDate, endDate);
        List<Employee> targetEmployees = getTargetEmployees(id);

        for (Employee employee : targetEmployees) {
            for (LocalDate date : dateRange) {
                processSingleUpdate(employee, date, status);
            }
            // 사원 한 명의 루프가 끝날 때마다 즉시 DB 반영 및 웹소켓 전송
            attendanceRepository.flush(); 
            refreshAndNotify(employee, startDate);
        }

        return Map.of("message", "업데이트 완료", "target", id, "appliedDays", dateRange.size());
    }

    /**
     * 2. 일괄 삭제 및 실시간 전송
     */
    @Transactional
    public Map<String, Object> deleteAttendanceBatch(String id, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = startDate;
        List<LocalDate> dateRange = getDateRange(startDate, endDate);
        List<Employee> targetEmployees = getTargetEmployees(id);

        for (Employee employee : targetEmployees) {
            for (LocalDate date : dateRange) {
                processSingleDelete(employee, date);
            }
            // 사원 한 명의 삭제 루프가 끝날 때마다 즉시 DB 반영 및 웹소켓 전송
            attendanceRepository.flush(); 
            refreshAndNotify(employee, startDate);
        }

        return Map.of("message", "삭제 및 복구 완료", "target", id, "appliedDays", dateRange.size());
    }

    private void processSingleUpdate(Employee employee, LocalDate date, String status) {
        AttendanceLog logData = attendanceRepository.findByEmployeeAndWorkDate(employee, date)
                .orElse(AttendanceLog.builder().employee(employee).workDate(date).build());

        if (logData.getStatus() != null) {
            if (logData.getStatus().equals(status)) return;
            restoreLeaveBalance(employee.getId(), logData.getStatus());
        }

        int fullDayMins = 480;
        switch (status) {
            case "연차":
                if (employeeRepository.decrementAnnualLeave(employee.getId(), 1.0) > 0) {
                    setLogData(logData, "연차", fullDayMins, calculateWage(fullDayMins, employee.getHourlyRate()));
                } else { setLogData(logData, "연차부족", 0, 0); }
                break;
            case "반차":
                if (employeeRepository.decrementAnnualLeave(employee.getId(), 0.5) > 0) {
                    setLogData(logData, "반차", 240, calculateWage(240, employee.getHourlyRate()));
                } else { setLogData(logData, "연차부족", 0, 0); }
                break;
            case "병가":
                if (employeeRepository.decrementSickLeave(employee.getId()) > 0) {
                    setLogData(logData, "병가", fullDayMins, calculateWage(fullDayMins, employee.getHourlyRate()));
                } else { setLogData(logData, "병가(무급)", 0, 0); }
                break;
            case "휴가":
            case "정상근무":
                setLogData(logData, status, fullDayMins, calculateWage(fullDayMins, employee.getHourlyRate()));
                break;
            default:
                setLogData(logData, status, 0, 0);
                break;
        }
        attendanceRepository.save(logData); // saveAndFlush는 루프 밖에서 호출
    }

    private void processSingleDelete(Employee employee, LocalDate date) {
        attendanceRepository.findByEmployeeAndWorkDate(employee, date).ifPresent(logData -> {
            restoreLeaveBalance(employee.getId(), logData.getStatus());
            attendanceRepository.delete(logData);
        });
    }

    private void restoreLeaveBalance(Long employeeId, String status) {
        if ("연차".equals(status)) employeeRepository.incrementAnnualLeave(employeeId, 1.0);
        else if ("반차".equals(status)) employeeRepository.incrementAnnualLeave(employeeId, 0.5);
        else if ("병가".equals(status)) employeeRepository.incrementSickLeave(employeeId);
    }

    private List<LocalDate> getDateRange(LocalDate start, LocalDate end) {
        List<LocalDate> range = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            range.add(current);
            current = current.plusDays(1);
        }
        return range;
    }

    private List<Employee> getTargetEmployees(String id) {
        if ("all".equals(id)) return employeeRepository.findAll();
        Employee emp = employeeRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new RuntimeException("사원 없음"));
        return List.of(emp);
    }

    private void refreshAndNotify(Employee employee, LocalDate date) {
        // 사원 정보 다시 로드 (잔여 연차 등 업데이트 반영)
        Employee updated = employeeRepository.findById(employee.getId()).orElse(employee);
        long newSalary = calculateMonthlySalary(updated.getId(), date.getYear(), date.getMonthValue());
        sendWebSocketUpdate(updated.getId(), date, updated, newSalary);
    }

    private void setLogData(AttendanceLog log, String status, int mins, int wage) {
        log.setStatus(status);
        log.setWorkingMinutes(mins);
        log.setDailyWage(wage);
    }

    private int calculateWage(long mins, int hourlyRate) {
        return (int) Math.floor(mins * (hourlyRate / 60.0));
    }

    private void sendWebSocketUpdate(Long employeeId, LocalDate date, Employee employee, long salary) {
        LocalDate start = LocalDate.of(date.getYear(), date.getMonthValue(), 1);
        List<AttendanceLogResponse> monthlyLogs = attendanceRepository.findByEmployeeIdAndWorkDateBetween(
                employeeId, start, start.withDayOfMonth(start.lengthOfMonth()))
                .stream().map(AttendanceLogResponse::new).collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ADMIN_UPDATE");
        payload.put("employeeId", employeeId);
        payload.put("date", date.toString()); // 변경 기준일
        payload.put("remainingLeave", employee.getAnnualLeave());
        payload.put("remainingSickLeave", employee.getSickLeave());
        payload.put("monthlyLogs", monthlyLogs);
        payload.put("newTotalSalary", salary);

        // 메시지 전송
        this.messagingTemplate.convertAndSend("/topic/attendance/" + employeeId, (Object) payload);
        this.messagingTemplate.convertAndSend("/topic/attendance/admin", (Object) payload);
        
        log.info("WebSocket 전송 완료 - 사원: {}, 급여: {}", employeeId, salary);
    }

    public long calculateMonthlySalary(Long employeeId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        return attendanceRepository.findByEmployeeIdAndWorkDateBetween(employeeId, start, start.withDayOfMonth(start.lengthOfMonth()))
                .stream().mapToLong(l -> l.getDailyWage() != null ? l.getDailyWage().longValue() : 0L).sum();
    }
}