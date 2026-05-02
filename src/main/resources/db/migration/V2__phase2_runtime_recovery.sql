create table runtime_failure (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
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
  run_id uuid not null references agent_run(id),
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

comment on table runtime_failure is '阶段 2 运行时失败记录';
comment on column runtime_failure.id is '运行时失败ID';
comment on column runtime_failure.task_id is '关联任务ID';
comment on column runtime_failure.run_id is '关联代理运行ID';
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

comment on table user_input_request is '阶段 2 用户介入请求';
comment on column user_input_request.id is '用户介入请求ID';
comment on column user_input_request.task_id is '关联任务ID';
comment on column user_input_request.run_id is '关联代理运行ID';
comment on column user_input_request.step_id is '关联代理步骤ID';
comment on column user_input_request.plan_item_id is '关联计划项ID';
comment on column user_input_request.status is '用户介入请求状态';
comment on column user_input_request.question is '向用户提出的问题';
comment on column user_input_request.context_summary is '请求用户介入的上下文摘要';
comment on column user_input_request.suggested_options is '建议选项列表';
comment on column user_input_request.answer is '用户回答内容';
comment on column user_input_request.created_at is '创建时间';
comment on column user_input_request.answered_at is '回答或取消时间';
