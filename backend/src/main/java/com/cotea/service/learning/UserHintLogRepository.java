package com.cotea.service.learning;

import com.cotea.service.learning.entity.UserHintLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserHintLogRepository extends JpaRepository<UserHintLogEntity, Long> {

    List<UserHintLogEntity> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, LocalDateTime createdAt);
}
