#!/usr/bin/env bash
# Arguments:
# 1: next version
# 2: channel

source "$(dirname "$0")/docker-common.sh" $1 $2

# Push docker images (built previously)
docker buildx build \
    --platform $PLATFORMS \
    --tag "$DOCKER_IMAGE_REPO:$DOCKER_CHANNEL" \
    --tag "$DOCKER_IMAGE_REPO:$1" \
    --file ./Dockerfile . \
    --push
