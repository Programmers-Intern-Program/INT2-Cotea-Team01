package com.cotea.service.problem.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 생성 파이프라인({@code com.cotea.service.problem.generation})이 LLM 생성 결과를 저장할 때
 * {@code @Builder}로 인스턴스를 만든다. 자식 리스트는 항상 명시적으로 채워서 저장하지만,
 * 혹시 빠뜨리는 경우를 대비해 {@code @Builder.Default}로 빈 리스트를 기본값으로 둔다
 * (Lombok @Builder는 명시하지 않으면 필드 초기값을 무시하고 null로 만들기 때문).
 * {@code @NoArgsConstructor}가 있으면 {@code @Builder}가 all-args 생성자를 자동 생성하지 않으므로
 * {@code @AllArgsConstructor}를 명시적으로 같이 둔다.
 */
@Getter
@Entity
@Table(name = "problem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemEntity {

    @Id
    @Column(name = "problem_id")
    private Integer problemId;

    private String platform;
    private String title;
    private String level;
    private String url;

    @Column(name = "difficulty_reason")
    private String difficultyReason;

    @Column(name = "recommended_approach")
    private String recommendedApproach;

    @Column(name = "expected_time_complexity")
    private String expectedTimeComplexity;

    @Column(name = "expected_space_complexity")
    private String expectedSpaceComplexity;

    @Column(name = "key_insight")
    private String keyInsight;

    @Column(name = "metadata_version")
    private String metadataVersion;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ProblemClassificationEntity> classifications = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ApproachAlternativeEntity> alternativeApproaches = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<KeyDataStructureEntity> keyDataStructures = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("orderIndex ASC, id ASC")
    @Builder.Default
    private List<SolvingCheckpointEntity> implementationCheckpoints = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<StuckPointHintEntity> stuckPointHints = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<WrongAnswerMistakeEntity> wrongAnswerMistakes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ComplexityVariableEntity> complexityVariables = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<FatalApproachSignalEntity> fatalApproachSignals = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<EdgeCaseEntity> edgeCases = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<EvaluationCriteriaEntity> evaluationCriteria = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<OptimizationHintEntity> optimizationHints = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SimilarProblemEntity> similarProblems = new ArrayList<>();
}
