create sequence if not exists audit_event_sequence_seq;

alter table audit_event add column if not exists event_sequence bigint;

with ordered_events as (
  select id, row_number() over (order by occurred_at, id) as sequence_value
  from audit_event
)
update audit_event
set event_sequence = ordered_events.sequence_value
from ordered_events
where audit_event.id = ordered_events.id;

select setval(
  'audit_event_sequence_seq',
  coalesce((select max(event_sequence) from audit_event), 0) + 1,
  false
);

alter table audit_event
  alter column event_sequence set default nextval('audit_event_sequence_seq');

alter table audit_event
  alter column event_sequence set not null;

create unique index if not exists idx_audit_event_sequence on audit_event(event_sequence);
create index if not exists idx_audit_event_task_sequence on audit_event(task_id, event_sequence);
create index if not exists idx_audit_event_run_sequence on audit_event(run_id, event_sequence);
