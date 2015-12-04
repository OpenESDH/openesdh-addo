package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;
import dk.openesdh.addo.model.AddoDocumentStatus;
import dk.openesdh.repo.services.cases.CaseService;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;
import java.io.IOException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@WebScript(description = "Gets signing status of Visma Addo document", families = {"Addo"})
public class AddoSigningStatusWebScript extends AbstractAddoWebscript {

    private static final Logger LOG = Logger.getLogger(AddoSigningStatusWebScript.class);

    @Autowired
    private CaseService caseService;

    @Uri(value = "/api/openesdh/addo/getSigningStatus/{caseId}", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningStatus(@UriVariable final String caseId) throws JSONException, IOException {
        String[] userCred = getUserNameAndPassword();
        NodeRef caseNodeRef = caseService.getCaseById(caseId);
        String signingTokenId = (String) nodeService.getProperty(caseNodeRef, PROP_CASE_ADDO_ID);
        JSONObject signingStatusJSON = new JSONObject(service.getSigningStatus(userCred[0], userCred[1], signingTokenId))
                .getJSONObject("GetSigningSatus");
        signingStatusJSON.put("state", AddoDocumentStatus.of(signingStatusJSON.getInt("StateID")));
        return WebScriptUtils.jsonResolution(signingStatusJSON);
    }
}
