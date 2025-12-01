package com.ussd.wallet.ultimate.repository;

import com.ussd.wallet.ultimate.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUserIdAndCurrency(Long userId, String currency);
}