<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://github.com/user-attachments/assets/0aa67016-6eaf-458a-adb2-6e31a0763ed6" />
</div>

# TranscribeCare Web

This contains everything you need to run the app locally and deploy to Google Cloud Run.

## Run Locally

**Prerequisites:** Node.js 18+

1. Install dependencies:
   ```bash
   npm install
   ```
2. Copy `.env.example` to `.env` and set your `GEMINI_API_KEY`:
   ```bash
   cp .env.example .env
   ```
3. Run the dev server:
   ```bash
   npm run dev
   ```

## Deploy to Google Cloud Run

The app is deployed as the **transcribecare** service in the **us-west1** region.

### Prerequisites

- [Google Cloud CLI (`gcloud`)](https://cloud.google.com/sdk/docs/install) installed and authenticated
- A GCP project with Cloud Run and Artifact Registry APIs enabled
- Docker (only needed if building locally)

### Deploy a new revision

From the `web/` directory:

```bash
# 1. Build and deploy in one step using Cloud Build (recommended)
gcloud run deploy transcribecare \
  --source . \
  --region us-west1 \
  --allow-unauthenticated \
  --port 8080 \
  --project treatcost-com \
  --set-env-vars "GEMINI_API_KEY=unused"
```

This command:
- Builds the container image using the `Dockerfile` via Cloud Build
- Pushes the image to Artifact Registry
- Deploys a new revision to the **transcribecare** service in **us-west1**

### Deploy with a pre-built image

If you prefer to build and push the image manually:

```bash
# 1. Set your project ID
export PROJECT_ID=$(gcloud config get-value project)

# 2. Build the image
docker build -t us-west1-docker.pkg.dev/$PROJECT_ID/cloud-run-source-deploy/transcribecare .

# 3. Push to Artifact Registry
docker push us-west1-docker.pkg.dev/$PROJECT_ID/cloud-run-source-deploy/transcribecare

# 4. Deploy the new revision
gcloud run deploy transcribecare \
  --image us-west1-docker.pkg.dev/$PROJECT_ID/cloud-run-source-deploy/transcribecare \
  --region us-west1 \
  --allow-unauthenticated \
  --port 8080 \
  --set-env-vars "GEMINI_API_KEY=<your-api-key>"
```

### Verify the deployment

```bash
gcloud run services describe transcribecare --region us-west1 --format="value(status.url)"
```

### Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `PORT` | No | Server port (defaults to 8080, set automatically by Cloud Run) |
| `GEMINI_API_KEY` | Yes | API key for Gemini AI features |

### Rollback

To roll back to a previous revision:

```bash
# List revisions
gcloud run revisions list --service transcribecare --region us-west1

# Route traffic to a specific revision
gcloud run services update-traffic transcribecare \
  --region us-west1 \
  --to-revisions <revision-name>=100
```
