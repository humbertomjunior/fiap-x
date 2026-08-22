# SQS DLQ configuration

The LocalStack init script creates one DLQ per main queue:

- `video-uploaded-queue-dlq`
- `video-status-api-queue-dlq`
- `video-status-notification-queue-dlq`

Configured behavior:

- `maxReceiveCount=4`
  Reason: this gives a few retries for transient failures without leaving poison messages circulating for too long.
- `video-uploaded-queue` uses `VisibilityTimeout=900` seconds
  Reason: FFmpeg extraction plus ZIP generation can take several minutes for larger videos, so the default 30 seconds is too short and could cause duplicate processing.
- `video-status-api-queue` and `video-status-notification-queue` use `VisibilityTimeout=120` seconds
  Reason: these consumers mainly persist state and send notifications, which should finish quickly but still need enough time for short infrastructure hiccups.

Listener error handling was also tightened so runtime failures are rethrown. That allows SQS retry/redrive to work instead of acknowledging failed messages silently.
