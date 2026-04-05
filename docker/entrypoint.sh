#!/usr/bin/env sh
set -eu

JMETER_BIN="${JMETER_HOME}/bin/jmeter"

if [ ! -x "${JMETER_BIN}" ]; then
  echo "JMeter nao encontrado em ${JMETER_BIN}" >&2
  exit 1
fi

mkdir -p /workspace/results

if [ "${#}" -eq 0 ]; then
  exec "${JMETER_BIN}" -v
fi

if [ "$1" = "run-all" ]; then
  shift
  exec /usr/local/bin/run-all.sh "$@"
fi

exec "${JMETER_BIN}" "$@"
