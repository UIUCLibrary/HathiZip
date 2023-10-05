ARG PIP_DOWNLOAD_CACHE=/.cache/pip
FROM ubuntu:22.04 AS wheel_builder_base
COPY ci/docker/python/linux/tox/apt-packages.txt /tmp/
RUN apt-get update && \
    apt-get install -y software-properties-common gpg-agent --no-install-recommends && \
    add-apt-repository ppa:deadsnakes/ppa && \
    DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get -y install tzdata && \
    apt-get update && \
     < /tmp/apt-packages.txt xargs apt-get install -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL
ARG PIP_DOWNLOAD_CACHE
RUN mkdir -p ${PIP_DOWNLOAD_CACHE} && \
    chmod -R 777 ${PIP_DOWNLOAD_CACHE}

ENV PIP_DOWNLOAD_CACHE=${PIP_DOWNLOAD_CACHE}
COPY requirements-ci.txt /tmp/python_requirements/
COPY requirements/ /tmp/python_requirements/requirements/
RUN python3 -m pip install --no-cache-dir pip --upgrade && pip3 install --no-cache-dir -r /tmp/python_requirements/requirements-ci.txt
WORKDIR /src
CMD ["tox", "--workdir", "/tmp/tox", "--recreate"]