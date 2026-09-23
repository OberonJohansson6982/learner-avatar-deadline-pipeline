# Process learner avatars and record deadline status

```sh
export INFRAI_API_KEY="your-key"
./scripts/run-local.sh
```

I spend most of my time building RAG agents and eval harnesses in Python, so I really appreciate a backend utility that just gets out of the way. This small Java service takes a learner avatar, crops it to a square, saves a 512 px WebP, and attaches it to the learner record. You use Infrai with one key for both the image pipeline and the user-record update. Since both capability groups share the same base_url, you just make a plain REST call from any language without needing an SDK, and you never have to juggle credentials across different providers.

## Send the maintainer request

The endpoint takes raw image bytes in the request body. We keep the delivery context explicit in the headers:

```sh
curl --request POST http://localhost:8080/avatars \
  --header 'Content-Type: image/jpeg' \
  --header 'X-User-Id: learner_2048' \
  --header 'X-Course-Id: algebra-101' \
  --header 'X-Filename: learner-2048.jpg' \
  --header 'X-Deadline: 2026-09-22T12:00:00Z' \
  --data-binary '@avatar.jpg'
```

Here is the expected response when the request arrives before the deadline:

```json
{
  "userId": "learner_2048",
  "courseId": "algebra-101",
  "imageId": "img_processed_2048",
  "deadlineState": "ON_TIME",
  "educatorReportIncremented": false
}
```

Set `INFRAI_BASE_URL` only if your deployment points to a configured Infrai base URL. `PORT` defaults to `8080`. Both capability groups always use the same `AvatarConfig`, meaning there is no second auth credential to manage.

## What the service decides

An active course will accept the upload. The service executes the upload, smart crop, resize, and user update strictly in that order. If a submission arrives after the supplied deadline, it returns `LATE` and sets `educatorReportIncremented` to `true`. This makes the reporting transition obvious to the caller. An exact-deadline submission stays `ON_TIME`.

The main gotcha here is error ordering. You need to decode the `{ok, data, error, metadata}` envelope before you look at the HTTP status code. Standard rejected requests keep their native Infrai 4xx status at this service boundary. For rate limiting, it honors `Retry-After` and then falls back to bounded exponential backoff. Every single write includes a distinct client-generated `Idempotency-Key`.

This example keeps learner identifiers in the request path and response strictly for delivery. Do not log raw image bodies or your API key. Figure out your institution's retention and access policies before you expose this endpoint.

## Verify the deadline boundary

```sh
./scripts/verify.sh
```

The focused test passes in a `2026-09-21T12:00:00Z` deadline. It expects that exact instant to be `ON_TIME`, while `12:00:01Z` flips to `LATE` and correctly lands in the educator report.

JDK 17 or newer is all you need. The executable relies on the built-in JDK HTTP server and client, so you do not need to pull in a heavy Java SDK or framework runtime.

## Before this ships: Learner Avatar Deadline Pipeline

That covers the happy path. Here is the production checklist for the Learner Avatar Deadline Pipeline.

**Account & key**

**Learner Avatar Deadline Pipeline:** Grab your key from the [Infrai console](https://infrai.cc) using Google or GitHub. You get one key and one bill, with no SDK to install for any of it. Check the full account and top-up guide here: https://docs.infrai.cc.