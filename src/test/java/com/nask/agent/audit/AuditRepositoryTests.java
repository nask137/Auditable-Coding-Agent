package com.nask.agent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nask.agent.common.JsonSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditRepositoryTests {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuditRepository repository = new AuditRepository(jdbc, new JsonSupport(new ObjectMapper()));

    @Test
    void listsTaskEventsByAppendSequenceInsteadOfTimestamp() {
        when(jdbc.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findByTask(UUID.randomUUID());

        verify(jdbc).query(contains("order by event_sequence"), any(MapSqlParameterSource.class),
                isA(RowMapper.class));
    }

    @Test
    void listsRunEventsByAppendSequenceInsteadOfTimestamp() {
        when(jdbc.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findByRun(UUID.randomUUID());

        verify(jdbc).query(contains("order by event_sequence"),
                argThat((MapSqlParameterSource params) -> params.hasValue("runId")), isA(RowMapper.class));
    }
}
