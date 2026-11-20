#!/usr/bin/env bash
# One-time opt-in deployment: let only the HandoffGuard web service read
# genuine private monitoring snapshots. Requires Cloud Run / Storage IAM admin rights.
set -euo pipefail
PROJECT="handoffguard"
REGION="asia-south1"
SERVICE="handoffguard"
BUCKET="handoffguard-snapshots-614664795142"
PROJECT_NUMBER="614664795142"
# Genuine, independently verified Salesforce monitoring JSON captured Nov 16.
EVIDENCE_OBJECT="snapshots/2026-11-16T07-19-56.691794651Z.json"

gcloud run services describe "$SERVICE" --project="$PROJECT" --region="$REGION" >/dev/null
gcloud storage buckets describe "gs://$BUCKET" --project="$PROJECT" >/dev/null
if ! gcloud storage objects describe "gs://$BUCKET/$EVIDENCE_OBJECT" --project="$PROJECT" >/dev/null 2>&1; then
  echo "Verified historical monitoring snapshot missing. No evidence will be fabricated." >&2
  exit 1
fi
SERVICE_ACCOUNT="$(gcloud run services describe "$SERVICE" --project="$PROJECT" --region="$REGION" --format='value(spec.template.spec.serviceAccountName)')"
if [[ -z "$SERVICE_ACCOUNT" ]]; then SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"; fi
echo "Granting bucket-scoped read-only access to Cloud Run identity: $SERVICE_ACCOUNT"
echo "Bucket remains private; the public HTTP endpoint returns sanitized assessments only."
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" \
  --member="serviceAccount:$SERVICE_ACCOUNT" --role="roles/storage.objectViewer"
gcloud run services update "$SERVICE" --project="$PROJECT" --region="$REGION" \
  --update-env-vars="PUBLIC_EVIDENCE_ENABLED=true,MONITOR_GCS_BUCKET=$BUCKET,PUBLIC_EVIDENCE_OBJECT=$EVIDENCE_OBJECT"
echo "Pinned real historical evidence: gs://$BUCKET/$EVIDENCE_OBJECT"
echo "Inspect actual response (the server will redact record IDs):"
echo "  curl -i https://handoffguard-614664795142.asia-south1.run.app/api/salesforce/evidence"
