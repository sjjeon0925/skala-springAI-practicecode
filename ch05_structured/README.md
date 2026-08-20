# ch05_structured — 교재 6장 · 구조화 출력 · 멀티모달

`entity()` · 목록/중첩 · 실패 복구 · 이미지 입력.

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
| `StructuredOutputService.java` | 한 줄로 객체 받기 / 목록 / 중첩 / converter 직접 / **실패 복구 3단** |
| `MultimodalService.java` | 이미지 입력 — 멀티모달 + 구조화 출력의 결합 |
| `StructuredController.java` | `/ch05/**` |
| `ChatClientConfig.java` | (4장에서 복사) 추출용 빈을 쓴다 |

**핵심** — 문자열을 파싱하지 말고 객체로 받는다. 그래야 컴파일러가 지켜 준다.
구조화 출력은 "거의" 성공한다. 그 '거의'가 운영에서는 장애 알람이므로
`classifySafely()` 처럼 온도 0 재요청 → 안전한 기본값 순으로 내려앉게 만든다.

---

## 실행해 보기

```bash
curl 'localhost:8080/ch05/classify?q=카드가 두 번 결제됐어요'
#   {"category":"BILLING","priority":"HIGH","summary":"...","tags":[...]}

curl -X POST localhost:8080/ch05/keywords -H 'Content-Type: text/plain' \
     -d '벡터 데이터베이스는 임베딩을 저장하고 유사도로 검색한다. ...'

curl -F 'file=@receipt.png' localhost:8080/ch05/receipt      # 이미지 입력
```

### 테스트

```bash
./gradlew test
```

`StructuredOutputServiceTest` 는 **모의 `ChatModel`** 로 응답 처리 로직만 검증한다.
모델의 답 내용을 검증하지 않는 것이 요령이다 — 느리고, 비싸고, 매번 흔들린다.

같은 요청이 **`http/ch05_structured.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- `ChatClientConfig` 를 4장 프로젝트에서 복사해 왔다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
