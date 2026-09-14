# Pre-build the PostgreSQL provider and observability features for optimized startup.
FROM quay.io/keycloak/keycloak:26.7.3 AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

# Keep the runtime on the exact same Keycloak patch version and run as non-root.
FROM quay.io/keycloak/keycloak:26.7.3

COPY --from=builder /opt/keycloak/ /opt/keycloak/

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
