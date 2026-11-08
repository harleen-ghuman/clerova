-- Canadian carriers: the workspace's country (billing in CAD) and each load's currency (from its rate con)
alter table organizations add column country varchar(2) not null default 'US';
alter table loads add column currency varchar(3) not null default 'USD';
