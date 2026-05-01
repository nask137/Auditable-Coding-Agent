package com.nask.agent.common;

/**
 * Shared domain vocabulary persisted in the database and emitted in the API.
 *
 * <p>Enums live in one place so schema values, services, and clients can reason
 * about the same state machine names.</p>
 */
public final class Domain {
    private Domain() {
    }

    /**
     * Overall status of a user-created coding task.
     */
    public enum TaskStatus {
        CREATED, RUNNING, WAITING_APPROVAL, WAITING_USER_INPUT, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Status of a concrete agent attempt for a task.
     */
    public enum AgentRunStatus {
        RUNNING, WAITING_APPROVAL, WAITING_USER_INPUT, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Lifecycle of the generated plan for a run.
     */
    public enum PlanStatus {
        ACTIVE, COMPLETED, FAILED
    }

    /**
     * Lifecycle of a single plan item.
     */
    public enum PlanItemStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    }

    /**
     * Lifecycle of an execution step.
     */
    public enum StepStatus {
        RUNNING, WAITING_APPROVAL, COMPLETED, FAILED
    }

    /**
     * Semantic phase represented by a step.
     */
    public enum StepType {
        UNDERSTAND_TASK, INSPECT_WORKSPACE, CREATE_PLAN, EXECUTE_PLAN_ITEM, OBSERVE_RESULT, VALIDATE, FINISH, FAIL
    }

    /**
     * High-level action requested inside a step.
     */
    public enum ActionType {
        CALL_MODEL, CALL_TOOL, REQUEST_APPROVAL, UPDATE_PLAN, RUN_VALIDATION, ASK_USER, FINISH, FAIL
    }

    /**
     * Lifecycle of an action.
     */
    public enum ActionStatus {
        CREATED, RUNNING, COMPLETED, FAILED, BLOCKED, WAITING_APPROVAL
    }

    /**
     * Lifecycle of a tool call.
     */
    public enum ToolCallStatus {
        RUNNING, COMPLETED, FAILED, BLOCKED
    }

    /**
     * Actor responsible for an audit event.
     */
    public enum AuditActor {
        USER, AGENT, RUNTIME, TOOL, SYSTEM
    }

    /**
     * Severity of an audit event.
     */
    public enum AuditLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Stable event names for the audit log.
     */
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

    /**
     * Permission category requested by a tool call.
     */
    public enum PermissionLevel {
        READ_ONLY, WORKSPACE_WRITE, SHELL_SAFE, SHELL_RISKY, GIT_READ, GIT_WRITE, NETWORK
    }

    /**
     * Runtime permission decision returned by policy checks.
     */
    public enum PermissionDecisionType {
        ALLOW, REQUIRE_APPROVAL, BLOCK
    }

    /**
     * Coarse risk level used for permissions, approvals, and audit entries.
     */
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * File operation being classified.
     */
    public enum FileOperation {
        FILE_READ, FILE_CREATE, FILE_MODIFY, FILE_DELETE, FILE_MOVE
    }

    /**
     * Type of file change recorded for reporting and audit.
     */
    public enum ChangeType {
        CREATE, MODIFY, DELETE, MOVE
    }

    /**
     * Whether a patch was applied to disk.
     */
    public enum PatchApplyStatus {
        APPLIED, FAILED, NOT_APPLIED
    }

    /**
     * Policy classification for shell command execution.
     */
    public enum CommandPolicyType {
        ALLOWLIST, APPROVAL_REQUIRED, BLOCKED
    }

    /**
     * Lifecycle of a command execution record.
     */
    public enum CommandExecutionStatus {
        REQUESTED, WAITING_APPROVAL, RUNNING, COMPLETED, FAILED, BLOCKED
    }

    /**
     * Lifecycle of a user approval request.
     */
    public enum ApprovalStatus {
        PENDING, APPROVED, CONSUMED, DENIED, EXPIRED, CANCELLED
    }

    /**
     * Kind of user approval needed to continue a blocked operation.
     */
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

    /**
     * Validation command category.
     */
    public enum ValidationType {
        TEST, BUILD, LINT, TYPE_CHECK, CUSTOM
    }
}
