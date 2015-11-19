openesdh-addo
===================
OpenESDH integration with Visma Addo

required system parameters
---------------------------------
```
MAVEN_OPTS=-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=10999 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.hostname="localhost"

https.protocols=TLSv1,SSLv3,SSLv2Hello
```
development
----------------
To run project integrated with OpenESDH-core:
```
../OpenESDH/openesdh-core/openesdh-repo > export MAVEN_OPTS="..."

../OpenESDH/openesdh-core/openesdh-repo > mvn integration-test -Pvisma-addo,amp-with-solr,unpack-deps
``` 

standalone alfresco
------------------------

- install **addo-repo.amp**
- deploy **addo-webapp.war** to  *../Alfresco/tomcat/webapps/*
