package com.nask.agent.run;

import java.util.UUID;

public interface AgentLoopExecutor {
    void execute(UUID runId);
}
