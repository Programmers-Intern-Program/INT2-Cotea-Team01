package com.cotea.service.problem.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProblemHtmlParserTest {

    private final ProblemHtmlParser parser = new ProblemHtmlParser();

    private static final String SAMPLE_HTML = """
            <html><body>
            <div class="lesson-content" data-course-slug="코딩테스트-연습" data-course-id="30" data-lesson-id="42584" data-lesson-type="Challenge" data-lesson-title="테스트 문제" data-lesson-ai-commentable="false" data-challenge-level="2" data-challenge-category="algorithm" data-course-category="open_challenge" data-next-lesson-id="">
              <div class="guide-section-description">
                <h6 class="guide-section-title">문제 설명</h6>
                <div class="markdown">
                  <p>테스트용 문제 설명입니다. 조건을 만족하는 값을 return 하도록 solution 함수를 완성하세요.</p>
                  <h5>제한사항</h5>
                  <ul><li>테스트 제한사항 1</li></ul>
                  <h5>입출력 예</h5>
                  <table class="table"><thead><tr><th>a</th></tr></thead><tbody><tr><td>1</td></tr></tbody></table>
                </div>
              </div>
            </div>
            </body></html>
            """;

    @Test
    void parsesProblemIdTitleLevelAndUrlFromDataAttributes() {
        ParsedProblem result = parser.parse(SAMPLE_HTML);

        assertThat(result.problemId()).isEqualTo(42584);
        assertThat(result.title()).isEqualTo("테스트 문제");
        assertThat(result.level()).isEqualTo("Lv2");
        assertThat(result.url()).isEqualTo("https://school.programmers.co.kr/learn/courses/30/lessons/42584");
    }

    @Test
    void parsesBodyTextFromDescriptionMarkdown() {
        ParsedProblem result = parser.parse(SAMPLE_HTML);

        assertThat(result.bodyText()).contains("조건을 만족하는 값을 return");
        assertThat(result.bodyText()).contains("제한사항");
        assertThat(result.bodyText()).contains("입출력 예");
        assertThat(result.imageUrls()).isEmpty();
    }

    @Test
    void extractsImageUrlsFromDescription() {
        String html = """
                <html><body>
                <div class="lesson-content" data-lesson-id="1829" data-lesson-title="카카오프렌즈 컬러링북" data-challenge-level="2">
                  <div class="guide-section-description">
                    <div class="markdown">
                      <p>설명 텍스트</p>
                      <img src="https://grepp-programmers.s3.ap-northeast-2.amazonaws.com/files/example1.png" alt="">
                      <img src="https://grepp-programmers.s3.ap-northeast-2.amazonaws.com/files/example2.png" alt="">
                    </div>
                  </div>
                </div>
                </body></html>
                """;

        ParsedProblem result = parser.parse(html);

        assertThat(result.imageUrls()).containsExactly(
                "https://grepp-programmers.s3.ap-northeast-2.amazonaws.com/files/example1.png",
                "https://grepp-programmers.s3.ap-northeast-2.amazonaws.com/files/example2.png");
    }

    @Test
    void throwsWhenLessonContentMissing() {
        assertThatThrownBy(() -> parser.parse("<html><body>엉뚱한 페이지</body></html>"))
                .isInstanceOf(ProblemHtmlParseException.class)
                .hasMessageContaining("lesson-content");
    }

    @Test
    void throwsWhenDescriptionMissing() {
        String html = """
                <html><body>
                <div class="lesson-content" data-lesson-id="1" data-lesson-title="t" data-challenge-level="1"></div>
                </body></html>
                """;

        assertThatThrownBy(() -> parser.parse(html))
                .isInstanceOf(ProblemHtmlParseException.class)
                .hasMessageContaining("문제 설명");
    }
}
