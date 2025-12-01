package com.ussd.wallet.ultimate.repository;

import com.ussd.wallet.ultimate.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMsisdn(String msisdn);
}
