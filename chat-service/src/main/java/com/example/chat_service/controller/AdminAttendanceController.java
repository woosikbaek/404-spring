package com.example.chat_service.controller;

import com.example.chat_service.dto.AttendanceLogResponse;
import com.example.chat_service.entity.AttendanceLog;
import com.example.chat_service.repository.AttendanceLogRepository;
import com.example.chat_service.service.AttendanceAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AttendanceAdminService adminService;
    private final AttendanceLogRepository attendanceRepository;

    /**
     * 1. 근태 상태 수정 (관리자용)
     */
    @PostMapping("/update")
    public ResponseEntity<?> update(@RequestBody Map<String, Object> req) {
        try {
            Long empId = Long.valueOf(req.get("employeeId").toString());
            LocalDate date = LocalDate.parse(req.get("date").toString());
            String status = req.get("status").toString();
            return ResponseEntity.ok(adminService.updateAttendanceStatus(empId, date, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 2. 특정 사원의 월간 기록 조회 (달력용) - [복구됨]
     */
    @GetMapping("/monthly/{employeeId}")
    public ResponseEntity<?> getMonthlyAttendance(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            LocalDate now = LocalDate.now();
            int targetYear = (year != null) ? year : now.getYear();
            int targetMonth = (month != null) ? month : now.getMonthValue();

            LocalDate startDate = LocalDate.of(targetYear, targetMonth, 1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

            List<AttendanceLog> monthlyLogs = attendanceRepository.findByEmployeeIdAndWorkDateBetween(
                    employeeId, startDate, endDate);

            List<AttendanceLogResponse> result = monthlyLogs.stream()
                    .map(AttendanceLogResponse::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 3. 전 사원 월급 요약 조회 - [복구됨]
     */
    @GetMapping("/salary/all-summary")
    public ResponseEntity<?> getAllEmployeesSalarySummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            LocalDate now = LocalDate.now();
            int targetYear = (year != null) ? year : now.getYear();
            int targetMonth = (month != null) ? month : now.getMonthValue();

            LocalDate start = LocalDate.of(targetYear, targetMonth, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

            List<AttendanceLog> allLogs = attendanceRepository.findByWorkDateBetween(start, end);

            if (allLogs.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "해당 기간에 데이터가 없습니다."));
            }

            Map<Long, Long> salaryMap = allLogs.stream()
                    .filter(log -> log != null && log.getEmployee() != null)
                    .collect(Collectors.groupingBy(
                            log -> log.getEmployee().getId(),
                            Collectors.summingLong(
                                    log -> log.getDailyWage() != null ? log.getDailyWage().longValue() : 0L)));

            return ResponseEntity.ok(salaryMap);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 4. 전 사원 월간 기록 전체 조회 (관리자 리스트용)
     */
    @GetMapping("/monthly/all")
    public ResponseEntity<?> getAll(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
        try {
            LocalDate now = LocalDate.now();
            int targetYear = (year != null) ? year : now.getYear();
            int targetMonth = (month != null) ? month : now.getMonthValue();

            LocalDate start = LocalDate.of(targetYear, targetMonth, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

            List<AttendanceLog> logs = attendanceRepository.findByWorkDateBetween(start, end);
            
            Map<Long, List<AttendanceLogResponse>> grouped = logs.stream()
                    .filter(l -> l.getEmployee() != null)
                    .collect(Collectors.groupingBy(
                            l -> l.getEmployee().getId(), 
                            Collectors.mapping(AttendanceLogResponse::new, Collectors.toList())));

            List<Map<String, Object>> result = grouped.entrySet().stream().map(entry -> {
                Map<String, Object> m = new HashMap<>();
                m.put("employeeId", entry.getKey());
                m.put("logs", entry.getValue());
                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "전체 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 5. 근태 기록 삭제/취소
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody Map<String, Object> req) {
        try {
            Long empId = Long.valueOf(req.get("employeeId").toString());
            LocalDate date = LocalDate.parse(req.get("date").toString());
            return ResponseEntity.ok(adminService.cancelAttendance(empId, date));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}