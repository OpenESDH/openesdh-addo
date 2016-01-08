package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import java.io.IOException;

import org.alfresco.service.cmr.repository.NodeRef;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.stereotype.Component;

import dk.openesdh.addo.model.AddoDocumentStatus;
import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.repo.services.cases.CaseService;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Gets signing status of Visma Addo document", families = {"Addo"})
public class AddoSigningStatusWebScript extends AbstractAddoWebscript {

    @Autowired
    private CaseService caseService;

    @Uri(value = "/api/openesdh/addo/getSigningStatus/{caseId}", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningStatus(@UriVariable final String caseId) throws JSONException, IOException {
        String[] userCred = getUserNameAndPassword();
        NodeRef caseNodeRef = caseService.getCaseById(caseId);
        if (nodeService.hasAspect(caseNodeRef, AddoModel.ASPECT_ADDO_SIGNED)) {
            String signingTokenId = (String) nodeService.getProperty(caseNodeRef, AddoModel.PROP_ADDO_TOKEN);
            JSONObject signingStatusJSON = new JSONObject(service.getSigningStatus(userCred[0], userCred[1], signingTokenId))
                    .getJSONObject("GetSigningSatus");
            signingStatusJSON.put("state", AddoDocumentStatus.of(signingStatusJSON.getInt("StateID")));
            return WebScriptUtils.jsonResolution(signingStatusJSON);
        }
        throw new WebScriptException("No ADDO association");
    }
}
