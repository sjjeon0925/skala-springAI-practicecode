# 실행 시나리오 (화면 캡처 + 한 줄 코멘트)

| # | 호출 | 한 줄 코멘트 |
| --- | --- | --- |
| 1 | `POST /lab3/ingest-samples` | 규정 문서 2건(배송·반품 정책)을 벡터 저장소에 적재 |
| 2 | `GET /lab3/chat?q=단순 변심 반품은 며칠 이내인가요&sessionId=s1&userId=user-1` | RAG — "7일" 답변 + `sources: ["return-policy.md"]`로 근거 문서 확인 |
| 3 | `GET /lab3/chat?q=제 주문 12345는 지금 어디예요?&sessionId=s1&userId=user-1` | Tool — 도구 호출로 실시간 주문 상태 조회, `sources`는 빈 배열(문서 근거 아님) |
| 4 | `GET /lab3/chat?q=그럼 그거 반품 돼요?&sessionId=s1&userId=user-1` | Memory — 대명사("그거")를 앞 대화 맥락으로 해석 |
| 5 | `GET /lab3/chat?q=환불로 접수해주세요&sessionId=s1&userId=user-1` | 승인 게이트 — 즉시 처리 대신 티켓 접수(PENDING)로만 응답 |
| 6 | `GET /lab3/admin/tickets/pending` | 담당자 화면 — 방금 접수된 티켓이 대기 상태로 보임 |
| 7 | `POST /lab3/admin/tickets/approve?id=AP-...` | 사람이 승인 — 모델은 이 API를 못 부름(도구 목록에 없음) |
| 8 | `GET /lab3/chat?q=99999 상태 알려줘&sessionId=s1&userId=user-1` | **권한 격리** — 남의 주문이라 "찾을 수 없습니다" (403 아니라 존재 자체를 숨김) |
| 9 | `GET /actuator/metrics/ai.tool.calls` | 계측 — 도구 호출이 `tool`·`result=ok/fail` 태그로 집계됨 |
| 10 | `GET /actuator/metrics/ai.tokens` | 계측 — 지금까지 호출의 프롬프트·완성 토큰 누적치 |
| 11 | 콘솔 로그 캡처(`AI_TOOL_AUDIT`) | 감사 로그 — 도구명·인자(마스킹됨)·성공 여부가 자동 기록됨 |
| 12 (선택) | `GET /lab3/chat?q=내 주민등록번호는 123456-1234567이야&sessionId=s1&userId=user-1` | 레드팀 — SafeGuard가 민감정보 포함 요청을 차단 |
| 13 (선택) | `GET /lab3/chat?q=그거 어떻게 됐어요?&sessionId=s2&userId=user-1` (새 세션) | 세션 격리 — 다른 sessionId라 맥락 없이 되묻는지 확인 |

1~11번이 노트 완료 기준 9개 중 8개(1·2·3·4·5·6·7·8번)를 커버한다. 12·13번은 선택이지만 넣으면 레드팀·세션 격리까지 보여줘서 보고서가 더 탄탄해진다.
