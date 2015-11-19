package dk.openesdh.office.webscipts;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.repo.webscripts.utils.WebScriptUtils;
import dk.openesdh.addo.common.AddoService;

@Component
@WebScript(description = "Send Document To Visma Addo", families = {"Addo"})
public class DocumcentToAddoWebScript {

    private static final Logger LOG = Logger.getLogger(DocumcentToAddoWebScript.class);

    @Autowired
    private AddoService service;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private AuthorityService authorityService;
    @Autowired
    private NodeService nodeService;

    @Uri(value = "/api/vismaaddo/{zipCode}", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningTemplates(@UriVariable final String zipCode) throws JSONException {
        String userName = authenticationService.getCurrentUserName();
        NodeRef user = authorityService.getAuthorityNodeRef(userName);
        //email as user in addo
        String email = (String) nodeService.getProperty(user, ContentModel.PROP_EMAIL);
        //TODO: password must be saved somewhere
        String pw = "fq8N3bGV+Mh8ZVjMLespCm3qqnISJW76oe+bE1IhcniIIca3lm/jCqcLzLcuW/35svQeuEU51LtyduqyO5vR3g==";
        return WebScriptUtils.jsonResolution(new JSONObject(service.getSigningTemplates(email, pw)));
    }
}
