ARG PYTHON_VERSION=3.10
ARG PIP_EXTRA_INDEX_URL
ARG PIP_TRUSTED_HOST

FROM python:${PYTHON_VERSION} as wheel_builder

ADD requirements.txt requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
RUN python -m pip install --upgrade pip &&  \
    pip install setuptools && \
    pip install wheel && \
    pip wheel --wheel-dir=/wheels -r /tmp/requirements.txt -v && \
    pip wheel --wheel-dir=/wheels -r /tmp/requirements-dev.txt && \
    pip wheel --wheel-dir=/wheels -r /tmp/requirements-ci.txt

FROM python:${PYTHON_VERSION}
ARG USER_ID=1000
ARG GROUP_ID=1000

RUN groupadd -g $GROUP_ID user && \
    useradd -u $USER_ID -s /bin/sh -m -d /home/user -g user user

RUN apt update -y && apt install -y libgl1-mesa-glx && \
    rm -rf /var/lib/apt/lists/*

COPY --from=wheel_builder /wheels /wheels/
ADD requirements.txt requirements-dev.txt ci/docker/shared/requirements-ci.txt /tmp/
RUN pip install --no-index --find-links=/wheels -r /tmp/requirements.txt -r /tmp/requirements-dev.txt -r /tmp/requirements-ci.txt
ENV PIP_FIND_LINKS=/wheels
USER user
WORKDIR /home/user

ENV SONAR_SCANNER_VERSION=4.7.0.2747
ENV SONAR_SCANNER_HOME=/home/user/.sonar/sonar-scanner-$SONAR_SCANNER_VERSION-linux
RUN curl --create-dirs -sSLo $HOME/.sonar/sonar-scanner.zip \
        https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-$SONAR_SCANNER_VERSION-linux.zip && \
    unzip -o $HOME/.sonar/sonar-scanner.zip -d $HOME/.sonar/ && \
    rm $HOME/.sonar/sonar-scanner.zip && \
    mkdir -p /home/user/.sonar/cache && \
    chown user:user /home/user/.sonar/cache
ENV PATH=$SONAR_SCANNER_HOME/bin:$PATH
ENV SONAR_SCANNER_OPTS="-server"
