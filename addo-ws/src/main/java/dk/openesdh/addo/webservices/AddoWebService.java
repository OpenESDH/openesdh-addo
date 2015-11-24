package dk.openesdh.addo.webservices;

import java.io.*;
import java.net.MalformedURLException;

import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.ws.security.trust.STSClient;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningTemplate;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfValidationError;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.GetSigningTemplatesResponse;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ValidationError;
import org.json.XML;
import org.tempuri.AddoSigningService;

import dk.vismaaddo.schemas.services.signingservice._2014._09.SigningService;
import dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.InitiateSigningRequest;
import dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningresponse._2014._09.InitiateSigningResponse;
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

    /**
     * use only when already logged-id
     *
     * @return
     */
    private SigningService getPort() {
        SigningService port = service.getSigningService();
        ((BindingProvider) port).getRequestContext().put("ws-security.sts.client", sts);
        return port;
    }

    public SigningService getPort(String username, String password) {
        SigningService port = getPort();
        ((BindingProvider) port).getRequestContext().put("ws-security.username", username);
        ((BindingProvider) port).getRequestContext().put("ws-security.password", password);
        return port;
    }

    public String login(String username, String password) {
        return getPort(username, password).login(username, password);
    }

    public Boolean tryLogin(String username, String password) {
        return getPort(username, password).tryLogin();
    }

    /**
     *
     * @param guid
     * @param requestObject
     * @return signingToken
     */
    public String initiateSigning(String guid, InitiateSigningRequest requestObject) {
        InitiateSigningResponse initiateSigningResponse = getPort().initiateSigning(guid, requestObject, null);
        if (!initiateSigningResponse.getSigningToken().isNil()) {
            return initiateSigningResponse.getSigningToken().getValue();
        }
        ArrayOfValidationError errors = initiateSigningResponse.getValidationData().getValue().getValidationErrors().getValue();
        errors.getValidationError()
                .stream()
                .findFirst()
                .ifPresent(this::throwError);
        throw new RuntimeException("Signing token is missing");
    }

    private void throwError(ValidationError err) {
        throw new RuntimeException(err.getErrorCode() + ": " + err.getMessage());
    }

    public String getSigningTemplates(String guid) {
        SigningService port = getPort();
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
