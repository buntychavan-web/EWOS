{{- define "ewos.fullname" -}}
ewos-backend
{{- end -}}

{{- define "ewos.labels" -}}
app: {{ include "ewos.fullname" . }}
{{- end -}}
