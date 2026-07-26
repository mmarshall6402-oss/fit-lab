package com.fitlab.backend.service;

import com.fitlab.backend.domain.ScoringConfigEntity;
import com.fitlab.backend.dto.UpdateScoringConfigRequest;
import com.fitlab.backend.matching.ScoringConfig;
import com.fitlab.backend.matching.ScoringConfigProvider;
import com.fitlab.backend.repository.ScoringConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backs the live outfit-scoring config: one row in the database, loaded once
 * at startup and cached in memory so every scoring call (which can happen
 * hundreds of times per recommendation request) reads a plain object instead
 * of hitting the database. Admin updates write through the cache immediately,
 * so a save takes effect on the very next score call.
 */
@Service
public class ScoringConfigService implements ScoringConfigProvider {

    private final ScoringConfigRepository repository;
    private final AtomicReference<ScoringConfig> cache = new AtomicReference<>();

    public ScoringConfigService(ScoringConfigRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    @Transactional
    public void loadOrInitialize() {
        ScoringConfigEntity entity = repository.findById(ScoringConfigEntity.SINGLETON_ID)
                .orElseGet(() -> repository.save(toEntity(ScoringConfig.defaults())));
        cache.set(toConfig(entity));
    }

    @Override
    public ScoringConfig current() {
        return cache.get();
    }

    @Transactional
    public ScoringConfig update(UpdateScoringConfigRequest request) {
        ScoringConfigEntity entity = repository.findById(ScoringConfigEntity.SINGLETON_ID)
                .orElseGet(() -> ScoringConfigEntity.builder().id(ScoringConfigEntity.SINGLETON_ID).build());
        entity.setColorWeight(request.colorWeight());
        entity.setVibeWeight(request.vibeWeight());
        entity.setNeutralColorThreshold(request.neutralColorThreshold());
        entity.setNeutralColors(normalize(request.neutralColors()));
        entity.setSharedVibeReasonEnabled(request.sharedVibeReasonEnabled());
        entity.setSharedColorReasonEnabled(request.sharedColorReasonEnabled());
        entity.setNeutralCounterbalanceReasonEnabled(request.neutralCounterbalanceReasonEnabled());

        ScoringConfig saved = toConfig(repository.save(entity));
        cache.set(saved);
        return saved;
    }

    @Transactional
    public ScoringConfig resetToDefaults() {
        ScoringConfig defaults = ScoringConfig.defaults();
        ScoringConfigEntity entity = toEntity(defaults);
        cache.set(toConfig(repository.save(entity)));
        return cache.get();
    }

    private Set<String> normalize(Set<String> colors) {
        Set<String> normalized = new HashSet<>();
        for (String color : colors) {
            normalized.add(color.trim().toLowerCase());
        }
        return normalized;
    }

    private ScoringConfigEntity toEntity(ScoringConfig config) {
        return ScoringConfigEntity.builder()
                .id(ScoringConfigEntity.SINGLETON_ID)
                .colorWeight(config.colorWeight())
                .vibeWeight(config.vibeWeight())
                .neutralColorThreshold(config.neutralColorThreshold())
                .neutralColors(new HashSet<>(config.neutralColors()))
                .sharedVibeReasonEnabled(config.sharedVibeReasonEnabled())
                .sharedColorReasonEnabled(config.sharedColorReasonEnabled())
                .neutralCounterbalanceReasonEnabled(config.neutralCounterbalanceReasonEnabled())
                .build();
    }

    private ScoringConfig toConfig(ScoringConfigEntity entity) {
        return new ScoringConfig(
                entity.getColorWeight(),
                entity.getVibeWeight(),
                entity.getNeutralColorThreshold(),
                Set.copyOf(entity.getNeutralColors()),
                entity.isSharedVibeReasonEnabled(),
                entity.isSharedColorReasonEnabled(),
                entity.isNeutralCounterbalanceReasonEnabled()
        );
    }
}
