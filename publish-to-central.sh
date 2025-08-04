#!/bin/bash

# GrowthBook Kotlin SDK - Central Publishing Portal Upload
set -e

echo "🚀 Publishing GrowthBook 6.0.1 to Maven Central via Portal API..."

# Your Central Publishing Portal credentials
USERNAME="D4CQ23"
PASSWORD="O0VrXmpklDYC0OsYzapEig9aTpgLY2x4G"

# Create base64 encoded auth token
AUTH_TOKEN=$(printf "${USERNAME}:${PASSWORD}" | base64)
echo "✅ Created auth token"

# Build the artifacts first
echo "📦 Building artifacts..."
./gradlew :GrowthBook:publishToMavenLocal

# Create bundle from local Maven repository 
echo "📦 Creating deployment bundle..."
cd ~/.m2/repository/io/github/growthbook
zip -r /tmp/growthbook-6.0.1-bundle.zip . -i "*6.0.1*"
echo "✅ Bundle created: /tmp/growthbook-6.0.1-bundle.zip"

# Upload to Central Publishing Portal
echo "🚀 Uploading to Central Publishing Portal..."
DEPLOYMENT_ID=$(curl --request POST \
  --silent \
  --header "Authorization: Bearer ${AUTH_TOKEN}" \
  --form "bundle=@/tmp/growthbook-6.0.1-bundle.zip" \
  --form "publishingType=AUTOMATIC" \
  "https://central.sonatype.com/api/v1/publisher/upload")

echo "✅ Upload successful! Deployment ID: ${DEPLOYMENT_ID}"

# Check status
echo "🔍 Checking deployment status..."
sleep 5
curl --request POST \
  --header "Authorization: Bearer ${AUTH_TOKEN}" \
  "https://central.sonatype.com/api/v1/publisher/status?id=${DEPLOYMENT_ID}" \
  | jq '.'

echo "🎉 GrowthBook 6.0.1 uploaded! Check status at:"
echo "   https://central.sonatype.com/publishing/deployments"
echo ""
echo "📦 Once published, your customer can use:"
echo "   implementation(\"io.github.growthbook:GrowthBook:6.0.1\")"