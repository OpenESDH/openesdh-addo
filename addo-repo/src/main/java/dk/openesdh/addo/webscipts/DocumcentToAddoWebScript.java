package dk.openesdh.addo.webscipts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
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
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptSession;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.RequestParam;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.addo.common.AddoDocument;
import dk.openesdh.addo.common.AddoGUID;
import dk.openesdh.addo.common.AddoRecipient;
import dk.openesdh.addo.common.AddoService;
import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.repo.services.NodeInfoService;
import dk.openesdh.repo.services.cases.CaseService;
import dk.openesdh.repo.services.documents.DocumentService;
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
    @Autowired
    private CaseService caseService;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private NodeInfoService nodeInfoService;
    @Autowired
    private ContentService contentService;

    private AddoGUID getAddoToken(WebScriptRequest req) {
        WebScriptSession session = req.getRuntime().getSession();
        if (session.getValue(ADDO_SESSION_TOKEN) != null) {
            return (AddoGUID) session.getValue(ADDO_SESSION_TOKEN);
        }
        String[] user = getUserNameAndPassword();
        AddoGUID addoToken = service.login(user[0], user[1]);
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
            if (service.tryLogin(userEmail, password)) {
                nodeService.setProperty(user, PROP_ADDO_PASSWORD, password);
            }
        } catch (AddoException e) {
            if (LOGIN_ERROR.equals(e.getMessage())) {
                throw new WebScriptException("ADDO.USER.INCORECT_PASSWORD");
            }
            throw e;
        }

    }

    @Uri(value = "/api/vismaaddo/SigningTemplates", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningTemplates(WebScriptRequest req) throws JSONException {
        AddoGUID addoToken = getAddoToken(req);
        return WebScriptUtils.jsonResolution(new JSONObject(service.getSigningTemplates(addoToken)));
    }

    @Uri(value = "/api/vismaaddo/InitiateSigning?documentNodeRefId={documentNodeRefId}&signingTemplateId={signingTemplateId}&signingTemplateName={signingTemplateName}",
            method = HttpMethod.GET, defaultFormat = "json")
    public String initiateSigning(
            WebScriptRequest req,
            @RequestParam(required = true) final String documentNodeRefId,
            @RequestParam(required = true) final String signingTemplateId,
            @RequestParam(required = true) final String signingTemplateName
    ) throws JSONException {
        AddoGUID addoToken = getAddoToken(req);
        NodeRef documentNodeRef = new NodeRef(documentNodeRefId);
        String title = (String) nodeService.getProperty(documentNodeRef, ContentModel.PROP_TITLE);
        ContentReader reader = contentService.getReader(documentNodeRef, ContentModel.PROP_CONTENT);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        byte[] fileBytes = null;
        try {
            FileCopyUtils.copy(reader.getContentInputStream(), os);
            fileBytes = os.toByteArray();
        } catch (IOException e) {
            throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, e.getMessage());
        }

        AddoDocument document = new AddoDocument();
        document.setData(fileBytes);
        document.setMimeType(reader.getMimetype());
        document.setName(title);

        return service.initiateSigning(addoToken, signingTemplateId, signingTemplateName, document,
                Arrays.asList(new AddoRecipient("2205506218", addoToken.username, null, "Arnas", null))//contacts...
        );
    }

    public static String encodePw(String pw) {
        return Base64.encodeBase64String(DigestUtils.sha512(pw));
    }
}
