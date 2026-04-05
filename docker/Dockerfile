FROM eclipse-temurin:17-jre

ARG JMETER_VERSION=5.6.3
ARG JMETER_BASE_URL=https://archive.apache.org/dist/jmeter/binaries

ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV JMETER_HEAP="-Xms1g -Xmx2g -XX:MaxMetaspaceSize=256m"
ENV PATH="${JMETER_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates unzip python3 python3-pip nodejs npm \
    && curl -fsSL "${JMETER_BASE_URL}/apache-jmeter-${JMETER_VERSION}.tgz" -o /tmp/jmeter.tgz \
    && tar -xzf /tmp/jmeter.tgz -C /opt \
    && npm install --global allure-commandline@2.34.1 \
    && rm -f /tmp/jmeter.tgz \
    && apt-get purge -y --auto-remove curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY scripts /workspace/scripts
COPY docker/user.properties ${JMETER_HOME}/bin/user.properties
COPY docker/entrypoint.sh /usr/local/bin/jmeter-entrypoint.sh
COPY docker/run_all.sh /usr/local/bin/run-all.sh

RUN chmod +x /usr/local/bin/jmeter-entrypoint.sh /usr/local/bin/run-all.sh

ENTRYPOINT ["/usr/local/bin/jmeter-entrypoint.sh"]
