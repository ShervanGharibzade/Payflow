# PostgreSQL

One local PostgreSQL container hosts separate logical databases for each service.
Each service must own its schema and data; do not share tables across services.

Development credentials are defined in docker-compose.yml.
