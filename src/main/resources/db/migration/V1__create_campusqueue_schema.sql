create table app_user (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(100) not null,
    role varchar(20) not null,
    created_at timestamptz not null,
    constraint uk_app_user_email unique (email),
    constraint ck_app_user_role check (role in ('STUDENT', 'ORGANIZER'))
);

create table workshop (
    id uuid primary key,
    organizer_id uuid not null references app_user(id),
    title varchar(120) not null,
    description varchar(2000) not null,
    capacity integer not null,
    status varchar(20) not null,
    next_waitlist_sequence bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_workshop_capacity_positive check (capacity > 0),
    constraint ck_workshop_status check (status in ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    constraint ck_workshop_waitlist_sequence check (next_waitlist_sequence >= 0)
);

create table registration (
    id uuid primary key,
    workshop_id uuid not null references workshop(id),
    student_id uuid not null references app_user(id),
    status varchar(20) not null,
    waitlist_sequence bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_registration_workshop_student unique (workshop_id, student_id),
    constraint ck_registration_status check (status in ('CONFIRMED', 'WAITLISTED', 'CANCELLED')),
    constraint ck_registration_waitlist_sequence check (
        (status = 'WAITLISTED' and waitlist_sequence is not null and waitlist_sequence > 0)
        or (status in ('CONFIRMED', 'CANCELLED') and waitlist_sequence is null)
    )
);

create index idx_registration_workshop_status
    on registration (workshop_id, status);

create index idx_registration_workshop_status_sequence
    on registration (workshop_id, status, waitlist_sequence);
