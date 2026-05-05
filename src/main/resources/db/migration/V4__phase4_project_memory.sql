create table project_scan_run (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid references task(id),
  run_id uuid references agent_run(id),
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
  run_id uuid references agent_run(id),
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
  run_id uuid references agent_run(id),
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

comment on table project_scan_run is '项目扫描记录';
comment on column project_scan_run.id is '项目扫描ID';
comment on column project_scan_run.workspace_id is '关联工作区ID';
comment on column project_scan_run.task_id is '可选关联任务ID';
comment on column project_scan_run.run_id is '可选关联代理运行ID';
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
