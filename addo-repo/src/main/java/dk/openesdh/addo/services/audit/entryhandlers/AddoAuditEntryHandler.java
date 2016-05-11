package dk.openesdh.addo.services.audit.entryhandlers;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
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

    @Override
    public Optional<AuditEntry> handleEntry(String user, long time, Map<String, Serializable> values) {
        AuditEntry auditEntry = new AuditEntry(user, time);
        auditEntry.setType(REC_TYPE.DOCUMENT);

        String documents = (String) values.get(ADDO_DOCUMENTS);
        String attachments = (String) values.get(ADDO_ATTACHMENTS);

        String docsCount = BooleanUtils.toString(documents.contains(", "), "n", "1");
        auditEntry.addData("documents", documents);
        if (StringUtils.isEmpty(attachments)) {
            auditEntry.setAction("ADDO.auditlog.INITIATE_" + docsCount);
        } else {
            auditEntry.setAction("ADDO.auditlog.INITIATE_WITH_ATTACHMENTS_" + docsCount);
            auditEntry.addData("attachments", attachments);
        }
        return Optional.of(auditEntry);
    }
}
