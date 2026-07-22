package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 전처리(문제 메타) 기반 힌트 경로 vs 범위 밖 FREE_TEXT 경로를 규칙으로 판별한다.
 *
 * <p>BUTTON은 항상 RELATED. FREE_TEXT는 명확한 UNRELATED/RELATED만 규칙으로 확정하고,
 * 키워드에 안 걸리는 짧은 질문은 {@link Verdict#AMBIGUOUS}로 남겨 LLM 라우터에 넘긴다.
 *
 * <p>UNRELATED 키워드가 있어도 문제 제목과 겹치거나 userCode가 있으면
 * 하드 OFF_TOPIC이 아니라 {@link Verdict#AMBIGUOUS}로 올린다
 * (예: 주식가격 문제에서 "주식"이 자연스럽게 등장하는 경우).
 */
@Component
public class OffTopicQuestionClassifier {

    public enum Verdict {
        RELATED,
        OFF_TOPIC,
        AMBIGUOUS
    }

    private static final Pattern RELATED = Pattern.compile(
            ".*(힌트|풀이|접근|구현|코드|왜\\s*틀|원인|오답|시간\\s*초과|런타임|방문|복잡도|"
                    + "알고리즘|자료구조|배열|인덱스|테스트\\s*케이스|"
                    + "이\\s*문제|현재\\s*문제|방향|막혔|틀렸).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // dfs/bfs/dp/큐/스택은 "큐브", "스택형"처럼 다른 단어의 일부로도 흔히 등장해
    // RELATED 정규식의 단순 포함 검사 대신 조사 경계까지 확인하는 별도 목록으로 뺐다.
    private static final List<String> RELATED_SHORT_TERMS = List.of("dfs", "bfs", "dp", "큐", "스택");

    private static final Pattern UNRELATED = Pattern.compile(
            ".*(점심|저녁|날씨|게임\\s*추천|영화|노래|연예|주식|비트코인|"
                    + "사랑|연애|오늘\\s*뭐\\s*해|심심해|농담).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /** 문제 제목 겹침 검사용 — UNRELATED 도메인 단어(잡담 vs 문제 소재). */
    private static final List<String> UNRELATED_DOMAIN_TERMS = List.of(
            "점심", "저녁", "날씨", "영화", "노래", "연예", "주식", "비트코인",
            "사랑", "연애", "심심해", "농담", "게임"
    );

    public Verdict classify(HintRequest request, String question) {
        return classify(request, question, null);
    }

    public Verdict classify(HintRequest request, String question, String problemTitle) {
        if (!"FREE_TEXT".equals(request.getQuestionType())) {
            return Verdict.RELATED;
        }
        String q = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return Verdict.RELATED;
        }
        if (UNRELATED.matcher(q).matches()) {
            if (hasProblemContext(request, q, problemTitle)) {
                return Verdict.AMBIGUOUS;
            }
            return Verdict.OFF_TOPIC;
        }
        if (RELATED.matcher(q).matches()) {
            return Verdict.RELATED;
        }
        boolean matchesShortTerm = RELATED_SHORT_TERMS.stream()
                .anyMatch(term -> KoreanBoundaryMatcher.containsAsStandaloneTerm(q, term));
        if (matchesShortTerm) {
            return Verdict.RELATED;
        }
        return Verdict.AMBIGUOUS;
    }

    /**
     * UNRELATED 키워드가 문제 맥락일 수 있는지.
     * - userCode가 있으면 풀이 중으로 본다.
     * - 질문·문제 제목에 같은 도메인 단어가 있으면 문제 소재로 본다.
     */
    private boolean hasProblemContext(HintRequest request, String questionLower, String problemTitle) {
        if (request.getUserCode() != null && !request.getUserCode().isBlank()) {
            return true;
        }
        if (problemTitle == null || problemTitle.isBlank()) {
            return false;
        }
        String title = problemTitle.toLowerCase(Locale.ROOT);
        for (String term : UNRELATED_DOMAIN_TERMS) {
            if (questionLower.contains(term) && title.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
