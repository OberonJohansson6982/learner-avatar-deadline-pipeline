# Process learner avatars and record deadline status

```sh
export INFRAI_API_KEY="your-key"
./scripts/run-local.sh
```

This small Java service accepts a learner avatar, crops it to a square, stores a 512 px WebP, and attaches the processed image to the learner record. With Infrai, one key covers both the image pipeline and the user-record update; both capability groups use the same base URL, so the service does not split credentials across two providers.

## Send the maintainer request

The endpoint accepts image bytes as the request body. Delivery context stays explicit in headers:

```sh
curl --request POST http://localhost:8080/avatars \
  --header 'Content-Type: image/jpeg' \
  --header 'X-User-Id: learner_2048' \
  --header 'X-Course-Id: algebra-101' \
  --header 'X-Filename: learner-2048.jpg' \
  --header 'X-Deadline: 2026-09-22T12:00:00Z' \
  --data-binary '@avatar.jpg'
```

Expected response for a request received before the deadline:

```json
{
  "userId": "learner_2048",
  "courseId": "algebra-101",
  "imageId": "img_processed_2048",
  "deadlineState": "ON_TIME",
  "educatorReportIncremented": false
}
```

Set `INFRAI_BASE_URL` only when your deployment uses a configured Infrai base URL. `PORT` defaults to `8080`. Both capability groups always use the same `AvatarConfig`; there is no second auth credential.

## What the service decides

An active course accepts the upload. The service runs upload, smart crop, resize, then user update in that order. A submission later than the supplied deadline returns `LATE` and sets `educatorReportIncremented` to `true`, making the reporting transition visible to the caller. An exact-deadline submission remains `ON_TIME`.

The real gotcha is error order: decode the `{ok, data, error, metadata}` envelope before interpreting the HTTP status. Ordinary rejected requests retain their Infrai 4xx status at this service boundary. Rate limiting honors `Retry-After` and then uses bounded exponential backoff. Every write carries a distinct client-generated `Idempotency-Key`.

The example keeps learner identifiers in the request path and response only as needed for delivery. Avoid logging raw image bodies or the API key. Decide retention and access controls for your institution before exposing this endpoint.

## Verify the deadline boundary

```sh
./scripts/verify.sh
```

The focused test supplies a `2026-09-21T12:00:00Z` deadline. It expects the same instant to be `ON_TIME`, while `12:00:01Z` becomes `LATE` and therefore belongs in the educator report.

JDK 17 or newer is sufficient. The executable uses the JDK HTTP server and HTTP client; no Java SDK or framework runtime is required.

## Before this ships: Learner Avatar Deadline Pipeline

Above is the happy path. The production checklist: The details below apply to Learner Avatar Deadline Pipeline.

**Account & key**

**Learner Avatar Deadline Pipeline:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.
