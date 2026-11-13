#!/bin/sh
# Khởi tạo tài nguyên AWS giả lập trên MiniStack.
# Được chạy bởi service "aws-init" trong docker-compose.yml.
set -eu

BUCKET="${S3_BUCKET:-todo-reports}"
QUEUE="${SQS_QUEUE:-todo-events}"
TABLE="${DDB_TABLE:-todo-report-history}"

provision() {
  name="$1"
  endpoint="$2"
  aws_() { aws --endpoint-url "$endpoint" "$@"; }

  echo "==> [$name] $endpoint"

  aws_ s3api head-bucket --bucket "$BUCKET" 2>/dev/null \
    || aws_ s3api create-bucket --bucket "$BUCKET" >/dev/null
  echo "    S3 bucket       : $BUCKET"

  aws_ sqs create-queue --queue-name "$QUEUE" >/dev/null
  echo "    SQS queue       : $QUEUE"

  aws_ dynamodb describe-table --table-name "$TABLE" >/dev/null 2>&1 \
    || aws_ dynamodb create-table \
         --table-name "$TABLE" \
         --attribute-definitions AttributeName=id,AttributeType=S \
         --key-schema AttributeName=id,KeyType=HASH \
         --billing-mode PAY_PER_REQUEST >/dev/null
  echo "    DynamoDB table  : $TABLE"
}

provision ministack  "${MINISTACK_ENDPOINT:-http://ministack:4566}"

echo "✅ Đã khởi tạo xong tài nguyên trên MiniStack."
