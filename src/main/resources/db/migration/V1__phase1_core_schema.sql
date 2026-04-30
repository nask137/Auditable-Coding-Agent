create table workspace (
  id uuid primary key,
  name text not null,
  root_path text not null,
  trusted boolean not null,
  allowed_operations jsonb not null,
  blocked_paths jsonb not null,
  sensitive_patterns jsonb not null,
  created_at timestamptz not null,
  last_used_at timestamptz
);

create table task (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  title text not null,
  user_request text not null,
  status text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index idx_task_workspace_status on task(workspace_id, status);

create table agent_run (
  id uuid primary key,
  task_id uuid not null references task(id),
  agent_mode text not null,
  status text not null,
  started_at timestamptz not null,
  finished_at timestamptz,
  failure_reason text,
  runtime_metadata jsonb not null
);

create index idx_agent_run_task_status on agent_run(task_id, status);

create table plan (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  status text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table plan_item (
  id uuid primary key,
  plan_id uuid not null references plan(id),
  description text not null,
  status text not null,
  related_files jsonb not null,
  notes text,
  order_index int not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index idx_plan_item_plan_order on plan_item(plan_id, order_index);

create table agent_step (
  id uuid primary key,
  run_id uuid not null references agent_run(id),
  plan_item_id uuid references plan_item(id),
  step_type text not null,
  status text not null,
  input_summary text,
  output_summary text,
  started_at timestamptz not null,
  finished_at timestamptz
);

create index idx_agent_step_run_started on agent_step(run_id, started_at);

create table agent_action (
  id uuid primary key,
  step_id uuid not null references agent_step(id),
  action_type text not null,
  reason text not null,
  risk_level text not null,
  status text not null,
  created_at timestamptz not null
);

create table tool_call (
  id uuid primary key,
  action_id uuid not null references agent_action(id),
  tool_name text not null,
  permission_level text not null,
  input_summary text not null,
  input_payload jsonb not null,
  status text not null,
  started_at timestamptz not null,
  finished_at timestamptz
);

create table tool_result (
  id uuid primary key,
  tool_call_id uuid not null references tool_call(id),
  success boolean not null,
  output_summary text,
  output_payload jsonb,
  error_message text,
  metadata jsonb not null,
  created_at timestamptz not null
);

create table approval_request (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid references agent_step(id),
  action_id uuid references agent_action(id),
  approval_type text not null,
  reason text not null,
  risk_level text not null,
  affected_files jsonb not null,
  command text,
  working_directory text,
  patch_preview text,
  status text not null,
  created_at timestamptz not null,
  resolved_at timestamptz,
  resolved_by text,
  resolution_reason text
);

create index idx_approval_status_created on approval_request(status, created_at);

create table file_change (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid not null references agent_step(id),
  action_id uuid references agent_action(id),
  path text not null,
  change_type text not null,
  reason text not null,
  diff text,
  before_hash text,
  after_hash text,
  base_revision text,
  observed_at timestamptz,
  patch_apply_status text not null,
  line_added int not null default 0,
  line_deleted int not null default 0,
  risk_level text not null,
  approval_id uuid references approval_request(id),
  created_at timestamptz not null
);

create index idx_file_change_task_created on file_change(task_id, created_at);

create table command_execution (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid not null references agent_step(id),
  action_id uuid references agent_action(id),
  command text not null,
  executable text not null,
  arguments jsonb not null,
  working_directory text not null,
  policy_type text not null,
  risk_level text not null,
  approval_id uuid references approval_request(id),
  status text not null,
  exit_code int,
  output_summary text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null
);

create index idx_command_execution_task_created on command_execution(task_id, created_at);

create table audit_event (
  id uuid primary key,
  task_id uuid references task(id),
  run_id uuid references agent_run(id),
  step_id uuid references agent_step(id),
  action_id uuid references agent_action(id),
  event_type text not null,
  actor text not null,
  level text not null,
  occurred_at timestamptz not null,
  input_summary text,
  output_summary text,
  related_files jsonb not null,
  related_tool_call_id uuid references tool_call(id),
  related_approval_id uuid references approval_request(id),
  related_command_id uuid references command_execution(id),
  related_file_change_id uuid references file_change(id),
  permission_level text,
  risk_level text,
  approval_status text,
  success boolean,
  error_code text,
  error_message text,
  metadata jsonb not null
);

create index idx_audit_event_task_time on audit_event(task_id, occurred_at);
create index idx_audit_event_run_time on audit_event(run_id, occurred_at);

create table validation_result (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid references agent_step(id),
  command_id uuid references command_execution(id),
  validation_type text not null,
  success boolean not null,
  summary text not null,
  created_at timestamptz not null
);

create table task_report (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  content_md text not null,
  created_at timestamptz not null
);

create table command_policy (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  policy_type text not null,
  executable text not null,
  args_pattern jsonb not null,
  cwd_scope text not null,
  allow_pipe boolean not null,
  allow_redirect boolean not null,
  allow_background boolean not null,
  env_policy jsonb not null,
  enabled boolean not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
