package com.example.hr_service.service;

import com.example.hr_service.dto.AttendanceLogResponse;
import com.example.hr_service.entity.AttendanceLog;
import com.example.hr_service.entity.Employee;
import com.example.hr_service.repository.AttendanceLogRepository;
import com.example.hr_service.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAdminService {

    private final AttendanceLogRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 2026년 한국 공휴일 리스트
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>();

    static {
        HOLIDAYS.add(LocalDate.of(2026, 1, 1));   // 신정
        HOLIDAYS.add(LocalDate.of(2026, 2, 16));  // 설날 연휴
        HOLIDAYS.add(LocalDate.of(2026, 2, 17));  // 설날
        HOLIDAYS.add(LocalDate.of(2026, 2, 18));  // 설날 연휴
        HOLIDAYS.add(LocalDate.of(2026, 3, 1));   // 삼일절
        HOLIDAYS.add(LocalDate.of(2026, 3, 2));   // 삼일절 대체공휴일
        HOLIDAYS.add(LocalDate.of(2026, 5, 5));   // 어린이날 / 부처님오신날
        HOLIDAYS.add(LocalDate.of(2026, 6, 6));   // 현충일
        HOLIDAYS.add(LocalDate.of(2026, 8, 15));  // 광복절
        HOLIDAYS.add(LocalDate.of(2026, 8, 17));  // 광복절 대체공휴일
        HOLIDAYS.add(LocalDate.of(2026, 9, 24));  // 추석 연휴
        HOLIDAYS.add(LocalDate.of(2026, 9, 25));  // 추석
        HOLIDAYS.add(LocalDate.of(2026, 9, 26));  // 추석 연휴
        HOLIDAYS.add(LocalDate.of(2026, 10, 3));  // 개천절
        HOLIDAYS.add(LocalDate.of(2026, 10, 5));  // 개천절 대체공휴일
        HOLIDAYS.add(LocalDate.of(2026, 10, 9));  // 한글날
        HOLIDAYS.add(LocalDate.of(2026, 12, 25)); // 성탄절
    }

    /**
     * 주말 및 공휴일 여부 체크
     */
    private boolean isRestDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || HOLIDAYS.contains(date);
    }

    /**
     * 1. 일괄 수정 및 실시간 전송 (주말/공휴일 제외 적용)
     */
    @Transactional
    public Map<String, Object> updateAttendanceStatusBatch(String id, String status, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = startDate;
        
        // [수정] 평일(근무일) 리스트만 추출
        List<LocalDate> dateRange = getDateRange(startDate, endDate).stream()
                .filter(date -> !isRestDay(date))
                .collect(Collectors.toList());
                
        List<Employee> targetEmployees = getTargetEmployees(id);

        for (Employee employee : targetEmployees) {
            for (LocalDate date : dateRange) {
                processSingleUpdate(employee, date, status);
            }
            attendanceRepository.flush(); 
            refreshAndNotify(employee, startDate);
        }

        return Map.of("message", "업데이트 완료", "target", id, "appliedDays", dateRange.size());
    }

    /**
     * 2. 일괄 삭제 및 실시간 전송 (주말/공휴일 제외 적용)
     */
    @Transactional
    public Map<String, Object> deleteAttendanceBatch(String id, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = startDate;
        
        // [수정] 평일(근무일) 리스트만 추출
        List<LocalDate> dateRange = getDateRange(startDate, endDate).stream()
                .filter(date -> !isRestDay(date))
                .collect(Collectors.toList());
                
        List<Employee> targetEmployees = getTargetEmployees(id);

        for (Employee employee : targetEmployees) {
            for (LocalDate date : dateRange) {
                processSingleDelete(employee, date);
            }
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
        attendanceRepository.save(logData);
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
        payload.put("date", date.toString());
        payload.put("remainingLeave", employee.getAnnualLeave());
        payload.put("remainingSickLeave", employee.getSickLeave());
        payload.put("monthlyLogs", monthlyLogs);
        payload.put("newTotalSalary", salary);

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