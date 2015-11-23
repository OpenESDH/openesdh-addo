package dk.openesdh.addo.webscipts;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptSession;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.RequestParam;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.addo.common.AddoService;
import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.repo.webscripts.ParamUtils;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Send Document To Visma Addo", families = {"Addo"})
public class DocumcentToAddoWebScript {

    private static final Logger LOG = Logger.getLogger(DocumcentToAddoWebScript.class);
    private static final String ADDO_SESSION_TOKEN = "ADDO.SESSION.TOKEN";
    private static final String LOGIN_ERROR = "At least one security token in the message could not be validated.";
    private static final QName PROP_ADDO_PASSWORD = QName.createQName(ContentModel.USER_MODEL_URI, "addoPassword");

    @Autowired
    @Qualifier("addoService")
    private AddoService service;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private AuthorityService authorityService;
    @Autowired
    private NodeService nodeService;

    private String getAddoToken(WebScriptRequest req) {
        WebScriptSession session = req.getRuntime().getSession();
        if (session.getValue(ADDO_SESSION_TOKEN) != null) {
            return (String) session.getValue(ADDO_SESSION_TOKEN);
        }
        String[] user = getUserNameAndPassword();
        String addoToken = service.login(user[0], user[1]);
        session.setValue(ADDO_SESSION_TOKEN, addoToken);
        return addoToken;
    }

    private String[] getUserNameAndPassword() {
        String username = authenticationService.getCurrentUserName();
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        //email as user in addo
        String email = (String) nodeService.getProperty(user, ContentModel.PROP_EMAIL);
        //saved encoded user password for addo
        String pw = (String) nodeService.getProperty(user, PROP_ADDO_PASSWORD);
        return new String[]{email, pw};
    }

    @Uri(value = "/api/openesdh/addo/{username}/save", method = HttpMethod.POST, defaultFormat = "json")
    public void saveUserProperties(
            WebScriptRequest req,
            @UriVariable final String username,
            @RequestParam(required = true) final String addoPassword) {
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        String userEmail = (String) nodeService.getProperty(user, ContentModel.PROP_EMAIL);
        ParamUtils.checkRequiredParam(addoPassword, "addoPassword");
        String password = encodePw(addoPassword);
        try {
            String addoToken = service.login(userEmail, password);
            if (authenticationService.getCurrentUserName().equals(username)) {
                WebScriptSession session = req.getRuntime().getSession();
                session.setValue(ADDO_SESSION_TOKEN, addoToken);
            }
        } catch (AddoException e) {
            if (LOGIN_ERROR.equals(e.getMessage())) {
                throw new WebScriptException("ADDO.USER.INCORECT_PASSWORD");
            }
            throw e;
        }
        nodeService.setProperty(user, PROP_ADDO_PASSWORD, password);
    }

    @Uri(value = "/api/vismaaddo/SigningTemplates", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningTemplates(WebScriptRequest req) throws JSONException {
        String addoToken = getAddoToken(req);
        return WebScriptUtils.jsonResolution(new JSONObject(service.getSigningTemplates(addoToken)));
    }

    public static String encodePw(String pw) {
        return Base64.encodeBase64String(DigestUtils.sha512(pw));
    }
}
