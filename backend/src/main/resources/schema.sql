-- 电梯困人救援系统 SQLite 建表脚本（由 BootInit 在启动时按需执行）
-- id 使用 INTEGER PRIMARY KEY AUTOINCREMENT（Hibernate IDENTITY 策略，对应 SQLite rowid）

CREATE TABLE IF NOT EXISTS Elevator (
    id integer primary key autoincrement,
    building varchar(255) not null,
    code varchar(255) not null unique,
    location varchar(255) not null,
    maintenanceVendor varchar(255) not null,
    status varchar(255) not null check (status in ('IN_SERVICE','OUT_OF_SERVICE'))
);

CREATE TABLE IF NOT EXISTS RescueIncident (
    id integer primary key autoincrement,
    alarmCount integer not null,
    callStatus varchar(255) not null check (callStatus in ('CONNECTED','INTERRUPTED','RESTORED')),
    closedAt timestamp,
    escalationLevel integer not null,
    escalationReasons varchar(500),
    medicalAssistanceArranged boolean not null,
    openedAt timestamp not null,
    passengersUnwell boolean not null,
    recheckInspector varchar(100),
    recheckNote varchar(500),
    recheckPassed boolean not null,
    recheckedAt timestamp,
    status varchar(255) not null check (status in ('ALARMED','DISPATCHED','ON_SCENE','RESCUED','CLOSED')),
    trappedCount integer not null,
    elevator_id bigint not null
);

CREATE TABLE IF NOT EXISTS AlarmReport (
    id integer primary key autoincrement,
    callerNote varchar(200),
    receivedAt timestamp not null,
    trappedCount integer not null,
    elevator_id bigint not null,
    incident_id bigint not null,
    mergedInto_id bigint
);

CREATE TABLE IF NOT EXISTS Dispatch (
    id integer primary key autoincrement,
    acknowledgedAt timestamp,
    arrivedAt timestamp,
    assignee varchar(100) not null,
    dispatchedAt timestamp not null,
    etaLimitMinutes integer not null,
    note varchar(200),
    state varchar(255) not null check (state in ('ASSIGNED','ACKNOWLEDGED','ARRIVED')),
    team varchar(255) not null check (team in ('MAINTENANCE','SECURITY')),
    incident_id bigint not null
);

CREATE TABLE IF NOT EXISTS TimelineEvent (
    id integer primary key autoincrement,
    actor varchar(50),
    message varchar(300) not null,
    occurredAt timestamp not null,
    phase varchar(255) not null check (phase in ('ALARM','RESPONSE','RESCUE','ESCALATION','HANDOVER','RECHECK')),
    incident_id bigint not null
);

CREATE TABLE IF NOT EXISTS HandoverCheck (
    id integer primary key autoincrement,
    checkKey varchar(255) not null check (checkKey in ('DOOR_OPEN_CONFIRMED','EQUIPMENT_LOCKED_OUT','ALL_PERSONS_EXITED','SCENE_CLEARED')),
    checked boolean not null,
    checkedAt timestamp,
    checkedBy varchar(100),
    remark varchar(300),
    incident_id bigint not null
);

CREATE INDEX IF NOT EXISTS idx_alarm_incident ON AlarmReport (incident_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_incident ON Dispatch (incident_id);
CREATE INDEX IF NOT EXISTS idx_handover_incident ON HandoverCheck (incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_elevator ON RescueIncident (elevator_id);
CREATE INDEX IF NOT EXISTS idx_incident_status ON RescueIncident (status);
CREATE INDEX IF NOT EXISTS idx_timeline_incident ON TimelineEvent (incident_id);
