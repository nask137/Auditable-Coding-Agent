/**
 * Command policy, command execution, and shell-tool integration.
 *
 * <p>Commands are never run directly from agent decisions. They are first
 * classified against workspace policy, recorded for audit, and optionally routed
 * through user approval.</p>
 */
package com.nask.agent.command;
