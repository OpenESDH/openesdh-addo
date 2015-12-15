package dk.openesdh.addo.webscipts;

import dk.openesdh.addo.services.AddoService;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AbstractAddoWebscript {

    public static final QName PROP_ADDO_USERNAME = QName.createQName(ContentModel.USER_MODEL_URI, "addoUsername");
    public static final QName PROP_ADDO_PASSWORD = QName.createQName(ContentModel.USER_MODEL_URI, "addoPassword");
    public static final QName PROP_CASE_ADDO_ID = QName.createQName(ContentModel.USER_MODEL_URI, "caseAddoId");

    @Autowired
    @Qualifier("addoService")
    protected AddoService service;
    @Autowired
    protected AuthenticationService authenticationService;
    @Autowired
    protected AuthorityService authorityService;
    @Autowired
    protected NodeService nodeService;

    protected NodeRef getCurrentUserNodeRef() {
        String username = authenticationService.getCurrentUserName();
        return authorityService.getAuthorityNodeRef(username);
    }

    protected String[] getUserNameAndPassword() {
        NodeRef user = getCurrentUserNodeRef();
        //email as user in addo
        String email = (String) nodeService.getProperty(user, PROP_ADDO_USERNAME);
        //saved encoded user password for addo
        String pw = (String) nodeService.getProperty(user, PROP_ADDO_PASSWORD);
        return new String[]{email, pw};
    }
}
