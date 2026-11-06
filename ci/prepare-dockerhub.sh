#!/usr/bin/env bash
# Arguments:
# 1: next version
# 2: channel

source "$(dirname "$0")/docker-common.sh" $1 $2

# The Dockerfile is multi-stage: it builds the frontend + fat jar from source
# in an internal build stage, so no separate `./gradlew unpack` is needed here.

# Build docker images (no push)
docker buildx build \
    --platform $PLATFORMS \
    --tag "$DOCKER_IMAGE_REPO:$DOCKER_CHANNEL" \
    --tag "$DOCKER_IMAGE_REPO:$1" \
    --file ./Dockerfile .
