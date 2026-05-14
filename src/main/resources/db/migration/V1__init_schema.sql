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

create table conversation (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  title text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index idx_conversation_workspace_updated on conversation(workspace_id, updated_at desc);

create table task (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  conversation_id uuid references conversation(id),
  prompt_index integer not null default 1,
  title text not null,
  user_request text not null,
  status text not null,
  agent_mode text,
  execution_started_at timestamptz,
  execution_finished_at timestamptz,
  failure_reason text,
  runtime_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index idx_task_workspace_status on task(workspace_id, status);
create index idx_task_conversation_prompt on task(conversation_id, prompt_index, created_at);

create table plan (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null,
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
  run_id uuid not null,
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
  run_id uuid not null,
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
  run_id uuid not null,
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
  run_id uuid not null,
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
  run_id uuid,
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
  run_id uuid not null,
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
  run_id uuid not null,
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

create table runtime_failure (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null,
  step_id uuid references agent_step(id),
  plan_item_id uuid references plan_item(id),
  failure_type text not null,
  recoverable boolean not null,
  strategy text not null,
  summary text not null,
  details text,
  related_event_id uuid references audit_event(id),
  related_tool_call_id uuid references tool_call(id),
  related_command_id uuid references command_execution(id),
  related_file_change_id uuid references file_change(id),
  attempt int not null,
  created_at timestamptz not null
);

create index idx_runtime_failure_task_created on runtime_failure(task_id, created_at);
create index idx_runtime_failure_run_created on runtime_failure(run_id, created_at);
create index idx_runtime_failure_run_type on runtime_failure(run_id, failure_type);

create table user_input_request (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null,
  step_id uuid references agent_step(id),
  plan_item_id uuid references plan_item(id),
  status text not null,
  question text not null,
  context_summary text not null,
  suggested_options jsonb not null,
  answer text,
  created_at timestamptz not null,
  answered_at timestamptz
);

create index idx_user_input_request_status_created on user_input_request(status, created_at);
create index idx_user_input_request_run_status on user_input_request(run_id, status);

create table workflow_definition (
  id uuid primary key,
  name text not null,
  version int not null,
  description text not null,
  mode text not null,
  enabled boolean not null,
  definition_json jsonb not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint uq_workflow_definition_name_version unique (name, version)
);

create index idx_workflow_definition_enabled on workflow_definition(enabled, name, version);

create table workflow_node_execution (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null,
  workflow_definition_id uuid not null references workflow_definition(id),
  node_id text not null,
  node_type text not null,
  agent_step_id uuid references agent_step(id),
  status text not null,
  input_summary text,
  output_summary text,
  failure_id uuid references runtime_failure(id),
  started_at timestamptz not null,
  completed_at timestamptz,
  metadata_json jsonb not null
);

create index idx_workflow_node_execution_run_started on workflow_node_execution(run_id, started_at, id);
create index idx_workflow_node_execution_task_started on workflow_node_execution(task_id, started_at, id);

create table workflow_edge_decision (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null,
  workflow_definition_id uuid not null references workflow_definition(id),
  from_node_id text not null,
  to_node_id text not null,
  edge_type text not null,
  condition_summary text,
  decision_reason text not null,
  selected boolean not null,
  created_at timestamptz not null,
  metadata_json jsonb not null
);

create index idx_workflow_edge_decision_run_created on workflow_edge_decision(run_id, created_at, id);

create table project_scan_run (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid references task(id),
  run_id uuid,
  status text not null,
  scan_reason text not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  files_seen int not null,
  files_indexed int not null,
  files_skipped int not null,
  summary text not null,
  metadata_json jsonb not null
);

create index idx_project_scan_run_workspace_started on project_scan_run(workspace_id, started_at desc, id);
create index idx_project_scan_run_status_started on project_scan_run(status, started_at desc);

create table project_profile (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  language_summary text not null,
  frameworks_json jsonb not null,
  build_tools_json jsonb not null,
  test_tools_json jsonb not null,
  package_managers_json jsonb not null,
  entrypoints_json jsonb not null,
  important_paths_json jsonb not null,
  docs_paths_json jsonb not null,
  config_paths_json jsonb not null,
  last_scan_run_id uuid references project_scan_run(id),
  confidence numeric(5, 2) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint uq_project_profile_workspace unique (workspace_id)
);

create index idx_project_profile_workspace_updated on project_profile(workspace_id, updated_at desc);

create table indexed_document (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  scan_run_id uuid references project_scan_run(id),
  path text not null,
  document_type text not null,
  title text not null,
  chunk_index int not null,
  content text not null,
  content_hash text not null,
  line_start int not null,
  line_end int not null,
  token_count int not null,
  metadata_json jsonb not null,
  created_at timestamptz not null,
  constraint uq_indexed_document_content unique (workspace_id, document_type, path, chunk_index, content_hash)
);

create index idx_indexed_document_workspace_type_path on indexed_document(workspace_id, document_type, path);
create index idx_indexed_document_workspace_hash on indexed_document(workspace_id, content_hash);

create table code_symbol (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  scan_run_id uuid references project_scan_run(id),
  path text not null,
  language text not null,
  symbol_type text not null,
  symbol_name text not null,
  container_name text,
  signature text not null,
  line_start int not null,
  line_end int not null,
  visibility text,
  metadata_json jsonb not null,
  created_at timestamptz not null
);

create index idx_code_symbol_workspace_name on code_symbol(workspace_id, lower(symbol_name), symbol_type);
create index idx_code_symbol_workspace_path on code_symbol(workspace_id, path, line_start);

create table project_memory_item (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  memory_type text not null,
  scope text not null,
  title text not null,
  content text not null,
  source_type text not null,
  source_id uuid,
  source_path text,
  source_line_start int,
  source_line_end int,
  status text not null,
  confidence numeric(5, 2) not null,
  expires_at timestamptz,
  created_by text not null,
  created_at timestamptz not null,
  approved_by text,
  approved_at timestamptz,
  metadata_json jsonb not null
);

create index idx_project_memory_item_workspace_status_type on project_memory_item(workspace_id, status, memory_type);
create index idx_project_memory_item_workspace_updated on project_memory_item(workspace_id, created_at desc);

create table memory_retrieval (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid references task(id),
  run_id uuid,
  workflow_node_execution_id uuid references workflow_node_execution(id),
  query_text text not null,
  filters_json jsonb not null,
  result_refs_json jsonb not null,
  summary text not null,
  created_at timestamptz not null
);

create index idx_memory_retrieval_workspace_created on memory_retrieval(workspace_id, created_at desc, id);
create index idx_memory_retrieval_task_created on memory_retrieval(task_id, created_at desc, id);

create table memory_write_proposal (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid references task(id),
  run_id uuid,
  proposal_type text not null,
  title text not null,
  content text not null,
  source_refs_json jsonb not null,
  status text not null,
  approval_request_id uuid references approval_request(id),
  project_memory_item_id uuid references project_memory_item(id),
  created_at timestamptz not null,
  resolved_at timestamptz,
  metadata_json jsonb not null
);

create index idx_memory_write_proposal_workspace_status on memory_write_proposal(workspace_id, status, created_at desc);
create index idx_memory_write_proposal_approval on memory_write_proposal(approval_request_id);
create index idx_memory_write_proposal_run_status on memory_write_proposal(run_id, status);

comment on column workspace.id is '工作区ID';
comment on column workspace.name is '工作区名称';
comment on column workspace.root_path is '工作区根路径';
comment on column workspace.trusted is '是否受信任';
comment on column workspace.allowed_operations is '允许的操作配置';
comment on column workspace.blocked_paths is '阻止访问的路径配置';
comment on column workspace.sensitive_patterns is '敏感内容匹配规则';
comment on column workspace.created_at is '创建时间';
comment on column workspace.last_used_at is '最后使用时间';

comment on table conversation is '用户会话，一个 workspace 下可包含多个相关 task prompt';
comment on column conversation.workspace_id is '所属 workspace';
comment on column conversation.title is '会话标题';

comment on column task.id is '任务ID';
comment on column task.workspace_id is '所属工作区ID';
comment on column task.conversation_id is '所属会话ID';
comment on column task.prompt_index is '会话内 prompt 顺序，从 1 开始';
comment on column task.title is '任务标题';
comment on column task.user_request is '用户原始请求';
comment on column task.status is '任务状态';
comment on column task.agent_mode is '单次任务执行模式';
comment on column task.execution_started_at is '任务执行开始时间';
comment on column task.execution_finished_at is '任务执行结束时间';
comment on column task.failure_reason is '任务执行失败原因';
comment on column task.runtime_metadata is '任务执行运行时元数据';
comment on column task.created_at is '创建时间';
comment on column task.updated_at is '更新时间';

comment on column plan.id is '计划ID';
comment on column plan.task_id is '所属任务ID';
comment on column plan.run_id is '所属运行ID';
comment on column plan.status is '计划状态';
comment on column plan.created_at is '创建时间';
comment on column plan.updated_at is '更新时间';

comment on column plan_item.id is '计划项ID';
comment on column plan_item.plan_id is '所属计划ID';
comment on column plan_item.description is '计划项描述';
comment on column plan_item.status is '计划项状态';
comment on column plan_item.related_files is '相关文件列表';
comment on column plan_item.notes is '计划项备注';
comment on column plan_item.order_index is '排序序号';
comment on column plan_item.created_at is '创建时间';
comment on column plan_item.updated_at is '更新时间';

comment on column agent_step.id is '代理步骤ID';
comment on column agent_step.run_id is '所属运行ID';
comment on column agent_step.plan_item_id is '关联计划项ID';
comment on column agent_step.step_type is '步骤类型';
comment on column agent_step.status is '步骤状态';
comment on column agent_step.input_summary is '输入摘要';
comment on column agent_step.output_summary is '输出摘要';
comment on column agent_step.started_at is '开始时间';
comment on column agent_step.finished_at is '结束时间';

comment on column agent_action.id is '代理动作ID';
comment on column agent_action.step_id is '所属代理步骤ID';
comment on column agent_action.action_type is '动作类型';
comment on column agent_action.reason is '动作原因';
comment on column agent_action.risk_level is '风险等级';
comment on column agent_action.status is '动作状态';
comment on column agent_action.created_at is '创建时间';

comment on column tool_call.id is '工具调用ID';
comment on column tool_call.action_id is '所属代理动作ID';
comment on column tool_call.tool_name is '工具名称';
comment on column tool_call.permission_level is '权限级别';
comment on column tool_call.input_summary is '输入摘要';
comment on column tool_call.input_payload is '输入载荷';
comment on column tool_call.status is '调用状态';
comment on column tool_call.started_at is '开始时间';
comment on column tool_call.finished_at is '结束时间';

comment on column tool_result.id is '工具结果ID';
comment on column tool_result.tool_call_id is '所属工具调用ID';
comment on column tool_result.success is '是否成功';
comment on column tool_result.output_summary is '输出摘要';
comment on column tool_result.output_payload is '输出载荷';
comment on column tool_result.error_message is '错误消息';
comment on column tool_result.metadata is '结果元数据';
comment on column tool_result.created_at is '创建时间';

comment on column approval_request.id is '审批请求ID';
comment on column approval_request.task_id is '所属任务ID';
comment on column approval_request.run_id is '所属运行ID';
comment on column approval_request.step_id is '关联代理步骤ID';
comment on column approval_request.action_id is '关联代理动作ID';
comment on column approval_request.approval_type is '审批类型';
comment on column approval_request.reason is '审批原因';
comment on column approval_request.risk_level is '风险等级';
comment on column approval_request.affected_files is '受影响文件列表';
comment on column approval_request.command is '待审批命令';
comment on column approval_request.working_directory is '工作目录';
comment on column approval_request.patch_preview is '补丁预览';
comment on column approval_request.status is '审批状态';
comment on column approval_request.created_at is '创建时间';
comment on column approval_request.resolved_at is '处理时间';
comment on column approval_request.resolved_by is '处理人';
comment on column approval_request.resolution_reason is '处理原因';

comment on column file_change.id is '文件变更ID';
comment on column file_change.workspace_id is '所属工作区ID';
comment on column file_change.task_id is '所属任务ID';
comment on column file_change.run_id is '所属运行ID';
comment on column file_change.step_id is '所属代理步骤ID';
comment on column file_change.action_id is '关联代理动作ID';
comment on column file_change.path is '文件路径';
comment on column file_change.change_type is '变更类型';
comment on column file_change.reason is '变更原因';
comment on column file_change.diff is '差异内容';
comment on column file_change.before_hash is '变更前哈希';
comment on column file_change.after_hash is '变更后哈希';
comment on column file_change.base_revision is '基准版本';
comment on column file_change.observed_at is '观察时间';
comment on column file_change.patch_apply_status is '补丁应用状态';
comment on column file_change.line_added is '新增行数';
comment on column file_change.line_deleted is '删除行数';
comment on column file_change.risk_level is '风险等级';
comment on column file_change.approval_id is '关联审批请求ID';
comment on column file_change.created_at is '创建时间';

comment on column command_execution.id is '命令执行ID';
comment on column command_execution.workspace_id is '所属工作区ID';
comment on column command_execution.task_id is '所属任务ID';
comment on column command_execution.run_id is '所属运行ID';
comment on column command_execution.step_id is '所属代理步骤ID';
comment on column command_execution.action_id is '关联代理动作ID';
comment on column command_execution.command is '执行命令';
comment on column command_execution.executable is '可执行程序';
comment on column command_execution.arguments is '命令参数';
comment on column command_execution.working_directory is '工作目录';
comment on column command_execution.policy_type is '策略类型';
comment on column command_execution.risk_level is '风险等级';
comment on column command_execution.approval_id is '关联审批请求ID';
comment on column command_execution.status is '执行状态';
comment on column command_execution.exit_code is '退出码';
comment on column command_execution.output_summary is '输出摘要';
comment on column command_execution.started_at is '开始时间';
comment on column command_execution.finished_at is '结束时间';
comment on column command_execution.created_at is '创建时间';

comment on column audit_event.id is '审计事件ID';
comment on column audit_event.task_id is '关联任务ID';
comment on column audit_event.run_id is '关联运行ID';
comment on column audit_event.step_id is '关联代理步骤ID';
comment on column audit_event.action_id is '关联代理动作ID';
comment on column audit_event.event_type is '事件类型';
comment on column audit_event.actor is '事件参与者';
comment on column audit_event.level is '事件级别';
comment on column audit_event.occurred_at is '发生时间';
comment on column audit_event.input_summary is '输入摘要';
comment on column audit_event.output_summary is '输出摘要';
comment on column audit_event.related_files is '相关文件列表';
comment on column audit_event.related_tool_call_id is '相关工具调用ID';
comment on column audit_event.related_approval_id is '相关审批请求ID';
comment on column audit_event.related_command_id is '相关命令执行ID';
comment on column audit_event.related_file_change_id is '相关文件变更ID';
comment on column audit_event.permission_level is '权限级别';
comment on column audit_event.risk_level is '风险等级';
comment on column audit_event.approval_status is '审批状态';
comment on column audit_event.success is '是否成功';
comment on column audit_event.error_code is '错误码';
comment on column audit_event.error_message is '错误消息';
comment on column audit_event.metadata is '事件元数据';

comment on column validation_result.id is '验证结果ID';
comment on column validation_result.task_id is '所属任务ID';
comment on column validation_result.run_id is '所属运行ID';
comment on column validation_result.step_id is '关联代理步骤ID';
comment on column validation_result.command_id is '关联命令执行ID';
comment on column validation_result.validation_type is '验证类型';
comment on column validation_result.success is '是否成功';
comment on column validation_result.summary is '验证摘要';
comment on column validation_result.created_at is '创建时间';

comment on column task_report.id is '任务报告ID';
comment on column task_report.task_id is '所属任务ID';
comment on column task_report.run_id is '所属运行ID';
comment on column task_report.content_md is 'Markdown 报告内容';
comment on column task_report.created_at is '创建时间';

comment on column command_policy.id is '命令策略ID';
comment on column command_policy.workspace_id is '所属工作区ID';
comment on column command_policy.policy_type is '策略类型';
comment on column command_policy.executable is '可执行程序';
comment on column command_policy.args_pattern is '参数匹配规则';
comment on column command_policy.cwd_scope is '工作目录范围';
comment on column command_policy.allow_pipe is '是否允许管道';
comment on column command_policy.allow_redirect is '是否允许重定向';
comment on column command_policy.allow_background is '是否允许后台执行';
comment on column command_policy.env_policy is '环境变量策略';
comment on column command_policy.enabled is '是否启用';
comment on column command_policy.created_at is '创建时间';
comment on column command_policy.updated_at is '更新时间';

comment on table runtime_failure is '运行时失败记录';
comment on column runtime_failure.id is '运行时失败ID';
comment on column runtime_failure.task_id is '关联任务ID';
comment on column runtime_failure.run_id is '关联运行ID';
comment on column runtime_failure.step_id is '关联代理步骤ID';
comment on column runtime_failure.plan_item_id is '关联计划项ID';
comment on column runtime_failure.failure_type is '失败类型';
comment on column runtime_failure.recoverable is '是否可恢复';
comment on column runtime_failure.strategy is '选定恢复策略';
comment on column runtime_failure.summary is '失败摘要';
comment on column runtime_failure.details is '失败详情';
comment on column runtime_failure.related_event_id is '关联审计事件ID';
comment on column runtime_failure.related_tool_call_id is '关联工具调用ID';
comment on column runtime_failure.related_command_id is '关联命令执行ID';
comment on column runtime_failure.related_file_change_id is '关联文件变更ID';
comment on column runtime_failure.attempt is '同类失败或恢复策略尝试次数';
comment on column runtime_failure.created_at is '创建时间';

comment on table user_input_request is '用户介入请求';
comment on column user_input_request.id is '用户介入请求ID';
comment on column user_input_request.task_id is '关联任务ID';
comment on column user_input_request.run_id is '关联运行ID';
comment on column user_input_request.step_id is '关联代理步骤ID';
comment on column user_input_request.plan_item_id is '关联计划项ID';
comment on column user_input_request.status is '用户介入请求状态';
comment on column user_input_request.question is '向用户提出的问题';
comment on column user_input_request.context_summary is '请求用户介入的上下文摘要';
comment on column user_input_request.suggested_options is '建议选项列表';
comment on column user_input_request.answer is '用户回答内容';
comment on column user_input_request.created_at is '创建时间';
comment on column user_input_request.answered_at is '回答或取消时间';

comment on table workflow_definition is '工作流定义';
comment on table workflow_node_execution is '工作流节点执行记录';
comment on table workflow_edge_decision is '工作流边选择记录';

comment on table project_scan_run is '项目扫描记录';
comment on column project_scan_run.id is '项目扫描ID';
comment on column project_scan_run.workspace_id is '关联工作区ID';
comment on column project_scan_run.task_id is '可选关联任务ID';
comment on column project_scan_run.run_id is '可选关联运行ID';
comment on column project_scan_run.status is '扫描状态';
comment on column project_scan_run.scan_reason is '扫描原因';
comment on column project_scan_run.started_at is '开始时间';
comment on column project_scan_run.completed_at is '完成时间';
comment on column project_scan_run.files_seen is '看到的文件数量';
comment on column project_scan_run.files_indexed is '纳入索引的文件数量';
comment on column project_scan_run.files_skipped is '跳过的文件数量';
comment on column project_scan_run.summary is '扫描摘要';
comment on column project_scan_run.metadata_json is '扫描元数据';

comment on table project_profile is '项目画像';
comment on column project_profile.id is '项目画像ID';
comment on column project_profile.workspace_id is '关联工作区ID';
comment on column project_profile.language_summary is '语言摘要';
comment on column project_profile.frameworks_json is '框架列表';
comment on column project_profile.build_tools_json is '构建工具列表';
comment on column project_profile.test_tools_json is '测试工具列表';
comment on column project_profile.package_managers_json is '包管理器列表';
comment on column project_profile.entrypoints_json is '入口路径列表';
comment on column project_profile.important_paths_json is '重要路径列表';
comment on column project_profile.docs_paths_json is '文档路径列表';
comment on column project_profile.config_paths_json is '配置路径列表';
comment on column project_profile.last_scan_run_id is '最近扫描ID';
comment on column project_profile.confidence is '项目画像置信度';
comment on column project_profile.created_at is '创建时间';
comment on column project_profile.updated_at is '更新时间';

comment on table indexed_document is '文档和任务摘要索引';
comment on column indexed_document.id is '索引文档ID';
comment on column indexed_document.workspace_id is '关联工作区ID';
comment on column indexed_document.scan_run_id is '可选关联扫描ID';
comment on column indexed_document.path is '来源路径或虚拟路径';
comment on column indexed_document.document_type is '文档类型';
comment on column indexed_document.title is '文档标题';
comment on column indexed_document.chunk_index is '分片序号';
comment on column indexed_document.content is '分片内容';
comment on column indexed_document.content_hash is '内容哈希';
comment on column indexed_document.line_start is '起始行';
comment on column indexed_document.line_end is '结束行';
comment on column indexed_document.token_count is '近似 token 数';
comment on column indexed_document.metadata_json is '索引元数据';
comment on column indexed_document.created_at is '创建时间';

comment on table code_symbol is '代码符号索引';
comment on column code_symbol.id is '代码符号ID';
comment on column code_symbol.workspace_id is '关联工作区ID';
comment on column code_symbol.scan_run_id is '可选关联扫描ID';
comment on column code_symbol.path is '源文件路径';
comment on column code_symbol.language is '代码语言';
comment on column code_symbol.symbol_type is '符号类型';
comment on column code_symbol.symbol_name is '符号名称';
comment on column code_symbol.container_name is '所属容器符号名称';
comment on column code_symbol.signature is '符号签名';
comment on column code_symbol.line_start is '起始行';
comment on column code_symbol.line_end is '结束行';
comment on column code_symbol.visibility is '可见性';
comment on column code_symbol.metadata_json is '符号元数据';
comment on column code_symbol.created_at is '创建时间';

comment on table project_memory_item is '长期项目记忆条目';
comment on column project_memory_item.id is '记忆条目ID';
comment on column project_memory_item.workspace_id is '关联工作区ID';
comment on column project_memory_item.memory_type is '记忆类型';
comment on column project_memory_item.scope is '适用范围';
comment on column project_memory_item.title is '标题';
comment on column project_memory_item.content is '内容';
comment on column project_memory_item.source_type is '来源类型';
comment on column project_memory_item.source_id is '来源记录ID';
comment on column project_memory_item.source_path is '来源路径';
comment on column project_memory_item.source_line_start is '来源起始行';
comment on column project_memory_item.source_line_end is '来源结束行';
comment on column project_memory_item.status is '记忆状态';
comment on column project_memory_item.confidence is '置信度';
comment on column project_memory_item.expires_at is '过期时间';
comment on column project_memory_item.created_by is '创建者';
comment on column project_memory_item.created_at is '创建时间';
comment on column project_memory_item.approved_by is '审批者';
comment on column project_memory_item.approved_at is '审批时间';
comment on column project_memory_item.metadata_json is '记忆元数据';

comment on table memory_retrieval is '项目上下文检索记录';
comment on column memory_retrieval.id is '检索记录ID';
comment on column memory_retrieval.workspace_id is '关联工作区ID';
comment on column memory_retrieval.task_id is '可选关联任务ID';
comment on column memory_retrieval.run_id is '可选关联运行ID';
comment on column memory_retrieval.workflow_node_execution_id is '可选关联工作流节点执行ID';
comment on column memory_retrieval.query_text is '检索查询文本';
comment on column memory_retrieval.filters_json is '检索过滤条件';
comment on column memory_retrieval.result_refs_json is '检索结果来源引用';
comment on column memory_retrieval.summary is '检索摘要';
comment on column memory_retrieval.created_at is '创建时间';

comment on table memory_write_proposal is '待审批记忆写入建议';
comment on column memory_write_proposal.id is '记忆写入建议ID';
comment on column memory_write_proposal.workspace_id is '关联工作区ID';
comment on column memory_write_proposal.task_id is '可选关联任务ID';
comment on column memory_write_proposal.run_id is '可选关联运行ID';
comment on column memory_write_proposal.proposal_type is '建议类型';
comment on column memory_write_proposal.title is '标题';
comment on column memory_write_proposal.content is '内容';
comment on column memory_write_proposal.source_refs_json is '来源引用';
comment on column memory_write_proposal.status is '建议状态';
comment on column memory_write_proposal.approval_request_id is '关联审批请求ID';
comment on column memory_write_proposal.project_memory_item_id is '审批通过后的记忆条目ID';
comment on column memory_write_proposal.created_at is '创建时间';
comment on column memory_write_proposal.resolved_at is '处理时间';
comment on column memory_write_proposal.metadata_json is '建议元数据';
