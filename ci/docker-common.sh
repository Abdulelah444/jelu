# Arguments
# 1: next version
# 2: channel

export DOCKER_CLI_EXPERIMENTAL=enabled
PLATFORMS=linux/amd64,linux/arm/v7,linux/arm64/v8

# Image namespace the release publishes to. Override with DOCKER_IMAGE_REPO
# (for example in the workflow, from a repository variable) when releasing a
# fork; defaults to the upstream image. An empty value falls back to the default.
DOCKER_IMAGE_REPO="${DOCKER_IMAGE_REPO:-wabayang/jelu}"

if [ -z "$2" ]; then
    DOCKER_CHANNEL="latest"
else
    DOCKER_CHANNEL=$2
fi

echo "DockerHub repository: $DOCKER_IMAGE_REPO"
echo "DockerHub channel: $DOCKER_CHANNEL"
