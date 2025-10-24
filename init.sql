-- Initial database setup for Search Engine
-- This script runs automatically when MySQL container starts for the first time

-- Set character set and collation
ALTER DATABASE searchengine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant additional privileges if needed
FLUSH PRIVILEGES;
