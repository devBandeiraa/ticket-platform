package com.devbandeiraa.eventservice.repository;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Acesso aos eventos.
 *
 * <p>Estende {@link JpaSpecificationExecutor} para a listagem publica, cujos filtros sao todos
 * opcionais e se combinam livremente — ver {@link EventSpecifications}.
 */
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndStatus(UUID id, EventStatus status);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);
}
