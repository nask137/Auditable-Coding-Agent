package com.nask.agent.task;

import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.conversation.ConversationService;
import com.nask.agent.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTests {
    @Test
    void rejectsStartWhenStorageDidNotClaimCreatedTask() {
        var repository = mock(TaskRepository.class);
        var auditService = mock(AuditService.class);
        var service = new TaskService(repository, mock(WorkspaceService.class),
                mock(ConversationService.class), auditService);
        var task = new CodingTask(UUID.randomUUID(), UUID.randomUUID(), "title", "request",
                Domain.TaskStatus.CREATED.name(), Instant.now(), Instant.now());

        when(repository.startExecution(eq(task.id()), eq("CODE_EDIT"), eq("coding-agent"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.startExecution(task, "coding-agent"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.code()).isEqualTo("TASK_ALREADY_EXECUTED");
                });

        verify(auditService, never()).append(any());
    }

    @Test
    void rejectsStartWithoutSelectedWorkflow() {
        var repository = mock(TaskRepository.class);
        var service = new TaskService(repository, mock(WorkspaceService.class),
                mock(ConversationService.class), mock(AuditService.class));
        var task = new CodingTask(UUID.randomUUID(), UUID.randomUUID(), "title", "request",
                Domain.TaskStatus.CREATED.name(), Instant.now(), Instant.now());

        assertThatThrownBy(() -> service.startExecution(task, null))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.code()).isEqualTo("WORKFLOW_REQUIRED");
                });

        verify(repository, never()).startExecution(any(), any(), any(), any());
    }
}
