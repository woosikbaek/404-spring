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
     * 1. 근태 상태 일괄 수정 (주말 및 공휴일 자동 제외)
     */
    @PostMapping("/update")
    public ResponseEntity<?> update(@RequestBody Map<String, Object> req) {
        try {
            String id = req.get("employeeId").toString();
            String status = req.get("status").toString();
            LocalDate startDate = LocalDate.parse(req.get("date").toString());
            LocalDate endDate = req.get("endDate") != null ? LocalDate.parse(req.get("endDate").toString()) : null;

            return ResponseEntity.ok(adminService.updateAttendanceStatusBatch(id, status, startDate, endDate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 2. 근태 기록 일괄 삭제 (주말 및 공휴일 자동 제외)
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody Map<String, Object> req) {
        try {
            String id = req.get("employeeId").toString();
            LocalDate startDate = LocalDate.parse(req.get("date").toString());
            LocalDate endDate = req.get("endDate") != null ? LocalDate.parse(req.get("endDate").toString()) : null;

            return ResponseEntity.ok(adminService.deleteAttendanceBatch(id, startDate, endDate));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 3. 특정 사원 월간 조회
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

            List<AttendanceLogResponse> result = attendanceRepository.findByEmployeeIdAndWorkDateBetween(employeeId, startDate, endDate)
                    .stream().map(AttendanceLogResponse::new).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 4. 전 사원 월급 요약
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
            Map<Long, Long> salaryMap = allLogs.stream()
                    .filter(log -> log != null && log.getEmployee() != null)
                    .collect(Collectors.groupingBy(
                            log -> log.getEmployee().getId(),
                            Collectors.summingLong(log -> log.getDailyWage() != null ? log.getDailyWage().longValue() : 0L)));

            return ResponseEntity.ok(salaryMap);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 5. 전 사원 월간 기록 전체 조회
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}