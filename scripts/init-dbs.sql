SELECT 'CREATE DATABASE userdb'         WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='userdb')\gexec
SELECT 'CREATE DATABASE menudb'         WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='menudb')\gexec
SELECT 'CREATE DATABASE orderdb'        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='orderdb')\gexec
SELECT 'CREATE DATABASE paymentdb'      WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='paymentdb')\gexec
SELECT 'CREATE DATABASE notificationdb' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='notificationdb')\gexec
