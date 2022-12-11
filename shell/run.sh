#!/bin/bash
cd "$( dirname "${BASH_SOURCE[0]}" )"
java -Xmx1280m \
-Dfile.encoding=UTF-8 \
-DentityExpansionLimit=2147480000 \
-DtotalEntitySizeLimit=2147480000 \
-Djdk.xml.totalEntitySizeLimit=2147480000 \
-classpath \
../classes:\
../conf:\
../lib/log4j-api-2.12.4.jar:\
../lib/log4j-core-2.12.4.jar:\
../lib/xom-1.1b5.jar \
"$@"
