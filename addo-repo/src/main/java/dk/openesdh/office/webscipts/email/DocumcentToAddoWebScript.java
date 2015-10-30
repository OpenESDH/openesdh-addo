package dk.openesdh.office.webscipts.email;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.dynamicextensionsalfresco.webscripts.annotations.HttpMethod;
import com.github.dynamicextensionsalfresco.webscripts.annotations.Uri;
import com.github.dynamicextensionsalfresco.webscripts.annotations.UriVariable;
import com.github.dynamicextensionsalfresco.webscripts.annotations.WebScript;
import com.github.dynamicextensionsalfresco.webscripts.resolutions.Resolution;

import dk.openesdh.addo.AddoWebService;
import dk.openesdh.repo.services.documents.DocumentService;
import dk.openesdh.repo.webscripts.utils.WebScriptUtils;

@Component
@WebScript(description = "Send Document To Visma Addo", families = {"Addo"})
public class DocumcentToAddoWebScript {

    private static final Logger LOG = Logger.getLogger(DocumcentToAddoWebScript.class);

    @Autowired
    private DocumentService documentService;

    @Uri(value = "/api/vismaaddo/{zipCode}", method = HttpMethod.GET, defaultFormat = "json")
    public Resolution createEmailDocument(@UriVariable final String zipCode) throws JSONException, IOException {
        LOG.debug("Saving from email");
        return WebScriptUtils.jsonResolution(new JSONObject(new AddoWebService().getCityWeather(zipCode)));
    }
}
