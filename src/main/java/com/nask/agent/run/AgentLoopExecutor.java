package com.nask.agent.run;

import java.util.UUID;

/**
 * Strategy interface for executing an agent run.
 */
public interface AgentLoopExecutor {
    /**
     * Executes or resumes the run identified by {@code runId}.
     */
    void execute(UUID runId);
}
