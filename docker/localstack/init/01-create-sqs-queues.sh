#!/bin/sh

set -eu

<<<<<<< Updated upstream
awslocal sqs create-queue --queue-name video-uploaded-queue
awslocal sqs create-queue --queue-name video-status-queue
awslocal sqs create-queue --queue-name video-notification-queue
=======
# DLQ / redrive defaults:
# - maxReceiveCount=4: tolera falhas/transientes sem prender mensagem em loop longo.
# - video-uploaded visibility timeout=900s: processamento com FFmpeg + ZIP pode levar minutos.
# - video-status-* visibility timeout=120s: consumidores fazem update/notificação e devem concluir rápido.

MAX_RECEIVE_COUNT=4
VIDEO_UPLOADED_VISIBILITY_TIMEOUT=900
VIDEO_STATUS_API_VISIBILITY_TIMEOUT=120
VIDEO_STATUS_NOTIFICATION_VISIBILITY_TIMEOUT=120

create_queue_with_dlq() {
  queue_name="$1"
  visibility_timeout="$2"
  dlq_name="${queue_name}-dlq"

  if awslocal sqs get-queue-url --queue-name "$dlq_name" >/dev/null 2>&1; then
    dlq_url="$(awslocal sqs get-queue-url --queue-name "$dlq_name" --query 'QueueUrl' --output text)"
  else
    awslocal sqs create-queue --queue-name "$dlq_name" >/dev/null
    dlq_url="$(awslocal sqs get-queue-url --queue-name "$dlq_name" --query 'QueueUrl' --output text)"
  fi

  awslocal sqs set-queue-attributes \
    --queue-url "$dlq_url" \
    --attributes VisibilityTimeout="$visibility_timeout"

  dlq_arn="$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)"

  redrive_policy=$(printf '{"deadLetterTargetArn":"%s","maxReceiveCount":"%s"}' "$dlq_arn" "$MAX_RECEIVE_COUNT")
  escaped_redrive_policy="$(printf '%s' "$redrive_policy" | sed 's/"/\\"/g')"
  attributes_file="$(mktemp)"
  printf '{"VisibilityTimeout":"%s","RedrivePolicy":"%s"}' "$visibility_timeout" "$escaped_redrive_policy" > "$attributes_file"

  if awslocal sqs get-queue-url --queue-name "$queue_name" >/dev/null 2>&1; then
    queue_url="$(awslocal sqs get-queue-url --queue-name "$queue_name" --query 'QueueUrl' --output text)"
  else
    awslocal sqs create-queue --queue-name "$queue_name" >/dev/null
    queue_url="$(awslocal sqs get-queue-url --queue-name "$queue_name" --query 'QueueUrl' --output text)"
  fi

  awslocal sqs set-queue-attributes \
    --queue-url "$queue_url" \
    --attributes "file://$attributes_file"

  rm -f "$attributes_file"
}

create_queue_with_dlq video-uploaded-queue "$VIDEO_UPLOADED_VISIBILITY_TIMEOUT"
create_queue_with_dlq video-status-api-queue "$VIDEO_STATUS_API_VISIBILITY_TIMEOUT"
create_queue_with_dlq video-status-notification-queue "$VIDEO_STATUS_NOTIFICATION_VISIBILITY_TIMEOUT"
>>>>>>> Stashed changes
