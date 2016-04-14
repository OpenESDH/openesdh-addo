package dk.openesdh.addo.webscipts;

import java.io.Serializable;
import java.util.Map;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Visma Addo user managment", families = {"Addo"})
public class AddoUserWebScript extends AbstractAddoWebscript {

    @Uri(value = "/api/openesdh/addo/{username}/props", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getAddoUserProperties(@UriVariable final String username) throws JSONException {
        NodeRef user = authorityService.getAuthorityNodeRef(username);
        JSONObject addoJSON = new JSONObject();
        if (nodeService.hasAspect(user, AddoModel.ASPECT_ADDO_CREDENTIALS)) {
            Map<QName, Serializable> properties = nodeService.getProperties(user);
            String addoUserName = (String) properties.get(AddoModel.PROP_ADDO_USERNAME);
            //intentionally not returning password!
            addoJSON.put(AddoModel.PROP_ADDO_USERNAME.getLocalName(), addoUserName);
            addoJSON.put("configured", true);
        } else {
            addoJSON.put("configured", false);
        }
        return WebScriptUtils.jsonResolution(addoJSON);
    }

    @Uri(value = "/api/openesdh/addo/props", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getAddoCurrentUserProperties() throws JSONException {
        return getAddoUserProperties(authenticationService.getCurrentUserName());
    }
}
