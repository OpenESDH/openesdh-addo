package dk.openesdh.addo.webscipts;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.addo.services.AddoService;

@Component
public class AbstractAddoWebscript {

    @Autowired
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

    protected Pair<String, String> getUserNameAndPassword() {
        NodeRef user = getCurrentUserNodeRef();
        //email as user in addo
        String email = (String) nodeService.getProperty(user, AddoModel.PROP_ADDO_USERNAME);
        //saved encoded user password for addo
        String pw = (String) nodeService.getProperty(user, AddoModel.PROP_ADDO_PASSWORD);
        return new Pair(email, pw);
    }
}
