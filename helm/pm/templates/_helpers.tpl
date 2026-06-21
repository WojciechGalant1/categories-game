{{/*
Expand the name of the chart.
*/}}
{{- define "pm.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "pm.labels" -}}
helm.sh/chart: {{ include "pm.chart" . }}
{{ include "pm.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "pm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "pm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Chart label
*/}}
{{- define "pm.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Namespace
*/}}
{{- define "pm.namespace" -}}
{{- .Values.namespace.name }}
{{- end }}

{{/*
PostgreSQL JDBC URL (legacy StatefulSet vs CloudNativePG)
*/}}
{{- define "pm.postgres.jdbcUrl" -}}
{{- if eq .Values.postgres.mode "cnpg" -}}
jdbc:postgresql://{{ .Values.postgres.cnpg.clusterName }}-rw.{{ include "pm.namespace" . }}.svc.cluster.local:5432/{{ .Values.postgres.credentials.database }}
{{- else -}}
jdbc:postgresql://postgres-0.postgres.{{ include "pm.namespace" . }}.svc.cluster.local:5432/{{ .Values.postgres.credentials.database }}
{{- end -}}
{{- end }}

{{/*
Secret key for DB username (CNPG uses username; legacy uses POSTGRES_USER)
*/}}
{{- define "pm.postgres.secretUserKey" -}}
{{- if eq .Values.postgres.mode "cnpg" -}}
username
{{- else -}}
POSTGRES_USER
{{- end -}}
{{- end }}

{{/*
Secret key for DB password
*/}}
{{- define "pm.postgres.secretPasswordKey" -}}
{{- if eq .Values.postgres.mode "cnpg" -}}
password
{{- else -}}
POSTGRES_PASSWORD
{{- end -}}
{{- end }}
