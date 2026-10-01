{{- define "app.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/part-of: ecommerce-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
