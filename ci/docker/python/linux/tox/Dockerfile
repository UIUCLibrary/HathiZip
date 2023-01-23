ARG PIP_FIND_LINKS=/wheels
ARG PIP_DOWNLOAD_CACHE=/.cache/pip
FROM ubuntu:20.04 as wheel_builder_base
COPY ci/docker/python/linux/tox/apt-packages.txt /tmp/
RUN apt update && \
    apt-get install -y software-properties-common && \
    add-apt-repository ppa:deadsnakes/ppa && \
    apt update && \
    cat /tmp/apt-packages.txt | xargs apt-get -y install
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL

# ==============================================================================
FROM wheel_builder_base as WHEEL_BUILDER_PY37
ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL

COPY requirements.txt  requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
RUN PYTHON=python3.7 && \
    mkdir -p ${PIP_FIND_LINKS} && \
    $PYTHON -m pip install pip --upgrade pip && \
    $PYTHON -m pip install wheel && \
    $PYTHON -m pip wheel -w ${PIP_FIND_LINKS} --no-cache-dir  \
      -r /tmp/requirements-dev.txt  \
      -r /tmp/requirements-ci.txt  \
      -r /tmp/requirements.txt

# ==============================================================================
FROM wheel_builder_base as WHEEL_BUILDER_PY38
ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL

COPY requirements.txt  requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
ARG PIP_FIND_LINKS
RUN PYTHON=python3.8 && \
    mkdir -p ${PIP_FIND_LINKS} && \
    $PYTHON -m pip install pip --upgrade pip && \
    $PYTHON -m pip install wheel && \
    $PYTHON -m pip wheel -w ${PIP_FIND_LINKS} --no-cache-dir  \
      -r /tmp/requirements-dev.txt  \
      -r /tmp/requirements-ci.txt  \
      -r /tmp/requirements.txt

# ==============================================================================
FROM wheel_builder_base as WHEEL_BUILDER_PY39
ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL

COPY requirements.txt  requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
RUN PYTHON=python3.9 && \
    mkdir -p ${PIP_FIND_LINKS} && \
    $PYTHON -m pip install pip --upgrade pip && \
    $PYTHON -m pip install wheel && \
    $PYTHON -m pip wheel -w ${PIP_FIND_LINKS} --no-cache-dir  \
      -r /tmp/requirements-dev.txt  \
      -r /tmp/requirements-ci.txt  \
      -r /tmp/requirements.txt

# ==============================================================================
FROM wheel_builder_base as WHEEL_BUILDER_PY310
ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL

COPY requirements.txt  requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
RUN PYTHON=python3.10 && \
    mkdir -p ${PIP_FIND_LINKS} && \
    $PYTHON -m ensurepip --upgrade && \
    $PYTHON -m pip install pip --upgrade pip && \
    $PYTHON -m pip install wheel && \
    $PYTHON -m pip wheel -w ${PIP_FIND_LINKS} --no-cache-dir  \
      -r /tmp/requirements-dev.txt  \
      -r /tmp/requirements-ci.txt  \
      -r /tmp/requirements.txt
# ==============================================================================
FROM wheel_builder_base as WHEEL_BUILDER_PY311
ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL

COPY requirements.txt  requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
RUN PYTHON=python3.11 && \
    mkdir -p ${PIP_FIND_LINKS} && \
    $PYTHON -m ensurepip --upgrade && \
    $PYTHON -m pip install pip --upgrade pip && \
    $PYTHON -m pip install wheel && \
    $PYTHON -m pip wheel -w ${PIP_FIND_LINKS} --no-cache-dir  \
      -r /tmp/requirements-dev.txt  \
      -r /tmp/requirements-ci.txt  \
      -r /tmp/requirements.txt

# ==============================================================================
FROM wheel_builder_base
#ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL
ARG PIP_DOWNLOAD_CACHE
RUN mkdir -p ${PIP_DOWNLOAD_CACHE} && \
    chmod -R 777 ${PIP_DOWNLOAD_CACHE}
#COPY --from=WHEEL_BUILDER_PY37 ${PIP_FIND_LINKS}/* ${PIP_FIND_LINKS}/
#COPY --from=WHEEL_BUILDER_PY38 ${PIP_FIND_LINKS}/* ${PIP_FIND_LINKS}/
#COPY --from=WHEEL_BUILDER_PY39 ${PIP_FIND_LINKS}/* ${PIP_FIND_LINKS}/
#COPY --from=WHEEL_BUILDER_PY310 ${PIP_FIND_LINKS}/* ${PIP_FIND_LINKS}/
#COPY --from=WHEEL_BUILDER_PY311 ${PIP_FIND_LINKS}/* ${PIP_FIND_LINKS}/
#ENV PIP_FIND_LINKS=${PIP_FIND_LINKS}
ENV PIP_DOWNLOAD_CACHE=${PIP_DOWNLOAD_CACHE}
COPY requirements/ /tmp/requirements
RUN pip3 config set global.extra-index-url https://jenkins.library.illinois.edu/nexus/repository/uiuc_prescon_python/
RUN if [[ -z "$PIP_INDEX_URL"]]; then pip3 config set global.index-url ${PIP_INDEX_URL}; fi
RUN pip3 install -r /tmp/requirements/requirements_tox.txt "devpi-client"
WORKDIR /src
CMD tox --workdir /tmp/tox --recreate
