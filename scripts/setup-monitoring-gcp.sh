#!/usr/bin/env bash
# Idempotent setup for private HandoffGuard scheduled monitoring.
# Run in authenticated Google Cloud Shell after the latest Cloud Build succeeds.
set -euo pipefail
PROJECT="handoffguard"
REGION="asia-south1"
PROJECT_NUMBER="614664795142"
SERVICE="handoffguard"
JOB="handoffguard-monitor"
SCHEDULER="handoffguard-monitor-10min"
BUCKET="handoffguard-snapshots-${PROJECT_NUMBER}"
MONITOR_SA="handoffguard-monitor-sa@${PROJECT}.iam.gserviceaccount.com"
TRIGGER_SA="handoffguard-scheduler-sa@${PROJECT}.iam.gserviceaccount.com"

gcloud config set project "${PROJECT}" >/dev/null
gcloud services enable run.googleapis.com cloudscheduler.googleapis.com iam.googleapis.com secretmanager.googleapis.com storage.googleapis.com

IMAGE="$(gcloud run services describe "${SERVICE}" --region="${REGION}" --project="${PROJECT}" --format='value(spec.template.spec.containers[0].image)')"
test -n "${IMAGE}" || { echo "Cannot determine running HandoffGuard image"; exit 1; }
echo "Reusing Cloud Run image: ${IMAGE}"
echo "Confirm this image contains MonitoringJob before continuing (latest Cloud Build must have succeeded)."

if ! gcloud storage buckets describe "gs://${BUCKET}" --project="${PROJECT}" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${BUCKET}" --project="${PROJECT}" --location="${REGION}" --uniform-bucket-level-access
fi
# Retain historical evidence for 30 days by default; bucket is not public.
LIFECYCLE="$(mktemp)"
printf '{"rule":[{"action":{"type":"Delete"},"condition":{"age":30}}]}\n' >"${LIFECYCLE}"
gcloud storage buckets update "gs://${BUCKET}" --lifecycle-file="${LIFECYCLE}"
rm -f "${LIFECYCLE}"

if ! gcloud iam service-accounts describe "${MONITOR_SA}" --project="${PROJECT}" >/dev/null 2>&1; then
  gcloud iam service-accounts create handoffguard-monitor-sa --display-name="HandoffGuard private monitor" --project="${PROJECT}"
fi
if ! gcloud iam service-accounts describe "${TRIGGER_SA}" --project="${PROJECT}" >/dev/null 2>&1; then
  gcloud iam service-accounts create handoffguard-scheduler-sa --display-name="HandoffGuard scheduled job invoker" --project="${PROJECT}"
fi
for SECRET in sf-client-id sf-client-secret; do
  gcloud secrets add-iam-policy-binding "${SECRET}" --project="${PROJECT}" \
    --member="serviceAccount:${MONITOR_SA}" --role="roles/secretmanager.secretAccessor" >/dev/null
done
gcloud storage buckets add-iam-policy-binding "gs://${BUCKET}" \
  --member="serviceAccount:${MONITOR_SA}" --role="roles/storage.objectCreator" >/dev/null

# Read non-secret configuration from deployed Cloud Run service; never print keys.
CONFIG="$(gcloud run services describe "${SERVICE}" --region="${REGION}" --project="${PROJECT}" --format=json)"
SF_DOMAIN="$(printf '%s' "${CONFIG}" | jq -r '[.spec.template.spec.containers[0].env[]? | select(.name=="SF_DOMAIN") | .value][0] // empty')"
HUMANS="$(printf '%s' "${CONFIG}" | jq -r '[.spec.template.spec.containers[0].env[]? | select(.name=="SF_HUMAN_USER_IDS") | .value][0] // empty')"
test -n "${SF_DOMAIN}" && test -n "${HUMANS}" || { echo "Missing SF_DOMAIN or SF_HUMAN_USER_IDS on service"; exit 1; }

gcloud run jobs deploy "${JOB}" --project="${PROJECT}" --region="${REGION}" \
  --image="${IMAGE}" --service-account="${MONITOR_SA}" \
  --tasks=1 --max-retries=0 --task-timeout=600 \
  --command=java --args=-cp,build/classes,dev.handoffguard.MonitoringJob \
  --set-secrets="SF_CLIENT_ID=sf-client-id:latest,SF_CLIENT_SECRET=sf-client-secret:latest" \
  --set-env-vars="SF_LIVE_ENABLED=true,SF_FLOW_MARKERS_ENABLED=true,SF_DOMAIN=${SF_DOMAIN},SF_API_VERSION=v66.0,SF_HUMAN_USER_IDS=${HUMANS},MONITOR_MAX_SESSIONS=50,MONITOR_GCS_BUCKET=${BUCKET}"

gcloud run jobs add-iam-policy-binding "${JOB}" --project="${PROJECT}" --region="${REGION}" \
  --member="serviceAccount:${TRIGGER_SA}" --role="roles/run.invoker" >/dev/null

TARGET="https://run.googleapis.com/v2/projects/${PROJECT}/locations/${REGION}/jobs/${JOB}:run"
if gcloud scheduler jobs describe "${SCHEDULER}" --project="${PROJECT}" --location="${REGION}" >/dev/null 2>&1; then
  gcloud scheduler jobs update http "${SCHEDULER}" --project="${PROJECT}" --location="${REGION}" \
    --schedule='*/10 * * * *' --time-zone='Etc/UTC' --uri="${TARGET}" \
    --http-method=POST --oauth-service-account-email="${TRIGGER_SA}"
else
  gcloud scheduler jobs create http "${SCHEDULER}" --project="${PROJECT}" --location="${REGION}" \
    --schedule='*/10 * * * *' --time-zone='Etc/UTC' --uri="${TARGET}" \
    --http-method=POST --oauth-service-account-email="${TRIGGER_SA}"
fi

echo "Setup complete. Run once manually to validate:"
echo "  gcloud run jobs execute ${JOB} --project=${PROJECT} --region=${REGION} --wait"
echo "Then inspect private snapshots at: gs://${BUCKET}/snapshots/"
echo "Scheduled monitoring runs every 10 minutes, so deadline detection may lag by 10 minutes plus execution time."
