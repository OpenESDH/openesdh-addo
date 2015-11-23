package dk.openesdh.addo.webservices;

import java.io.*;
import java.net.MalformedURLException;

import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.ws.security.trust.STSClient;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningTemplate;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.GetSigningTemplatesResponse;
import org.json.XML;
import org.tempuri.AddoSigningService;

import dk.vismaaddo.schemas.services.signingservice._2014._09.SigningService;
import javax.xml.bind.*;
import javax.xml.ws.BindingProvider;

public class AddoWebService {

    private AddoSigningService service;
    private STSClient sts;

    public void init() throws MalformedURLException, IOException {
//        System.setProperty("https.protocols", "TLSv1,SSLv3,SSLv2Hello");
        service = new AddoSigningService(
                getClass().getResource("/original/SigningService.wsdl"),//TODO: change to configurable wsdlLocation;
                AddoSigningService.SigningService);
        sts = new STSClient(CXFBusFactory.getDefaultBus());
    }

    public SigningService getPort(String username, String password) {
        SigningService port = service.getSigningService();
        ((BindingProvider) port).getRequestContext().put("ws-security.sts.client", sts);
        if (username != null) {
            ((BindingProvider) port).getRequestContext().put("ws-security.username", username);
            ((BindingProvider) port).getRequestContext().put("ws-security.password", password);
        }
        return port;
    }

    private String login(SigningService port, String username, String password) {
        return port.login(username, password);
    }

    public String login(String username, String password) {
        return login(getPort(username, password), username, password);
    }

    public String getSigningTemplates(String guid) {
        SigningService port = getPort(null, null);
//        String loginToken = login(port, username, password);
        GetSigningTemplatesResponse signingTemplates = port.getSigningTemplates(guid);
        return jaxbToJsonString(signingTemplates.getSigningTemplateItems(), ArrayOfSigningTemplate.class);
    }

    private String jaxbToJsonString(JAXBElement jaxb, Class clazz) {
        try {
            JAXBContext jc = JAXBContext.newInstance(clazz);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            marshaller.marshal(jaxb, out);
            return XML.toJSONObject(out.toString()).toString();
        } catch (JAXBException ex) {
            throw new RuntimeException(ex);
        }
    }
}
