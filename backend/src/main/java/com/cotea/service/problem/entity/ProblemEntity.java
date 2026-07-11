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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "problem")
@NoArgsConstructor
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
    private List<ProblemClassificationEntity> classifications = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    private List<ApproachAlternativeEntity> alternativeApproaches = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    private List<KeyDataStructureEntity> keyDataStructures = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("orderIndex ASC, id ASC")
    private List<SolvingCheckpointEntity> implementationCheckpoints = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    private List<StuckPointHintEntity> stuckPointHints = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "problem_id", referencedColumnName = "problem_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    private List<WrongAnswerMistakeEntity> wrongAnswerMistakes = new ArrayList<>();
}
