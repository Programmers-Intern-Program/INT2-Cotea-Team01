package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import org.springframework.stereotype.Component;

/**
 * 사용자가 "이 유형의 개념/접근 자체가 없다"고 볼 수 있는 상황을 룰 기반으로 판정한다.
 *
 * <p>힌트 레벨(Lv1/Lv2 등)은 "얼마나 도움받고 싶은지(의도)"라 개념 유무와 무관하므로 신호로 쓰지 않는다.
 * 대신 사용자가 자유 텍스트로 <b>개념 자체를 물었는지</b>만 본다. 구현 디테일 질문("방문 처리 어떻게")이나
 * 오답/디버깅 질문("왜 틀렸")은 이미 접근이 있는 상태로 보고 제외한다.
 *
 * <p>참고: 사용자가 직접 "개념부터 볼래요"를 누르는 경로(D)는 FE에서 바로 추천을 호출하므로 이 판정과 무관하다.
 */
@Component
public class ConceptGapClassifier {

    /** 알고리즘/자료구조의 정체 자체를 묻는 표현 */
    private static final String[] IDENTITY_SIGNALS = {
            "뭐예요", "뭐에요", "뭔가요", "뭔지", "뭐야", "뭐임", "뭔데",
            "무슨 알고리즘", "무슨 개념", "무슨 자료구조",
            "어떤 알고리즘", "어떤 자료구조"
    };

    /** 개념이 전혀 없다는 전면적 부재 표현 */
    private static final String[] TOTAL_LACK_SIGNALS = {
            "아예 모르", "하나도 모르", "전혀 모르", "1도 모르", "일도 모르",
            "감이 안", "감도 안", "감이 전혀",
            "처음 봐", "처음이라", "처음이에요", "처음 접", "처음이야",
            "이해가 안", "이해가 잘 안", "이해를 못"
    };

    /** 어디서부터 손대야 할지 모르는 접근 부재 표현 */
    private static final String[] FROM_SCRATCH_SIGNALS = {
            "어디서부터", "어디부터", "뭐부터", "무엇부터",
            "뭘 써야", "무엇을 써야", "어떻게 시작", "어떻게 접근"
    };

    /**
     * "개념/알고리즘 자체를 가르쳐/설명해 달라"는 학습 요청 표현.
     * 단독 "알려"/"설명"은 「개선점 알려주세요」「코드 설명」에도 걸려 오탐이 나서,
     * 개념·유형을 가르치는 뉘앙스가 있는 구 위주로 둔다.
     */
    private static final String[] EXPLAIN_SIGNALS = {
            "대해 알려", "대해 설명",
            "개념 알려", "개념 설명", "개념부터",
            "알고리즘 알려", "알고리즘 설명",
            "자료구조 알려", "자료구조 설명",
            "설명해", "설명 좀", "설명해주", "설명 해",
            "가르쳐", "가르치", "배우고 싶", "공부하고 싶"
    };

    /** 이미 접근/코드가 있는 상태(개념 부재 아님) — 오답·디버깅 질문 */
    private static final String DEBUG_OR_REASON_REGEX =
            ".*(틀렸|틀린|오답|시간\\s*초과|런타임|디버그|메모리 초과|왜\\s*안\\s*되|왜\\s*틀).*";

    /** 자기 코드/답안을 가리키는 표현 — 개념 부재가 아니라 구현·리뷰 요청으로 본다 */
    private static final String CODE_REF_REGEX =
            ".*(내|제|이|위|저|본인)\\s*코드.*";

    public boolean isConceptGap(HintRequest request, String question) {
        if (request == null || !"FREE_TEXT".equals(request.getQuestionType())) {
            return false;
        }
        if ("AFTER_SOLVE".equals(request.getStage())) {
            return false;
        }
        if (question == null) {
            return false;
        }
        String q = question.trim();
        if (q.isEmpty() || q.matches(DEBUG_OR_REASON_REGEX) || q.matches(CODE_REF_REGEX)) {
            return false;
        }
        return containsAny(q, IDENTITY_SIGNALS)
                || containsAny(q, TOTAL_LACK_SIGNALS)
                || containsAny(q, FROM_SCRATCH_SIGNALS)
                || containsAny(q, EXPLAIN_SIGNALS)
                || mentionsConceptLack(q);
    }

    private boolean mentionsConceptLack(String q) {
        if (!q.contains("개념")) {
            return false;
        }
        return q.contains("모르") || q.contains("없") || q.contains("부족")
                || q.contains("약해") || q.contains("설명");
    }

    private boolean containsAny(String q, String[] signals) {
        for (String signal : signals) {
            if (q.contains(signal)) {
                return true;
            }
        }
        return false;
    }
}
