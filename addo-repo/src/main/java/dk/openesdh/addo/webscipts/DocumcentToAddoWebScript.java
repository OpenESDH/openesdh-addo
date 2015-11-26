package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.RequestParam;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.addo.services.AddoService;
import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.repo.model.OpenESDHModel;
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
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
    private ContentService contentService;

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
        String[] user = getUserNameAndPassword();
        return WebScriptUtils.jsonResolution(new JSONObject(service.getSigningTemplates(user[0], user[1])));
    }

    @Uri(value = "/api/vismaaddo/InitiateSigning",
            method = HttpMethod.POST, defaultFormat = "json")
    public Resolution initiateSigning(WebScriptRequest req) throws JSONException, IOException {
        JSONObject reqJSON = new JSONObject(req.getContent().getContent());
        JSONArray documentsJSON = reqJSON.getJSONArray("documents");
        JSONArray receiversJSON = reqJSON.getJSONArray("receivers");
        JSONObject templateJSON = reqJSON.getJSONObject("template");

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
        //TODO: save addoSigningToken for future use
        return WebScriptUtils.jsonResolution(addoSigningToken);
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

    public static String encodePw(String pw) {
        return Base64.encodeBase64String(DigestUtils.sha512(pw));
    }
}
