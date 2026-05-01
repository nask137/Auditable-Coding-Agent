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

comment on column workspace.id is '工作区ID';
comment on column workspace.name is '工作区名称';
comment on column workspace.root_path is '工作区根路径';
comment on column workspace.trusted is '是否受信任';
comment on column workspace.allowed_operations is '允许的操作配置';
comment on column workspace.blocked_paths is '阻止访问的路径配置';
comment on column workspace.sensitive_patterns is '敏感内容匹配规则';
comment on column workspace.created_at is '创建时间';
comment on column workspace.last_used_at is '最后使用时间';

comment on column task.id is '任务ID';
comment on column task.workspace_id is '所属工作区ID';
comment on column task.title is '任务标题';
comment on column task.user_request is '用户原始请求';
comment on column task.status is '任务状态';
comment on column task.created_at is '创建时间';
comment on column task.updated_at is '更新时间';

comment on column agent_run.id is '代理运行ID';
comment on column agent_run.task_id is '所属任务ID';
comment on column agent_run.agent_mode is '代理运行模式';
comment on column agent_run.status is '运行状态';
comment on column agent_run.started_at is '开始时间';
comment on column agent_run.finished_at is '结束时间';
comment on column agent_run.failure_reason is '失败原因';
comment on column agent_run.runtime_metadata is '运行时元数据';

comment on column plan.id is '计划ID';
comment on column plan.task_id is '所属任务ID';
comment on column plan.run_id is '所属代理运行ID';
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
comment on column agent_step.run_id is '所属代理运行ID';
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
comment on column approval_request.run_id is '所属代理运行ID';
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
comment on column file_change.run_id is '所属代理运行ID';
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
comment on column command_execution.run_id is '所属代理运行ID';
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
comment on column audit_event.run_id is '关联代理运行ID';
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
comment on column validation_result.run_id is '所属代理运行ID';
comment on column validation_result.step_id is '关联代理步骤ID';
comment on column validation_result.command_id is '关联命令执行ID';
comment on column validation_result.validation_type is '验证类型';
comment on column validation_result.success is '是否成功';
comment on column validation_result.summary is '验证摘要';
comment on column validation_result.created_at is '创建时间';

comment on column task_report.id is '任务报告ID';
comment on column task_report.task_id is '所属任务ID';
comment on column task_report.run_id is '所属代理运行ID';
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
