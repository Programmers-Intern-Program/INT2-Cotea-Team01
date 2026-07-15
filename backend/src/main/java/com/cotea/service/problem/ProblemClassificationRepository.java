package com.cotea.service.problem;

import com.cotea.service.problem.entity.ProblemClassificationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemClassificationRepository
        extends JpaRepository<ProblemClassificationEntity, Integer> {

    List<ProblemClassificationEntity> findByTagIn(Collection<String> tags);
}
