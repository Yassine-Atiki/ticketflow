-- TicketFlow - PostgreSQL Initialization
-- Development environment only

-- 1. Create dedicated users
CREATE USER customer_user WITH PASSWORD 'customer_dev';
CREATE USER event_user WITH PASSWORD 'event_dev';
CREATE USER booking_user WITH PASSWORD 'booking_dev';
CREATE USER payment_user WITH PASSWORD 'payment_dev';
CREATE USER ticket_user WITH PASSWORD 'ticket_dev';
CREATE USER notification_user WITH PASSWORD 'notification_dev';

-- 2. Create databases with dedicated owners
CREATE DATABASE customer_db OWNER customer_user;
CREATE DATABASE event_db OWNER event_user;
CREATE DATABASE booking_db OWNER booking_user;
CREATE DATABASE payment_db OWNER payment_user;
CREATE DATABASE ticket_db OWNER ticket_user;
CREATE DATABASE notification_db OWNER notification_user;


-- 3. Restrict public access to databases
REVOKE CONNECT ON DATABASE customer_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE event_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE booking_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE payment_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE ticket_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE notification_db FROM PUBLIC;

-- 4. Grant access to dedicated users
GRANT CONNECT ON DATABASE customer_db TO customer_user;
GRANT CONNECT ON DATABASE event_db TO event_user;
GRANT CONNECT ON DATABASE booking_db TO booking_user;
GRANT CONNECT ON DATABASE payment_db TO payment_user;
GRANT CONNECT ON DATABASE ticket_db TO ticket_user;
GRANT CONNECT ON DATABASE notification_db TO notification_user;