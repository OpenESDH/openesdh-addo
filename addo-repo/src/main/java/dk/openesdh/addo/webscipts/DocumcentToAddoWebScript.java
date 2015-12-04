package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.RequestParam;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoDocumentStatus;
import dk.openesdh.addo.model.AddoPasswordEncoder;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.addo.services.AddoService;
import dk.openesdh.repo.model.OpenESDHModel;
import dk.openesdh.repo.services.cases.CaseService;
import dk.openesdh.repo.webscripts.ParamUtils;
import dk.openesdh.repo.webscripts.contacts.ContactUtils;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

@Component
@WebScript(description = "Send Document To Visma Addo", families = {"Addo"})
public class DocumcentToAddoWebScript {

    private static final Logger LOG = Logger.getLogger(DocumcentToAddoWebScript.class);
    private static final QName PROP_ADDO_USERNAME = QName.createQName(ContentModel.USER_MODEL_URI, "addoUsername");
    private static final QName PROP_ADDO_PASSWORD = QName.createQName(ContentModel.USER_MODEL_URI, "addoPassword");
    private static final QName PROP_CASE_ADDO_ID = QName.createQName(ContentModel.USER_MODEL_URI, "addoCaseId");

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
    private ContentService contentService;
    @Autowired
    private CaseService caseService;

    private NodeRef getCurrentUserNodeRef() {
        String username = authenticationService.getCurrentUserName();
        return authorityService.getAuthorityNodeRef(username);
    }

    private String[] getUserNameAndPassword() {
        NodeRef user = getCurrentUserNodeRef();
        //email as user in addo
        String email = (String) nodeService.getProperty(user, ContentModel.PROP_EMAIL);
        //saved encoded user password for addo
        String pw = (String) nodeService.getProperty(user, PROP_ADDO_PASSWORD);
        return new String[]{email, pw};
    }

    @Uri(value = "/api/openesdh/addo/{username}/save", method = HttpMethod.POST, defaultFormat = "json")
    public void saveUserProperties(
            @UriVariable final String username,
            @RequestParam(required = true) final String addoUsername,
            @RequestParam(required = true) final String addoPassword) {
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        ParamUtils.checkRequiredParam(addoPassword, "addoPassword");
        String password = AddoPasswordEncoder.encode(addoPassword);
        if (!service.tryLogin(addoUsername, password)) {
            throw new WebScriptException("ADDO.USER.INCORECT_PASSWORD");
        }
        nodeService.setProperty(user, PROP_ADDO_USERNAME, addoUsername);
        nodeService.setProperty(user, PROP_ADDO_PASSWORD, password);
    }

    @Uri(value = "/api/vismaaddo/SigningTemplates", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningTemplates(WebScriptRequest req) throws JSONException {
        String[] user = getUserNameAndPassword();
        return WebScriptUtils.jsonResolution(new JSONObject(service.getSigningTemplates(user[0], user[1])));
    }

    @Uri(value = "/api/openesdh/addo/{username}/props", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getAddoUserProperties(@UriVariable final String username) throws JSONException {
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        JSONObject addoJSON = new JSONObject()
                .put(PROP_ADDO_USERNAME.getLocalName(), nodeService.getProperty(user, PROP_ADDO_USERNAME))
                .put("configured", nodeService.getProperty(user, PROP_ADDO_PASSWORD) != null);//not returning password
        return WebScriptUtils.jsonResolution(addoJSON);
    }

    @Uri(value = "/api/openesdh/addo/props", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getAddoCurrentUserProperties() throws JSONException {
        return getAddoUserProperties(authenticationService.getCurrentUserName());
    }

    @Uri(value = "/api/vismaaddo/InitiateSigning",
            method = HttpMethod.POST, defaultFormat = "json")
    public Resolution initiateSigning(WebScriptRequest req) throws JSONException, IOException {
        JSONObject reqJSON = new JSONObject(req.getContent().getContent());
        JSONArray documentsJSON = reqJSON.getJSONArray("documents");
        JSONArray receiversJSON = reqJSON.getJSONArray("receivers");
        JSONObject templateJSON = reqJSON.getJSONObject("template");
        String caseId = reqJSON.getString("caseId");

        List<AddoDocument> documents = new ArrayList<>();
        List<AddoDocument> enclosureDocuments = new ArrayList<>();
        for (int i = 0; i < documentsJSON.length(); i++) {
            JSONObject doc = documentsJSON.getJSONObject(i);
            AddoDocument document = getDocument(doc.getString("nodeRef"));
            if (doc.getBoolean("sign")) {
                documents.add(document);
            } else {
                enclosureDocuments.add(document);
            }
        }

        List<AddoRecipient> receivers = getRecipients(receiversJSON);

        String[] userCred = getUserNameAndPassword();

        String addoSigningToken = service.initiateSigning(
                userCred[0],
                userCred[1],
                templateJSON.getString("Id"),
                templateJSON.getString("FriendlyName"),
                documents,
                enclosureDocuments,
                receivers,
                reqJSON.getBoolean("sequential")
        );
        LOG.info("Addo initiane signing result: " + addoSigningToken);
        NodeRef caseNodeRef = caseService.getCaseById(caseId);
        nodeService.setProperty(caseNodeRef, PROP_CASE_ADDO_ID, addoSigningToken);
        return WebScriptUtils.jsonResolution(addoSigningToken);
    }

    @Uri(value = "/api/vismaaddo/getSigningStatus/{caseId}", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningStatus(@UriVariable final String caseId) throws JSONException, IOException {
        String[] userCred = getUserNameAndPassword();
        NodeRef caseNodeRef = caseService.getCaseById(caseId);
        String signingTokenId = (String) nodeService.getProperty(caseNodeRef, PROP_CASE_ADDO_ID);
        JSONObject signingStatusJSON = new JSONObject(service.getSigningStatus(userCred[0], userCred[1], signingTokenId))
                .getJSONObject("GetSigningSatus");
        signingStatusJSON.put("state", AddoDocumentStatus.of(signingStatusJSON.getInt("StateID")));
        return WebScriptUtils.jsonResolution(signingStatusJSON);
    }

    private AddoDocument getDocument(String documentNodeRefId) {
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
        return document;
    }

    private List<AddoRecipient> getRecipients(JSONArray receiversJSON) throws JSONException {
        List<AddoRecipient> receivers = new ArrayList<>();
        for (int i = 0; i < receiversJSON.length(); i++) {
            receivers.add(getRecipient(receiversJSON.getJSONObject(i)));
        }
        return receivers;
    }

    private AddoRecipient getRecipient(JSONObject recipient) throws JSONException {
        Map<QName, Serializable> props = nodeService.getProperties(new NodeRef(recipient.getString("nodeRef")));
        return new AddoRecipient(
                (String) props.get(OpenESDHModel.PROP_CONTACT_CPR_NUMBER),
                (String) props.get(OpenESDHModel.PROP_CONTACT_EMAIL),
                ContactUtils.getAddress(props),
                recipient.getString("displayName"),
                (String) props.get(OpenESDHModel.PROP_CONTACT_PHONE));
    }
}
