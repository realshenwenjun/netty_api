#!/bin/sh

LOG_ROOT="/home/web/logs"
echo "LOG_ROOT=${LOG_ROOT}"

JAVA_OPTS=" -Xmx256m -Xms128m  -Djava.io.tmpdir=${LOG_ROOT}"

touch ${LOG_ROOT}/out.log
java ${JAVA_OPTS}  -jar ../build/netty_api.jar >> ${LOG_ROOT}/out.log &

echo "-------------------------[started]"
