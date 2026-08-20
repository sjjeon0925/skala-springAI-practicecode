package com.example.step06;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * STEP 06 - 애플리케이션 진입점 (포트 8106)
 *
 * <p><b>실행 전 준비</b> : 벡터 DB가 먼저 떠 있어야 한다.
 * <pre>
 *   cd step06-rag-pgvector
 *   docker compose up -d      # pgvector(PostgreSQL) 기동
 * </pre>
 * DB가 없으면 DataSource 초기화 단계에서 기동에 실패한다.
 */
@SpringBootApplication
public class Step06Application {
    public static void main(String[] args) {
        SpringApplication.run(Step06Application.class, args);
    }
}
