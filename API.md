> 인사 관리 및 사내 협업 시스템 서버
> 

| **구분** | **접속 주소 및 설정** | **비고** |
| --- | --- | --- |
| 🚀 | `http://localhost:8080` | REST API 기본 주소 |
| 🗄️ | `jdbc:mysql://127.0.0.1:3306/smart_factory` | 데이터베이스(MySQL) 연결 주소 |
| 💬 | `ws://localhost:8080/ws` | 웹소켓(STOMP) 채팅 서버 주소 |
- **서버 호스트**: `0.0.0.0:8080` (외부 접속 허용)
- **채팅 프로토콜**: STOMP 기반의 안정적인 메시징
- **채팅 경로**:
    - **수신**: `/topic/chat/{roomId}`
    - **송신**: `/app/chat/{roomId}`

### 🏃 근태 및 급여 관리

| **방식** | **엔드포인트** | **인증** | **설명** |
| --- | --- | --- | --- |
| 🏁 | `/api/attendance/check-in` | ❌ | 출근 처리 (09:00 기준 지각 여부 기록) |
| 🛑 | `/api/attendance/check-out` | ❌ | 퇴근 처리 및 일일 근무 시간 계산 |
| 📝 | `/api/admin/attendance/update` | ✅ | 관리자 권한 근태 상태 일괄 수정 |
| 🗑️ | `/api/admin/attendance/delete` | ✅ | 관리자 권한 특정 기간 기록 삭제 |
| 📋 | `/api/admin/attendance/monthly/{id}` | ✅ | 특정 직원의 월간 근태 상세 내역 조회 |
| 💰 | `/api/admin/attendance/salary/all-summary` | ✅ | 전 직원 월별 확정 급여 요약 |
| 📂 | `/api/admin/attendance/monthly/all` | ✅ | 전 직원 월간 근태 기록 전체 조회 |

💬 실시간 협업 채팅 (STOMP)

| **구분** | **목적지** | **설명** |
| --- | --- | --- |
| 📤 | `/app/chat.sendMessage` | 채팅 메시지 전송 및 브로드캐스트 |
| 👋 | `/app/chat.addUser` | 채팅방 입장 알림 및 사용자 등록 |