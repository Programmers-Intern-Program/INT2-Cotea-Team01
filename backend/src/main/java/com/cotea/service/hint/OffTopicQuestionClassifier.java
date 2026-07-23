package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * FREE_TEXT 라우팅을 규칙으로 1차 판별한다.
 *
 * <p>BUTTON·빈 질문 → {@link Verdict#RELATED}.
 * RELATED 키워드/짧은 알고리즘 용어 → {@link Verdict#RELATED}.
 * 그 외(잡담·소재성 키워드·짧은 애매 질문 포함) → 전부 {@link Verdict#AMBIGUOUS}.
 *
 * <p>연애/날씨/주식 등은 스토리형 문제에도 자주 등장하므로 규칙 단계 하드 OFF_TOPIC은 두지 않는다.
 * OFF_TOPIC 확정은 {@link OffTopicRouteLlmClassifier}가 담당한다.
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

    public Verdict classify(HintRequest request, String question) {
        return classify(request, question, null);
    }

    /**
     * @param problemTitle 규칙 판별에는 쓰지 않음. LLM 라우터 호출 측에서 맥락으로 사용.
     */
    public Verdict classify(HintRequest request, String question, String problemTitle) {
        if (!"FREE_TEXT".equals(request.getQuestionType())) {
            return Verdict.RELATED;
        }
        String q = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return Verdict.RELATED;
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
}
