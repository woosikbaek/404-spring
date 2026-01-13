package com.example.hr_service.controller;

import com.example.hr_service.entity.AttendanceLog;
import com.example.hr_service.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // 출근 처리 API
    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody Map<String, Object> request) { // String -> Object로 변경
        try {
            // "id" 키로 값을 가져옴 (포스트맨에서 보낸 키값과 일치해야 함)
            Object idObj = request.get("id");

            if (idObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "사원 ID가 누락되었습니다."));
            }

            // String이든 Integer든 Long으로 안전하게 변환
            Long employeeId = Long.valueOf(idObj.toString());

            // 서비스 실행 (Long 타입 전달)
            AttendanceLog result = attendanceService.checkIn(employeeId);

            return ResponseEntity.ok(Map.of(
                "message", "출근 처리가 완료되었습니다.",
                "name", result.getEmployee().getName(),
                "status", result.getStatus(),
                "time", result.getCheckIn().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "서버 오류가 발생했습니다."));
        }
    }

    // 퇴근 처리 API
    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@RequestBody Map<String, Object> request) { // String -> Object로 변경
        try {
            Object idObj = request.get("id");
            
            if (idObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "사원 ID가 누락되었습니다."));
            }

            Long employeeId = Long.valueOf(idObj.toString());
            AttendanceLog result = attendanceService.checkOut(employeeId);

            return ResponseEntity.ok(Map.of(
                "message", "퇴근 처리가 완료되었습니다.",
                "name", result.getEmployee().getName(),
                "time", result.getCheckOut().toString()));
                
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "서버 오류가 발생했습니다."));
        }
    }
}