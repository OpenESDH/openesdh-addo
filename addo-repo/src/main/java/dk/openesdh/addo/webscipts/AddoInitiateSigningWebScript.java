package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.repo.model.OpenESDHModel;
import dk.openesdh.repo.services.cases.CaseService;
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
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

@Component
@WebScript(description = "Send Document To Visma Addo", families = {"Addo"})
public class AddoInitiateSigningWebScript extends AbstractAddoWebscript {

    private static final Logger LOG = Logger.getLogger(AddoInitiateSigningWebScript.class);

    @Autowired
    private ContentService contentService;
    @Autowired
    private CaseService caseService;

    @Uri(value = "/api/openesdh/addo/InitiateSigning",
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
