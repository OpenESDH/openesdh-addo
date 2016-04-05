package dk.openesdh.addo.webscipts;

import org.alfresco.util.Pair;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.repo.exceptions.DomainException;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Returns Visma Addo Signing Templates for current user", families = {"Addo"})
public class AddoSigningTemplatesWebScript extends AbstractAddoWebscript {

    @Uri(value = "/api/openesdh/addo/SigningTemplates", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution getSigningTemplates(WebScriptRequest req) throws JSONException {
        Pair<String, String> user = getUserNameAndPassword();
        try {
            return WebScriptUtils.jsonResolution(new JSONObject(
                    service.getSigningTemplates(user.getFirst(), user.getSecond())
            ));
        } catch (AddoException ex) {
            if (ex.isDomainException()) {
                throw new DomainException(ex.getMessage());
            }
            throw new RuntimeException(ex);
        }
    }
}
