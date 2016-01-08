package dk.openesdh.addo.services;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.addo.services.audit.antryhandlers.AddoAuditEntryHandler;
import static dk.openesdh.addo.services.audit.antryhandlers.AddoAuditEntryHandler.ADDO_DOCUMENTS;
import dk.openesdh.repo.services.audit.AuditSearchService;

@Service
public class AddoAuditRegisterService {

    @Autowired
    private AuditSearchService auditSearchService;

    @PostConstruct
    public void init() {
        auditSearchService.registerApplication("esdh-addo");
        auditSearchService.registerEntryHandler(ADDO_DOCUMENTS, new AddoAuditEntryHandler());
        auditSearchService.registerIgnoredProperties(AddoModel.PROP_ADDO_TOKEN);
    }

}
