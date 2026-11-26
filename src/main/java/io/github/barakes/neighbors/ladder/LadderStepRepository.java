package io.github.barakes.neighbors.ladder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LadderStepRepository extends JpaRepository<LadderStep, Long> {

    List<LadderStep> findByAccountIdOrderByStepOrderAsc(Long accountId);
}
