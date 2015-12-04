package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.RequestParam;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;
import dk.openesdh.addo.model.AddoPasswordEncoder;
import dk.openesdh.repo.webscripts.ParamUtils;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;
import org.alfresco.service.cmr.repository.NodeRef;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.stereotype.Component;

@Component
@WebScript(description = "Visma Addo user managment", families = {"Addo"})
public class AddoUserWebScript extends AbstractAddoWebscript {

    @Uri(value = "/api/openesdh/addo/{username}/save", method = HttpMethod.POST, defaultFormat = "json")
    public void saveUserProperties(
            @UriVariable final String username,
            @RequestParam(required = true) final String addoUsername,
            @RequestParam(required = true) final String addoPassword) {
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        ParamUtils.checkRequiredParam(addoPassword, "addoPassword");
        String password = AddoPasswordEncoder.encode(addoPassword);
        if (!service.tryLogin(addoUsername, password)) {
            throw new WebScriptException("ADDO.USER.INCORECT_PASSWORD");
        }
        nodeService.setProperty(user, PROP_ADDO_USERNAME, addoUsername);
        nodeService.setProperty(user, PROP_ADDO_PASSWORD, password);
    }

    @Uri(value = "/api/openesdh/addo/{username}/props", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getAddoUserProperties(@UriVariable final String username) throws JSONException {
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        JSONObject addoJSON = new JSONObject()
                .put(PROP_ADDO_USERNAME.getLocalName(), nodeService.getProperty(user, PROP_ADDO_USERNAME))
                .put("configured", nodeService.getProperty(user, PROP_ADDO_PASSWORD) != null);//not returning password
        return WebScriptUtils.jsonResolution(addoJSON);
    }

    @Uri(value = "/api/openesdh/addo/props", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getAddoCurrentUserProperties() throws JSONException {
        return getAddoUserProperties(authenticationService.getCurrentUserName());
    }
}
