# STEP 06 - RAG + PGVector

## PGVector 실행

```bash
cd step06-rag-pgvector
docker compose up -d
cd ..
```

## 애플리케이션 실행

```bash
./gradlew :step06-rag-pgvector:bootRun
```

## 질문

```bash
curl "http://localhost:8106/api/rag?question=Advisor의%20역할은?"
```

실습 편의를 위해 시작할 때 예제 문서를 저장합니다.
운영 환경에서는 중복 적재 방지, 문서 버전, 삭제/갱신 전략이 필요합니다.


## Spring AI 2.0 의존성 주의

`QuestionAnswerAdvisor`의 Java 패키지는 다음과 같습니다.

```java
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
```

하지만 클래스는 PGVector Starter 자체에 포함되지 않습니다. Spring AI 2.0에서는 아래 모듈을
**별도로 추가해야 합니다.**

```gradle
implementation 'org.springframework.ai:spring-ai-vector-store-advisor'
```

즉, `spring-ai-starter-vector-store-pgvector`는 VectorStore 구현을 제공하고,
`spring-ai-vector-store-advisor`는 `QuestionAnswerAdvisor` 같은 Chat/RAG 연결 Advisor를 제공합니다.
