-- 코티(Cotea) 문제 메타데이터 테이블 생성문.
-- backend/src/main/java/com/cotea/service/problem/entity/*.java의 JPA 매핑과 정확히 일치한다.
-- user_hint_log_schema.sql과 같은 컨벤션: 마이그레이션 도구(Flyway 등) 없이, 로컬/운영 DB에 사람이
-- 직접 한 번 실행해서 테이블을 만드는 용도다. 이 프로젝트에는 아직 마이그레이션 도구가 없다.

CREATE TABLE IF NOT EXISTS problem (
    problem_id                 INT             NOT NULL,
    platform                   VARCHAR(50)     NOT NULL,
    title                      VARCHAR(255)    NOT NULL,
    level                      VARCHAR(10)     NULL,
    url                        VARCHAR(500)    NULL,
    difficulty_reason          TEXT            NULL,
    recommended_approach       TEXT            NULL,
    expected_time_complexity   VARCHAR(100)    NULL,
    expected_space_complexity  VARCHAR(100)    NULL,
    key_insight                TEXT            NULL,
    metadata_version           VARCHAR(20)     NULL,
    reviewed_by                VARCHAR(100)    NULL,
    PRIMARY KEY (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS problem_classification (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    problem_id  INT             NOT NULL,
    tag         VARCHAR(50)     NOT NULL,
    subcategory VARCHAR(50)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_problem_classification_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS approach_alternative (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    problem_id    INT           NOT NULL,
    approach_name VARCHAR(255)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approach_alternative_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complexity_variable (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    problem_id           INT            NOT NULL,
    variable_name        VARCHAR(50)    NOT NULL,
    variable_description VARCHAR(500)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_complexity_variable_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS key_data_structure (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    problem_id      INT           NOT NULL,
    structure_name  VARCHAR(255)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_key_data_structure_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS solving_checkpoint (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    problem_id       INT      NOT NULL,
    checkpoint_text  TEXT     NOT NULL,
    order_index      INT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_solving_checkpoint_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stuck_point_hint (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    problem_id  INT           NOT NULL,
    point_key   VARCHAR(50)   NOT NULL,
    hint_text   TEXT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stuck_point_hint_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wrong_answer_mistake (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    problem_id      INT           NOT NULL,
    symptom         VARCHAR(20)   NOT NULL,
    likely_cause    TEXT          NOT NULL,
    direction_hint  TEXT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wrong_answer_mistake_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fatal_approach_signal (
    id           BIGINT  NOT NULL AUTO_INCREMENT,
    problem_id   INT     NOT NULL,
    signal_text  TEXT    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_fatal_approach_signal_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS edge_case (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    problem_id  INT     NOT NULL,
    case_text   TEXT    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_edge_case_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS evaluation_criteria (
    id             BIGINT  NOT NULL AUTO_INCREMENT,
    problem_id     INT     NOT NULL,
    criteria_text  TEXT    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_evaluation_criteria_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS optimization_hint (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    problem_id  INT     NOT NULL,
    hint_text   TEXT    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_optimization_hint_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS similar_problem (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    problem_id    INT           NOT NULL,
    problem_name  VARCHAR(255)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_similar_problem_problem
        FOREIGN KEY (problem_id) REFERENCES problem (problem_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 동시 생성 방지용 락 테이블. 같은 problemId를 여러 유저가 동시에 요청해도
-- problem_id를 PK로 하는 INSERT 하나만 성공하므로, 먼저 성공한 요청만 생성을 진행한다.
-- 생성 성공/실패와 무관하게 시도가 끝나면 이 행은 지운다(성공 시 problem 테이블에 실제 데이터가
-- 생겼으므로 더 이상 필요 없고, 실패 시에도 다음 요청이 재시도할 수 있어야 하므로 지운다).
-- started_at은 프로세스가 죽어서 행이 안 지워진 경우(stale lock)를 감지하는 용도.
CREATE TABLE IF NOT EXISTS problem_generation_lock (
    problem_id  INT         NOT NULL,
    started_at  DATETIME    NOT NULL,
    PRIMARY KEY (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
