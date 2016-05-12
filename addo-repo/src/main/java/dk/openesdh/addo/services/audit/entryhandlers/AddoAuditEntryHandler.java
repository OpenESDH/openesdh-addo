package dk.openesdh.addo.services.audit.entryhandlers;

import static dk.openesdh.repo.services.audit.AuditEntryHandler.REC_TYPE.DOCUMENT;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.repo.services.audit.AuditEntry;
import dk.openesdh.repo.services.audit.AuditEntryHandler;
import dk.openesdh.repo.services.audit.AuditSearchService;

@Component
public class AddoAuditEntryHandler extends AuditEntryHandler {

    public static final String ADDO_DOCUMENTS = "/esdh-addo/action/initiate/documents";
    private static final String ADDO_ATTACHMENTS = "/esdh-addo/action/initiate/attachments";

    @Autowired
    private AuditSearchService auditSearchService;

    @PostConstruct
    public void init() {
        auditSearchService.registerApplication("esdh-addo");
        auditSearchService.registerEntryHandler(ADDO_DOCUMENTS, new AddoAuditEntryHandler());
        auditSearchService.registerIgnoredProperties(AddoModel.PROP_ADDO_TOKEN);
    }

    private List<String> getDocumentsList(Serializable documents) {
        if (documents instanceof List) {
            return (List<String>) documents;
        }
        return Arrays.asList(StringUtils.split(documents.toString(), ", "));
    }

    @Override
    public Optional<AuditEntry> handleEntry(AuditEntry auditEntry, Map<String, Serializable> values) {
        auditEntry.setType(DOCUMENT);

        List<String> documents = getDocumentsList(values.get(ADDO_DOCUMENTS));
        List<String> attachments = getDocumentsList(values.get(ADDO_ATTACHMENTS));

        JSONArray props = new JSONArray();
        if (documents.size() == 1) {
            auditEntry.setAction("ADDO.auditlog.INITIATE.ONE");
            auditEntry.addData("title", documents.get(0));
        } else {
            auditEntry.setAction("ADDO.auditlog.INITIATE.MANY");
            for (String doc : documents) {
                props.add(createDataItem("ADDO.auditlog.INITIATE.DOCUMENT", doc));
            }
        }
        for (String attachment : attachments) {
            props.add(createDataItem("ADDO.auditlog.INITIATE.ATTACHMENT", attachment));
        }
        if (props.size() > 0) {
            auditEntry.addData("props", props);
        }
        return Optional.of(auditEntry);
    }

    private JSONObject createDataItem(String code, String title) {
        JSONObject item = new JSONObject();
        JSONObject itemData = new JSONObject();
        item.put("code", code);
        item.put("data", itemData);
        itemData.put("title", title);
        return item;
    }
}
