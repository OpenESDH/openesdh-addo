package dk.openesdh.addo.webscipts;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.stereotype.Component;

@Component
@WebScript(description = "Returns Visma Addo Signing Templates for current user", families = {"Addo"})
public class AddoSigningTemplatesWebScript extends AbstractAddoWebscript {

    @Uri(value = "/api/openesdh/addo/SigningTemplates", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningTemplates(WebScriptRequest req) throws JSONException {
        String[] user = getUserNameAndPassword();
        return WebScriptUtils.jsonResolution(new JSONObject(service.getSigningTemplates(user[0], user[1])));
    }
}
