package dk.openesdh.addo.webscipts;

import static dk.openesdh.repo.services.audit.entryhandlers.TransactionPathAuditEntryHandler.TRANSACTION_PATH;

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
import org.alfresco.service.cmr.repository.Path;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.compress.utils.IOUtils;
import org.codehaus.plexus.util.StringUtils;
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
import com.google.common.collect.ImmutableMap;

import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.repo.exceptions.DomainException;
import dk.openesdh.repo.model.OpenESDHModel;
import dk.openesdh.repo.services.audit.AuditUtils;
import dk.openesdh.repo.services.cases.CaseService;
import dk.openesdh.repo.services.cases.PartyService;
import dk.openesdh.repo.services.contacts.PartyRoleService;
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
    @Qualifier(PartyService.BEAN_ID)
    private PartyService partyService;
    @Autowired
    @Qualifier("PartyRoleService")
    private PartyRoleService partyRoleService;
    @Autowired
    @Qualifier("NamespaceService")
    private NamespaceService namespaceService;

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

        validateValues(templateJSON, receivers);

        partyService.addCaseParty(caseId, getMemberPartyRole(),
                receivers.stream().map(AddoRecipient::getEmail).collect(Collectors.toList()));

        Pair<String, String> userCred = getUserNameAndPassword();

        String addoSigningToken;
        try {
            addoSigningToken = service.initiateSigning(
                    userCred.getFirst(),
                    userCred.getSecond(),
                    templateJSON.getString("Id"),
                    templateJSON.getString("FriendlyName"),
                    documents,
                    enclosureDocuments,
                    receivers,
                    reqJSON.getBoolean("sequential")
            );
        } catch (AddoException ex) {
            if (ex.isDomainException()) {
                throw new DomainException(ex.getMessage());
            }
            throw new RuntimeException(ex);
        }

        logger.info("Addo initiane signing result: " + addoSigningToken);
        NodeRef caseNodeRef = caseService.getCaseById(caseId);

        Map<QName, Serializable> props = new HashMap<>();
        props.put(AddoModel.PROP_ADDO_TOKEN, addoSigningToken);
        nodeService.addAspect(caseNodeRef, AddoModel.ASPECT_ADDO_SIGNED, props);

        audit(caseId, documents, enclosureDocuments);

        return WebScriptUtils.jsonResolution(addoSigningToken);
    }

    private NodeRef getMemberPartyRole() {
        return partyRoleService.getClassifValueByName(PartyRoleService.MEMBER_ROLE).get().getNodeRef();
    }

    private void audit(String caseId, List<AddoDocument> documents, List<AddoDocument> enclosureDocuments) {
        Map<String, Serializable> auditValues = new HashMap<>();
        auditValues.put("caseId", caseId);
        auditValues.put("documents", getDocumentNames(documents));
        auditValues.put("attachments", getDocumentNames(enclosureDocuments));
        audit.recordAuditValues("/esdh-addo/initiatate-signing", auditValues);
    }

    private ArrayList getDocumentNames(List<AddoDocument> list) {
        ArrayList<String> documents = new ArrayList<>();
        for (AddoDocument addoDocument : list) {
            String title = addoDocument.getName();
            Path path = nodeService.getPath(new NodeRef(addoDocument.getId()));
            String docPath = AuditUtils.getDocumentPath(ImmutableMap.of(TRANSACTION_PATH, path.toPrefixString(namespaceService)));
            documents.add(docPath + title);
        }
        return documents;
    }

    private AddoDocument getDocument(String documentNodeRefId) throws IOException {
        NodeRef documentNodeRef = new NodeRef(documentNodeRefId);
        String title = (String) nodeService.getProperty(documentNodeRef, ContentModel.PROP_TITLE);
        AddoDocument document = new AddoDocument();
        document.setId(documentNodeRefId);
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

    /**
     * Depending on the chosen signing template there are some required fields we need to be aware of for sending documents with Visma Addo
     * If distribution equal e-mail then e-mail is required (true)
     * If distribution equal SMS then phone no. is required
     * If signing method equal NemID then CPR number is required (true)
     * If "Encrypt document" is chosen then CPR number is required (true)
     * If "Validate by phone" is chosen then phone no. is required
     */
    private void validateValues(JSONObject templateJSON, List<AddoRecipient> receivers) throws JSONException {
        if ((templateJSON.has("MessageType") && "Sms".equals(templateJSON.getString("MessageType")))
                || (templateJSON.has("SmsVerification") && templateJSON.getBoolean("SmsVerification"))) {

            Object[] noPhones = receivers.stream()
                    .filter(receiver -> StringUtils.isEmpty(receiver.getPhone()))
                    .map(AddoRecipient::getName)
                    .toArray();
            if (noPhones.length >= 0) {
                throw new DomainException("ADDO.ERROR.REQUIRED_PHONE_EMPTY_FOR_RECEIVERS",
                        new JSONObject().put("receivers", StringUtils.join(noPhones, ", ")));
            }
        }
    }
}
