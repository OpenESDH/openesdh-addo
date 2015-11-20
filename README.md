development
----------------
To run project integrated with OpenESDH-core:
```
../OpenESDH/openesdh-addo > mvn install

../OpenESDH/openesdh-core/openesdh-repo > mvn integration-test -Pvisma-addo,amp-with-solr,unpack-deps
```

standalone alfresco
------------------------
- add system parameter 
> https.protocols=TLSv1,SSLv3,SSLv2Hello

- install **addo-repo.amp**
- deploy **addo-webapp.war** to  *../Alfresco/tomcat/webapps/*
