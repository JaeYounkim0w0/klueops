{{- define "aiops.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "aiops.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "aiops.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
app.kubernetes.io/name: {{ include "aiops.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "aiops.validateAuthenticationMode" -}}
{{- $mode := required "authentication.mode is required" .Values.authentication.mode -}}
{{- if not (has $mode (list "managed-keycloak" "external-oidc")) -}}
{{- fail (printf "unsupported authentication.mode %q; expected managed-keycloak or external-oidc" $mode) -}}
{{- end -}}
{{- end -}}

{{- define "aiops.managedKeycloakEnabled" -}}
{{- include "aiops.validateAuthenticationMode" . -}}
{{- eq .Values.authentication.mode "managed-keycloak" -}}
{{- end -}}

{{- define "aiops.externalOidcEnabled" -}}
{{- include "aiops.validateAuthenticationMode" . -}}
{{- eq .Values.authentication.mode "external-oidc" -}}
{{- end -}}

{{- define "aiops.image" -}}
{{- $image := . -}}
{{- if $image.digest -}}
{{- printf "%s@%s" $image.repository $image.digest -}}
{{- else -}}
{{- printf "%s:%s" $image.repository $image.tag -}}
{{- end -}}
{{- end -}}

{{- define "aiops.portalDatabaseHost" -}}
{{- default .Values.postgresql.host .Values.portal.database.host -}}
{{- end -}}

{{- define "aiops.validatePortal" -}}
{{- if .Values.portal.enabled -}}
{{- $databaseHost := include "aiops.portalDatabaseHost" . -}}
{{- $_ := required "portal database host is required (portal.database.host or postgresql.host)" $databaseHost -}}
{{- $_ := required "portal.database.runtimeExistingSecret is required" .Values.portal.database.runtimeExistingSecret -}}
{{- $_ := required "portal.crypto.masterKeyExistingSecret is required" .Values.portal.crypto.masterKeyExistingSecret -}}
{{- if .Values.portal.commandRunner.enabled -}}
{{- $_ := required "portal.commandRunner.tokenExistingSecret is required when command runner is enabled" .Values.portal.commandRunner.tokenExistingSecret -}}
{{- end -}}
{{- if .Values.global.productionMode -}}
{{- $_ := required "portal.backend.image.digest is required when global.productionMode=true" .Values.portal.backend.image.digest -}}
{{- $_ := required "portal.frontend.image.digest is required when global.productionMode=true" .Values.portal.frontend.image.digest -}}
{{- if .Values.portal.commandRunner.enabled -}}
{{- $_ := required "portal.commandRunner.image.digest is required when command runner is enabled in production" .Values.portal.commandRunner.image.digest -}}
{{- end -}}
{{- if eq .Values.authentication.mode "managed-keycloak" -}}
{{- $_ := required "authentication.managedKeycloak.image.digest is required when global.productionMode=true" .Values.authentication.managedKeycloak.image.digest -}}
{{- end -}}
{{- if ne .Values.portal.frontend.service.type "ClusterIP" -}}
{{- fail "portal.frontend.service.type must be ClusterIP when global.productionMode=true; expose the portal through TLS ingress" -}}
{{- end -}}
{{- if not (hasPrefix "https://" .Values.authentication.publicBaseUrl) -}}
{{- fail "authentication.publicBaseUrl must use https:// when global.productionMode=true" -}}
{{- end -}}
{{- if not .Values.portal.session.cookieSecure -}}
{{- fail "portal.session.cookieSecure must be true when global.productionMode=true" -}}
{{- end -}}
{{- if not .Values.portal.ingress.enabled -}}
{{- fail "portal.ingress.enabled must be true when global.productionMode=true" -}}
{{- end -}}
{{- $_ := required "portal.ingress.host is required in production" .Values.portal.ingress.host -}}
{{- $_ := required "portal.ingress.tlsSecretName is required in production" .Values.portal.ingress.tlsSecretName -}}
{{- if eq .Values.authentication.mode "managed-keycloak" -}}
{{- if not (hasPrefix "https://" .Values.authentication.managedKeycloak.publicUrl) -}}
{{- fail "authentication.managedKeycloak.publicUrl must use https:// when global.productionMode=true" -}}
{{- end -}}
{{- $_ := required "portal.ingress.authenticationHost is required for managed Keycloak in production" .Values.portal.ingress.authenticationHost -}}
{{- $_ := required "portal.ingress.authenticationTlsSecretName is required for managed Keycloak in production" .Values.portal.ingress.authenticationTlsSecretName -}}
{{- range .Values.authentication.managedKeycloak.additionalPortalUrls -}}
{{- if not (hasPrefix "https://" .) -}}
{{- fail "authentication.managedKeycloak.additionalPortalUrls must use https:// when global.productionMode=true" -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}
