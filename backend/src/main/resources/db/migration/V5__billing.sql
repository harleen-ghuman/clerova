-- Stripe subscriptions. The subscription status decides access once
-- billing is enabled; the Stripe objects stay the source of truth.
alter table organizations add column stripe_customer_id varchar(64);
alter table organizations add column stripe_subscription_id varchar(64);
alter table organizations add column subscription_status varchar(30);
alter table organizations add column billing_interval varchar(10);
alter table organizations add column current_period_end timestamp(6) with time zone;
alter table organizations add column cancel_at_period_end boolean not null default false;
alter table organizations add column founding_customer boolean not null default false;

create unique index ux_organizations_stripe_customer on organizations (stripe_customer_id);
