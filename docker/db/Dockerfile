FROM mariadb:11.2

# Set labels for documentation
LABEL maintainer="FotoShare Team"
LABEL description="MariaDB database for FotoShare application"

# Copy initialization scripts
COPY init.sql /docker-entrypoint-initdb.d/

# Set default character encoding
ENV MARIADB_CHARSET=utf8mb4
ENV MARIADB_COLLATION=utf8mb4_unicode_ci

# Expose MariaDB port
EXPOSE 3306

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD mariadb-admin ping -h localhost -u root -p${MARIADB_ROOT_PASSWORD} || exit 1