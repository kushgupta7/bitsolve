-- "Me too": students confirm they are affected by an open incident on a shared campus service
-- instead of filing a duplicate report. One confirmation per student per incident (primary key).
-- Confirmations are a signal for the administrator; they never change the priority by themselves.
alter table incidents add column if not exists confirmations integer not null default 0;

create table if not exists incident_confirmations (
    incident_id bigint not null references incidents (id),
    student_id bigint not null references app_users (id),
    confirmed_at timestamp with time zone not null,
    primary key (incident_id, student_id)
);
create index if not exists incident_confirmations_student on incident_confirmations (student_id);
