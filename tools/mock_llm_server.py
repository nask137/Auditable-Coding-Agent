#!/usr/bin/env python3
"""OpenAI-compatible mock LLM server for phase 2 CLI recovery scenarios.

The server is intentionally tiny and stateful. It supports the scenarios in
docs/step2/phase2-cli-test-guide.md:

- exercise model output retry recovery
- exercise runtime rejected tool recovery
- force runtime to ask for user input

Run:
  python tools/mock_llm_server.py --port 9000
"""

from __future__ import annotations

import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import urlparse


MODEL = "mock-model"
UUID_PATTERN = re.compile(
    r"Current plan item:\s*PlanItem\[id=([0-9a-fA-F-]{36})", re.MULTILINE
)


class ScenarioState:
    def __init__(self) -> None:
        self.model_retry_plan_calls = 0
        self.runtime_reject_decision_calls = 0
        self.user_input_plan_calls = 0


STATE = ScenarioState()


def chat_response(content: str) -> dict[str, Any]:
    return {
        "model": MODEL,
        "choices": [
            {
                "message": {"role": "assistant", "content": content},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 32, "completion_tokens": 16, "total_tokens": 48},
    }


def content_json(value: dict[str, Any]) -> str:
    return json.dumps(value, separators=(",", ":"))


def prompt_type(user_prompt: str) -> str:
    if '"summary": "short task summary"' in user_prompt:
        return "understand"
    if "Create 2 to 6 small plan items" in user_prompt:
        return "plan"
    if "Create 1 to 3 small recovery plan items" in user_prompt:
        return "replan"
    if '"actions": [' in user_prompt:
        return "decision"
    if '"shouldValidate": true' in user_prompt:
        return "validation"
    if '"markdown": "# Agent Run Report' in user_prompt:
        return "report"
    return "unknown"


def scenario(user_prompt: str) -> str:
    lowered = user_prompt.lower()
    if "exercise model output retry recovery" in lowered or "model output retry" in lowered:
        return "model_retry"
    if (
        "exercise runtime rejected tool recovery" in lowered
        or "runtime rejected tool" in lowered
        or "unsupported tool intent" in lowered
        or "runtime recovery" in lowered
    ):
        return "runtime_reject"
    if "force runtime to ask for user input" in lowered or "user input" in lowered:
        return "user_input"
    return "default"


def current_plan_item_id(user_prompt: str) -> str:
    match = UUID_PATTERN.search(user_prompt)
    if match:
        return match.group(1)
    return "00000000-0000-0000-0000-000000000001"


def task_understanding(which: str) -> str:
    summaries = {
        "model_retry": "exercise model output retry recovery",
        "runtime_reject": "exercise runtime rejected tool recovery",
        "user_input": "force runtime to ask for user input",
        "default": "mock phase 2 task",
    }
    return content_json(
        {
            "summary": summaries.get(which, summaries["default"]),
            "taskType": "CODE_EDIT",
            "constraints": ["phase 2 mock llm scenario"],
            "initialSearchHints": ["README.md"],
        }
    )


def plan_for(which: str) -> str:
    if which == "runtime_reject":
        return content_json(
            {
                "items": [
                    {
                        "description": "Trigger unsupported tool intent for runtime recovery",
                        "relatedFiles": ["README.md"],
                        "notes": "Used by phase 2 use case 5.",
                    }
                ]
            }
        )
    if which == "user_input":
        return content_json(
            {
                "items": [
                    {
                        "description": "Continue after user recovery guidance",
                        "relatedFiles": ["README.md"],
                        "notes": "Read the safe workspace file after user input.",
                    }
                ]
            }
        )
    return content_json(
        {
            "items": [
                {
                    "description": "Inspect README.md",
                    "relatedFiles": ["README.md"],
                    "notes": "Confirm the workspace file can be read.",
                },
                {
                    "description": "Write mock recovery note",
                    "relatedFiles": ["MOCK_RECOVERY_NOTE.md"],
                    "notes": "Create a small audited artifact for completion.",
                },
            ]
        }
    )


def replan_for() -> str:
    return content_json(
        {
            "items": [
                {
                    "description": "Read README.md after runtime rejection",
                    "relatedFiles": ["README.md"],
                    "notes": "Replace the rejected model intent with a safe read.",
                }
            ]
        }
    )


def decision_for(user_prompt: str, which: str) -> str:
    plan_item_id = current_plan_item_id(user_prompt)
    description = user_prompt.lower()
    if which == "runtime_reject" and STATE.runtime_reject_decision_calls == 0:
        STATE.runtime_reject_decision_calls += 1
        return content_json(
            {
                "planItemId": plan_item_id,
                "actions": [
                    {
                        "type": "RUN_COMMAND",
                        "reason": "Deliberately unsupported action for use case 5.",
                        "input": {"executable": "java", "args": ["-version"]},
                    }
                ],
            }
        )
    if "write mock recovery note" in description:
        action = {
            "type": "CREATE_FILE",
            "reason": "Create a deterministic note for the mock scenario.",
            "input": {
                "path": "MOCK_RECOVERY_NOTE.md",
                "content": "# Mock Recovery Note\n\nThe mock LLM scenario completed.\n",
            },
        }
    else:
        action = {
            "type": "READ_FILE",
            "reason": "Read the safe workspace README.",
            "input": {"path": "README.md"},
        }
    return content_json({"planItemId": plan_item_id, "actions": [action]})


def validation_decision() -> str:
    return content_json(
        {
            "shouldValidate": False,
            "executableAndArgs": [],
            "reason": "Mock LLM scenario does not need a shell validation command.",
        }
    )


def report() -> str:
    return content_json(
        {
            "markdown": "# Mock LLM Agent Run Report\n\nThe phase 2 mock scenario completed."
        }
    )


def completion_for(user_prompt: str) -> str:
    kind = prompt_type(user_prompt)
    which = scenario(user_prompt)

    if kind == "understand":
        return task_understanding(which)
    if kind == "plan":
        if which == "model_retry":
            STATE.model_retry_plan_calls += 1
            if STATE.model_retry_plan_calls == 1:
                return "{this is not valid json"
        if which == "user_input" and "user answered recovery prompt" not in user_prompt.lower():
            STATE.user_input_plan_calls += 1
            return "{this plan remains invalid until the user answers"
        return plan_for(which)
    if kind == "decision":
        return decision_for(user_prompt, which)
    if kind == "replan":
        return replan_for()
    if kind == "validation":
        return validation_decision()
    if kind == "report":
        return report()
    return task_understanding("default")


class Handler(BaseHTTPRequestHandler):
    server_version = "Phase2MockLLM/1.0"

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/health":
            self._json_response({"status": "ok", "model": MODEL})
            return
        if path == "/reset":
            global STATE
            STATE = ScenarioState()
            self._json_response({"status": "reset"})
            return
        self.send_error(404, "not found")

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/chat/completions":
            self.send_error(404, "not found")
            return
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        try:
            request = json.loads(body.decode("utf-8"))
            messages = request.get("messages", [])
            user_prompt = next(
                (message.get("content", "") for message in messages if message.get("role") == "user"),
                "",
            )
            self._json_response(chat_response(completion_for(user_prompt)))
        except Exception as error:  # pragma: no cover - CLI helper diagnostics
            self._json_response({"error": str(error)}, status=500)

    def _json_response(self, value: dict[str, Any], status: int = 200) -> None:
        response_body = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_body)))
        self.end_headers()
        self.wfile.write(response_body)

    def log_message(self, fmt: str, *args: Any) -> None:
        print("%s - %s" % (self.address_string(), fmt % args))


def main() -> None:
    parser = argparse.ArgumentParser(description="Phase 2 OpenAI-compatible mock LLM")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9000)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Phase 2 mock LLM listening on http://{args.host}:{args.port}")
    print("Endpoint: /chat/completions")
    print("Reset state: GET /reset")
    server.serve_forever()


if __name__ == "__main__":
    main()
