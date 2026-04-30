package com.nask.agent.common;

public final class Domain {
    private Domain() {
    }

    public enum TaskStatus {
        CREATED, RUNNING, WAITING_APPROVAL, WAITING_USER_INPUT, COMPLETED, FAILED, CANCELLED
    }

    public enum AgentRunStatus {
        RUNNING, WAITING_APPROVAL, WAITING_USER_INPUT, COMPLETED, FAILED, CANCELLED
    }

    public enum PlanStatus {
        ACTIVE, COMPLETED, FAILED
    }

    public enum PlanItemStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    }

    public enum StepStatus {
        RUNNING, COMPLETED, FAILED
    }

    public enum StepType {
        UNDERSTAND_TASK, INSPECT_WORKSPACE, CREATE_PLAN, EXECUTE_PLAN_ITEM, OBSERVE_RESULT, VALIDATE, FINISH, FAIL
    }

    public enum ActionType {
        CALL_MODEL, CALL_TOOL, REQUEST_APPROVAL, UPDATE_PLAN, RUN_VALIDATION, ASK_USER, FINISH, FAIL
    }

    public enum ActionStatus {
        CREATED, RUNNING, COMPLETED, FAILED, BLOCKED, WAITING_APPROVAL
    }

    public enum ToolCallStatus {
        RUNNING, COMPLETED, FAILED, BLOCKED
    }

    public enum AuditActor {
        USER, AGENT, RUNTIME, TOOL, SYSTEM
    }

    public enum AuditLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public enum AuditEventType {
        TaskCreated,
        TaskCancelled,
        AgentRunStarted,
        AgentRunPaused,
        AgentRunResumed,
        AgentRunFailed,
        TaskUnderstood,
        PlanCreated,
        PlanUpdated,
        PlanItemStarted,
        PlanItemCompleted,
        PlanItemFailed,
        StepStarted,
        StepCompleted,
        StepFailed,
        ModelCallStarted,
        ModelCallCompleted,
        ModelCallFailed,
        ToolCallRequested,
        ToolCallStarted,
        ToolCallCompleted,
        ToolCallFailed,
        FileRead,
        FileCreated,
        FileModified,
        FileAccessBlocked,
        CommandRequested,
        CommandAllowed,
        CommandApprovalRequired,
        CommandBlocked,
        CommandExecuted,
        PermissionChecked,
        PermissionAllowed,
        PermissionApprovalRequired,
        PermissionBlocked,
        ApprovalRequested,
        ApprovalGranted,
        ApprovalDenied,
        ValidationStarted,
        ValidationCompleted,
        AgentFinished,
        AgentFailed
    }

    public enum PermissionLevel {
        READ_ONLY, WORKSPACE_WRITE, SHELL_SAFE, SHELL_RISKY, GIT_READ, GIT_WRITE, NETWORK
    }

    public enum PermissionDecisionType {
        ALLOW, REQUIRE_APPROVAL, BLOCK
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum FileOperation {
        FILE_READ, FILE_CREATE, FILE_MODIFY, FILE_DELETE, FILE_MOVE
    }

    public enum ChangeType {
        CREATE, MODIFY, DELETE, MOVE
    }

    public enum PatchApplyStatus {
        APPLIED, FAILED, NOT_APPLIED
    }

    public enum CommandPolicyType {
        ALLOWLIST, APPROVAL_REQUIRED, BLOCKED
    }

    public enum CommandExecutionStatus {
        REQUESTED, WAITING_APPROVAL, RUNNING, COMPLETED, FAILED, BLOCKED
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, CONSUMED, DENIED, EXPIRED, CANCELLED
    }

    public enum ApprovalType {
        FILE_DELETE,
        FILE_MOVE,
        SENSITIVE_FILE_READ,
        SENSITIVE_FILE_MODIFY,
        LARGE_PATCH,
        COMMAND_EXECUTION,
        NETWORK_ACCESS,
        GIT_WRITE,
        DEPENDENCY_INSTALL
    }

    public enum ValidationType {
        TEST, BUILD, LINT, TYPE_CHECK, CUSTOM
    }
}
