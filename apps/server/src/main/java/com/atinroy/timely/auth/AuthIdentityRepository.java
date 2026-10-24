package com.atinroy.timely.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, AuthIdentity.Key> {
}
