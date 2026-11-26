package io.github.barakes.neighbors.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** "Dana" and "dana" are the same name (the same rule as the unique index in V1). */
    @Query("select a from Account a where lower(a.username) = lower(:username)")
    Optional<Account> findByUsernameIgnoringCase(@Param("username") String username);
}
