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
  run_id uuid not null references agent_run(id),
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
  run_id uuid not null references agent_run(id),
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

comment on table workflow_definition is '工作流定义';
comment on table workflow_node_execution is '工作流节点执行记录';
comment on table workflow_edge_decision is '工作流边选择记录';
