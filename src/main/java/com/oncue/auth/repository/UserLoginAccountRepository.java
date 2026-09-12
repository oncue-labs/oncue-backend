package com.oncue.auth.repository;

import com.oncue.auth.model.User;
import com.oncue.auth.model.UserLoginAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLoginAccountRepository extends JpaRepository<UserLoginAccount, Long> {

    Optional<UserLoginAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
