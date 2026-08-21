package com.skala.helpdesk.chat;

import java.util.List;

/** 13장 Phase 6 — 구조화 응답. 화면이 답변·출처·도구 사용 여부를 각각 다르게 쓸 수 있게 나눈다. */
public record AnswerDto(String answer, List<String> sources, boolean toolUsed) {
}
