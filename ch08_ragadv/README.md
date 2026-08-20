# ch08_ragadv — 교재 9장 · RAG 심화

HyDE · 재순위 · 모듈형 RAG · 하이브리드 검색.

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
| `AdvancedRagService.java` | ① HyDE ② 재순위 ③ 모듈형 RAG ④ RRF 하이브리드 |
| `RagAdvancedController.java` | `/ch08/**` (+ 문서 적재용 `ingest-samples`) |
| `IngestService.java` | (8장에서 복사) 검색할 것이 있어야 비교가 된다 |
| `VectorStoreConfig.java` · `ChatClientConfig.java` | 인메모리 스토어 · 용도별 빈 |

**어느 것도 공짜가 아니다.** 변환·확장은 질의당 모델 호출을 1~3회 더 쓴다.
회수율이 실제로 오르는지 재고 켠다.

---

## 실행해 보기

```bash
# ① 먼저 문서를 넣는다 (안 하면 전부 빈 결과다)
curl -X POST localhost:8080/ch08/ingest-samples

# ② HyDE — 질문이 아니라 "그럴듯한 가상 답변"으로 검색한다
curl 'localhost:8080/ch08/hyde?q=제주도 배송비가 따로 붙나요'

# ③ 재순위 — 넓게 찾고(20) 좁게 넣는다(4)
curl 'localhost:8080/ch08/rerank?q=반품 배송비는 누가 부담하나요&recall=20&keep=4'

# ④ 모듈형 RAG — 질문 재작성 + 질의 확장 + 검색
curl 'localhost:8080/ch08/modular?q=환불 얼마나 걸려요'
```

> **확인해 볼 것** — 같은 질문을 `/ch07/retrieve` 와 `/ch08/hyde` 로 각각 던져
> 회수된 청크를 비교해 보라. 질문과 문서는 문체가 다르지만, **답변과 문서는
> 문체가 비슷해서** HyDE 가 더 잘 맞는 경우가 있다. 항상은 아니다 — 그래서 재 본다.
>
> `hyde` 는 로그 레벨을 DEBUG 로 올리면 생성된 가상 답변이 보인다.

같은 요청이 **`http/ch08_ragadv.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- 검색 대상이 있어야 심화 기법을 비교할 수 있어 `IngestService` 와 `POST /ch08/ingest-samples` 를 이 프로젝트에 넣었다(원래는 8장 것을 함께 띄워 썼다).

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
