# ch06_advanced — 교재 7장 · 워크플로 패턴

라우팅 · 병렬 · 평가-교정 · 오케스트레이션 · 캐시.

**혼자 돈다.** 이 폴더만 열어 `./gradlew bootRun` 하면 끝이고, 다른 장 폴더를 참조하지 않는다.

---

## 실행

```bash
export OPENAI_API_KEY="sk-..."   # 소스·깃에 절대 커밋하지 않는다
./gradlew bootRun          # http://localhost:8080
```

- Swagger UI — <http://localhost:8080/swagger-ui.html> ([Try it out] 으로 curl 없이 호출)
- 포트를 바꾸려면 `./gradlew bootRun --args='--server.port=8081'`

> 키가 없어도 **앱은 뜬다**(`${OPENAI_API_KEY:not-set}`). 모델을 실제로 부르는
> 엔드포인트에서만 401 이 난다 — Swagger 를 열어 구조부터 둘러보는 데는 지장이 없다.

---

## 무엇이 들어 있나

| 파일 | 무엇을 보나 |
| --- | --- |
| `WorkflowPatterns.java` | ① 라우팅 ② 병렬 ③ 평가-교정 ④ 오케스트레이터-워커 ⑤ 체이닝 |
| `CachedChatService.java` | 가장 값싼 최적화 — "호출하지 않는 것" |
| `AiExecutorConfig.java` | AI 전용 스레드 풀 — **풀 크기가 곧 동시 호출 상한**이다 |
| `ChatClientConfig.java` | (4장에서 복사) |

**병렬은 지연을 줄이지 비용을 줄이지 않는다.** 호출 수는 그대로다.
전용 풀로 동시 호출을 묶지 않으면 곧 레이트 리밋(429)을 만나고,
그때는 성공한 호출까지 함께 느려진다.

---

## 실행해 보기

```bash
# ① 라우팅 — 값싼 모델이 유형을 정하고 유형별 경로로 보낸다
curl 'localhost:8080/ch06/route?q=반품 규정이 어떻게 되나요'       # SIMPLE_FAQ
curl 'localhost:8080/ch06/route?q=배포 후 응답이 5초로 늘었어요'    # TECHNICAL
curl 'localhost:8080/ch06/route?q=세 번이나 잘못 배송됐습니다'      # COMPLAINT

# ③ 평가-교정 — 평가자에게는 다른 시스템 프롬프트를 준다
curl 'localhost:8080/ch06/write?topic=사내 코드 리뷰 문화&rounds=2'

# ④ 오케스트레이션
curl 'localhost:8080/ch06/orchestrate?goal=신규 입사자 온보딩 문서 목차 만들기'

# ⑤ 캐시 — 같은 질문을 두 번 던져 보면 두 번째가 즉시 온다
curl 'localhost:8080/ch06/cached?q=반품은 며칠 이내인가요'
curl 'localhost:8080/ch06/cached?q=반품은 며칠 이내인가요'    # hits 가 오른다
```

> **확인해 볼 것** — 서버 로그의 `라우팅 결과 ...` 줄. 분류기가 왜 그 경로를
> 골랐는지 이유까지 남는다. 라우팅이 틀리면 그 뒤는 전부 틀린다.

같은 요청이 **`http/ch06_advanced.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- `ChatClientConfig` 를 4장 프로젝트에서 복사해 왔다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
