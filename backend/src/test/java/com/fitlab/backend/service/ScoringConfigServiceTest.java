package com.fitlab.backend.service;

import com.fitlab.backend.dto.UpdateScoringConfigRequest;
import com.fitlab.backend.matching.ScoringConfig;
import com.fitlab.backend.repository.ScoringConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ScoringConfigServiceTest {

    @Autowired
    private ScoringConfigRepository repository;

    @Test
    void firstLoadPersistsAndCachesDefaults() {
        ScoringConfigService service = new ScoringConfigService(repository);
        service.loadOrInitialize();

        ScoringConfig current = service.current();

        assertThat(current.colorWeight()).isEqualTo(3.0);
        assertThat(current.vibeWeight()).isEqualTo(2.0);
        assertThat(current.neutralColorThreshold()).isEqualTo(3);
        assertThat(current.neutralColors()).contains("black", "white");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void updateWritesThroughToCacheImmediately() {
        ScoringConfigService service = new ScoringConfigService(repository);
        service.loadOrInitialize();

        ScoringConfig updated = service.update(new UpdateScoringConfigRequest(
                5.0, 1.0, 2, Set.of("black", "Beige "), false, true, false, 2.0));

        assertThat(service.current()).isEqualTo(updated);
        assertThat(updated.colorWeight()).isEqualTo(5.0);
        assertThat(updated.vibeWeight()).isEqualTo(1.0);
        assertThat(updated.neutralColorThreshold()).isEqualTo(2);
        assertThat(updated.neutralColors()).containsExactlyInAnyOrder("black", "beige");
        assertThat(updated.sharedVibeReasonEnabled()).isFalse();
        assertThat(updated.neutralCounterbalanceReasonEnabled()).isFalse();
    }

    @Test
    void resetRestoresDefaultsAfterAnUpdate() {
        ScoringConfigService service = new ScoringConfigService(repository);
        service.loadOrInitialize();
        service.update(new UpdateScoringConfigRequest(10.0, 10.0, 0, Set.of(), false, false, false, 5.0));

        ScoringConfig reset = service.resetToDefaults();

        assertThat(reset).isEqualTo(ScoringConfig.defaults());
        assertThat(service.current()).isEqualTo(ScoringConfig.defaults());
    }
}
