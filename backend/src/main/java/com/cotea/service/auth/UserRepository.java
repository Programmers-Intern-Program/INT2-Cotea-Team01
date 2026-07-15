package com.cotea.service.auth;

import com.cotea.service.auth.entity.AuthProvider;
import com.cotea.service.auth.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
