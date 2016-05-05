package dk.openesdh.addo.webscipts;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.repo.audit.AuditComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.codehaus.plexus.util.StringUtils;
import org.json.JSONException;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Transaction;
import com.github.dynamicextensionsalfresco.webscripts.annotations.TransactionType;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.google.common.collect.Lists;

import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.doctemplates.api.model.OfficeTemplateMerged;
import dk.openesdh.doctemplates.api.services.OfficeTemplateService;
import dk.openesdh.repo.exceptions.DomainException;
import dk.openesdh.repo.model.OpenESDHModel;
import dk.openesdh.repo.webscripts.contacts.ContactUtils;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Send Filled template To Visma Addo", families = {"Addo", "OpenESDH Office Template"})
public class TemplateAddoInitiateSigningWebScript extends AbstractAddoWebscript {

    private final Logger logger = LoggerFactory.getLogger(TemplateAddoInitiateSigningWebScript.class);

    @Autowired
    @Qualifier("auditComponent")
    private AuditComponent audit;
    @Autowired(required = false)
    @Qualifier("OfficeTemplateService")
    private OfficeTemplateService officeTemplateService;

    @Transaction(TransactionType.REQUIRED)
    @Uri(value = "/api/openesdh/template/{store_type}/{store_id}/{node_id}/case/{caseId}/folder/{folderStoreType}/{folderStoreId}/{folderNodeId}/fillToAddo", method = HttpMethod.POST, defaultFormat = "json")
    public void fillToAddo(
            @UriVariable final String store_type,
            @UriVariable final String store_id,
            @UriVariable final String node_id,
            @UriVariable final String caseId,
            @UriVariable final String folderStoreType,
            @UriVariable final String folderStoreId,
            @UriVariable final String folderNodeId,
            WebScriptRequest req, WebScriptResponse res
    ) throws IOException, JSONException {
        if (officeTemplateService == null) {
            throw new RuntimeException("\"doc-templates\" module is unavailable");
        }
        JSONObject json = WebScriptUtils.readJson(req);
        List<OfficeTemplateMerged> merged = officeTemplateService.getMergedTemplates(
                new NodeRef(store_type, store_id, node_id), caseId, json);
        JSONObject fieldData = (JSONObject) json.get("fieldData");
        JSONObject templateJSON = (JSONObject) fieldData.get("addo.template");

        Map<NodeRef, AddoRecipient> recipientsMap = new HashMap<>();
        merged.forEach(mergedDoc -> {
            recipientsMap.put(mergedDoc.getReceiver(), getRecipient(mergedDoc.getReceiver()));
        });
        validateValues(templateJSON, recipientsMap.values());

        Pair<String, String> userCred = getUserNameAndPassword();
        for (OfficeTemplateMerged doc : merged) {
            AddoDocument document = getDocument(doc);
            AddoRecipient recipient = recipientsMap.get(doc.getReceiver());
            String addoSigningToken;
            try {
                addoSigningToken = service.initiateSigning(
                        userCred.getFirst(),
                        userCred.getSecond(),
                        (String) templateJSON.get("Id"),
                        (String) templateJSON.get("FriendlyName"),
                        Arrays.asList(document),
                        Collections.emptyList(),
                        Arrays.asList(recipient),
                        false
                );
            } catch (AddoException ex) {
                if (ex.isDomainException()) {
                    throw new DomainException(ex.getMessage());
                }
                throw new RuntimeException(ex);
            }
            logger.info("Addo initiane signing result for \"{}\": {}", document.getName(), addoSigningToken);
            audit(caseId, document);
        }

        officeTemplateService.saveToFolder(new NodeRef(folderStoreType, folderStoreId, folderNodeId), merged);

        /*NodeRef caseNodeRef = caseService.getCaseById(caseId);

        Map<QName, Serializable> props = new HashMap<>();
        props.put(AddoModel.PROP_ADDO_TOKEN, addoSigningToken);
        nodeService.addAspect(caseNodeRef, AddoModel.ASPECT_ADDO_SIGNED, props);*/
    }

    private void audit(String caseId, AddoDocument document) {
        Map<String, Serializable> auditValues = new HashMap<>();
        auditValues.put("caseId", caseId);
        auditValues.put("documents", document.getName());
        auditValues.put("attachments", Lists.newArrayList());
        audit.recordAuditValues("/esdh-addo/initiatate-signing", auditValues);
    }

    private AddoDocument getDocument(OfficeTemplateMerged mergedDoc) throws IOException {
        AddoDocument document = new AddoDocument();
        document.setData(mergedDoc.getContent());
        document.setMimeType(mergedDoc.getMimetype());
        document.setName(mergedDoc.getFileName());
        return document;
    }

    private AddoRecipient getRecipient(NodeRef recipientNodeRef) {
        Map<QName, Serializable> props = nodeService.getProperties(recipientNodeRef);
        return new AddoRecipient(
                (String) props.get(OpenESDHModel.PROP_CONTACT_CPR_NUMBER),
                (String) props.get(OpenESDHModel.PROP_CONTACT_EMAIL),
                ContactUtils.getAddress(props),
                ContactUtils.getDisplayName(props),
                (String) props.get(OpenESDHModel.PROP_CONTACT_PHONE));
    }

    /**
     * Depending on the chosen signing template there are some required fields we need to be aware of for sending documents with Visma Addo
     * If distribution equal e-mail then e-mail is required (true)
     * If distribution equal SMS then phone no. is required
     * If signing method equal NemID then CPR number is required (true)
     * If "Encrypt document" is chosen then CPR number is required (true)
     * If "Validate by phone" is chosen then phone no. is required
     */
    private void validateValues(JSONObject templateJSON, Collection<AddoRecipient> receivers) throws JSONException {
        if ((templateJSON.containsKey("MessageType") && "Sms".equals((String) templateJSON.get("MessageType")))
                || (templateJSON.containsKey("SmsVerification") && (Boolean) templateJSON.get("SmsVerification"))) {

            Object[] noPhones = receivers.stream()
                    .filter(receiver -> StringUtils.isEmpty(receiver.getPhone()))
                    .map(AddoRecipient::getName)
                    .toArray();
            if (noPhones.length == 0) {
                throw new DomainException("ADDO.ERROR.REQUIRED_PHONE_EMPTY_FOR_RECEIVERS",
                        new org.json.JSONObject().put("receivers", StringUtils.join(noPhones, ", ")));
            }
        }
    }
}
