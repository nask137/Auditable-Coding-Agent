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
        RUNNING, WAITING_APPROVAL, WAITING_USER_INPUT, COMPLETED, FAILED
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
        TaskExecutionStarted,
        TaskExecutionPaused,
        TaskExecutionResumed,
        TaskExecutionFailed,
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
        ValidationFailed,
        RuntimeRejected,
        RecoveryStarted,
        RecoveryRetried,
        RecoveryReplanned,
        RecoveryUserInputRequested,
        RecoverySkipped,
        RecoveryExhausted,
        UserInputRequested,
        UserInputProvided,
        UserInputCancelled,
        ProjectScanStarted,
        ProjectScanCompleted,
        ProjectScanFailed,
        WorkflowNodeCompleted,
        WorkflowEdgeSelected,
        AgentFinished,
        AgentFailed
    }

    /**
     * Structured runtime failure classes used for recovery decisions.
     */
    public enum RuntimeFailureType {
        MODEL_CALL_FAILED,
        MODEL_OUTPUT_PARSE_FAILED,
        MODEL_OUTPUT_VALIDATION_FAILED,
        MODEL_DECISION_MISMATCH,
        UNSUPPORTED_TOOL_INTENT,
        TOOL_PERMISSION_BLOCKED,
        TOOL_EXECUTION_FAILED,
        PATCH_CONFLICT,
        PATH_ACCESS_BLOCKED,
        COMMAND_POLICY_BLOCKED,
        COMMAND_EXECUTION_FAILED,
        VALIDATION_FAILED,
        APPROVAL_DENIED,
        USER_INPUT_REQUIRED,
        RUNTIME_LIMIT_EXCEEDED,
        UNEXPECTED_RUNTIME_ERROR
    }

    /**
     * Recovery action selected by the runtime after a structured failure.
     */
    public enum RecoveryStrategy {
        RETRY_SAME_ACTION,
        REPLAN_CURRENT_ITEM,
        REPLAN_REMAINING_PLAN,
        ASK_USER,
        REQUEST_APPROVAL,
        SKIP_PLAN_ITEM,
        FAIL_TASK
    }

    /**
     * Lifecycle of a request for additional user guidance.
     */
    public enum UserInputStatus {
        PENDING, ANSWERED, CANCELLED, EXPIRED
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
        DEPENDENCY_INSTALL,
        MEMORY_WRITE
    }

    /**
     * Validation command category.
     */
    public enum ValidationType {
        TEST, BUILD, LINT, TYPE_CHECK, CUSTOM
    }

    /**
     * Lifecycle of a phase 4 project scan.
     */
    public enum ProjectScanStatus {
        RUNNING, COMPLETED, FAILED
    }

    /**
     * Coarse file classification produced by the phase 4 scanner.
     */
    public enum ProjectFileType {
        SOURCE, TEST, DOCS, CONFIG, BUILD_FILE, MIGRATION, OTHER
    }

    /**
     * Type of document chunk stored by the phase 4 document indexer.
     */
    public enum IndexedDocumentType {
        README, DOCS, CONFIG, SOURCE, TEST, MIGRATION, BUILD_FILE, TASK_REPORT, MEMORY
    }

    /**
     * Symbol kinds produced by the phase 4 Java outline extractor.
     */
    public enum CodeSymbolType {
        CLASS, INTERFACE, ENUM, RECORD, METHOD, CONSTRUCTOR, FIELD, FUNCTION, CONSTANT
    }

    /**
     * Long-lived project memory item categories.
     */
    public enum ProjectMemoryType {
        PROJECT_RULE,
        TECH_STACK,
        COMMON_COMMAND,
        TEST_STRATEGY,
        MODULE_SUMMARY,
        ENTRYPOINT,
        USER_PREFERENCE,
        TASK_LESSON,
        FAILURE_PATTERN,
        DO_NOT_TOUCH
    }

    /**
     * Lifecycle of long-lived project memory.
     */
    public enum ProjectMemoryStatus {
        PROPOSED, APPROVED, REJECTED, ARCHIVED, SUPERSEDED
    }

    /**
     * Lifecycle of a proposed long-term memory write.
     */
    public enum MemoryWriteProposalStatus {
        WAITING_APPROVAL, APPROVED, REJECTED
    }

    /**
     * Built-in workflow modes supported by the phase 3 workflow runtime.
     */
    public enum WorkflowMode {
        CODING, REVIEW, TEST, PLANNING, DEBUG
    }

    /**
     * Node types understood by the phase 3 workflow runtime.
     */
    public enum WorkflowNodeType {
        TASK_UNDERSTANDING,
        WORKSPACE_INSPECTION,
        PROJECT_SCAN,
        PROJECT_MEMORY,
        CODE_UNDERSTANDING,
        PLAN_CREATION,
        PLAN_ITEM_EXECUTION,
        VALIDATION,
        TASK_SUMMARY_MEMORY,
        REPORT,
        WAIT_APPROVAL,
        WAIT_USER_INPUT,
        CONDITION,
        FINISH,
        FAIL
    }

    /**
     * Edge types used when selecting the next workflow node.
     */
    public enum WorkflowEdgeType {
        ALWAYS,
        ON_SUCCESS,
        ON_FAILURE,
        ON_BLOCKED,
        ON_WAITING_APPROVAL,
        ON_WAITING_USER_INPUT,
        ON_APPROVAL_GRANTED,
        ON_APPROVAL_DENIED,
        ON_VALIDATION_FAILED,
        ON_MAX_RETRY,
        CONDITION
    }

    /**
     * Lifecycle of one workflow node execution.
     */
    public enum WorkflowNodeStatus {
        RUNNING, SUCCESS, FAILURE, BLOCKED, WAITING_APPROVAL, WAITING_USER_INPUT, FINISHED
    }
}

