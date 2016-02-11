package dk.openesdh.addo.webscipts;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.audit.AuditComponent;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.repo.model.OpenESDHModel;
import dk.openesdh.repo.services.cases.CaseService;
import dk.openesdh.repo.services.cases.PartyService;
import dk.openesdh.repo.services.documents.DocumentPDFService;
import dk.openesdh.repo.webscripts.contacts.ContactUtils;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Send Document To Visma Addo", families = {"Addo"})
public class AddoInitiateSigningWebScript extends AbstractAddoWebscript {

    private final Logger logger = LoggerFactory.getLogger(AddoInitiateSigningWebScript.class);

    @Autowired
    @Qualifier("CaseService")
    private CaseService caseService;
    @Autowired
    @Qualifier("DocumentPDFService")
    private DocumentPDFService documentPDFService;
    @Autowired
    @Qualifier("auditComponent")
    private AuditComponent audit;
    @Autowired
    @Qualifier("PartyService")
    private PartyService partyService;

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

        partyService.addCaseParty(caseId, OpenESDHModel.CASE_PARTY_ROLE_SENDER,
                receivers.stream().map(AddoRecipient::getEmail).collect(Collectors.toList()));

        Pair<String, String> userCred = getUserNameAndPassword();

        String addoSigningToken = service.initiateSigning(
                userCred.getFirst(),
                userCred.getSecond(),
                templateJSON.getString("Id"),
                templateJSON.getString("FriendlyName"),
                documents,
                enclosureDocuments,
                receivers,
                reqJSON.getBoolean("sequential")
        );

        logger.info("Addo initiane signing result: " + addoSigningToken);
        NodeRef caseNodeRef = caseService.getCaseById(caseId);

        Map<QName, Serializable> props = new HashMap<>();
        props.put(AddoModel.PROP_ADDO_TOKEN, addoSigningToken);
        nodeService.addAspect(caseNodeRef, AddoModel.ASPECT_ADDO_SIGNED, props);

        audit(caseId, documents, enclosureDocuments);

        return WebScriptUtils.jsonResolution(addoSigningToken);
    }

    private void audit(String caseId, List<AddoDocument> documents, List<AddoDocument> enclosureDocuments) {
        Map<String, Serializable> auditValues = new HashMap<>();
        auditValues.put("caseId", caseId);
        auditValues.put("documents", documents.stream().map(AddoDocument::getName).collect(Collectors.joining("\", \"", "\"", "\"")));
        auditValues.put("attachments", enclosureDocuments.stream().map(AddoDocument::getName).collect(Collectors.joining("\", \"", "\"", "\"")));
        audit.recordAuditValues("/esdh-addo/initiatate-signing", auditValues);
    }

    private AddoDocument getDocument(String documentNodeRefId) throws IOException {
        NodeRef documentNodeRef = new NodeRef(documentNodeRefId);
        String title = (String) nodeService.getProperty(documentNodeRef, ContentModel.PROP_TITLE);
        AddoDocument document = new AddoDocument();
        document.setData(getDocumentPdfBytes(documentNodeRef));
        document.setMimeType(MimetypeMap.MIMETYPE_PDF);
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
        Map<QName, Serializable> props = nodeService.getProperties(new NodeRef(recipient.getString("nodeRefId")));
        return new AddoRecipient(
                (String) props.get(OpenESDHModel.PROP_CONTACT_CPR_NUMBER),
                (String) props.get(OpenESDHModel.PROP_CONTACT_EMAIL),
                ContactUtils.getAddress(props),
                ContactUtils.getDisplayName(props),
                (String) props.get(OpenESDHModel.PROP_CONTACT_PHONE));
    }

    private byte[] getDocumentPdfBytes(NodeRef documentNodeRef) throws IOException {
        return IOUtils.toByteArray(documentPDFService.getDocumentPdfThumbnailStream(documentNodeRef)
                .orElseThrow(() -> new WebScriptException("PDF Convertion error")));
    }
}
