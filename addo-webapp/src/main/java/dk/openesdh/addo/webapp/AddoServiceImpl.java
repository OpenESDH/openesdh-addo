package dk.openesdh.addo.webapp;

import java.util.Base64;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningDocument;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningRecipientData;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.Signing;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningDocument;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningInboundEnclosuresCollection;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningRecipientData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.InitiateSigningRequest;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import dk.openesdh.addo.common.AddoDocument;
import dk.openesdh.addo.common.AddoGUID;
import dk.openesdh.addo.common.AddoRecipient;
import dk.openesdh.addo.common.AddoService;
import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.addo.webservices.AddoWebService;

@Component("addoService")
public class AddoServiceImpl implements AddoService {

    @Autowired
    @Qualifier("AddoWebService")
    private AddoWebService webService;

    @Override
    public AddoGUID login(String username, String password) throws AddoException {
        return new AddoGUID(username, password)
                .setToken(webService.login(username, password));
    }

    @Override
    public Boolean tryLogin(String username, String password) {
        return webService.tryLogin(username, password);
    }

    private AddoGUID validateGUID(AddoGUID guid) {
        if (!guid.isValid()) {
            guid.setToken(webService.login(guid.username, guid.password));
        }
        return guid;
    }

    @Override
    public String getSigningTemplates(AddoGUID guid) {
        return webService.getSigningTemplates(validateGUID(guid).getToken());
    }

    @Override
    public String initiateSigning(AddoGUID guid, String signingTemplateId, String signingTemplateName,
            AddoDocument addoDocument, List<AddoRecipient> addoRecipients) throws AddoException {
        dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.ObjectFactory factory
                = new dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.ObjectFactory();

        org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ObjectFactory signingFactory
                = new org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ObjectFactory();

        /**
         * SigningDocument
         */
        SigningDocument signingDocument = signingFactory.createSigningDocument();
        signingDocument.setData(signingFactory.createSigningDocumentData(Base64.getEncoder().encodeToString(addoDocument.getData())));
        //signingDocument.setId("");
        signingDocument.setIsShared(Boolean.FALSE);
        signingDocument.setMimeType(signingFactory.createSigningDocumentMimeType(addoDocument.getMimeType()));
        signingDocument.setName(signingFactory.createSigningDocumentName(addoDocument.getName()));

        /**
         * Documents
         */
        ArrayOfSigningDocument documents = signingFactory.createArrayOfSigningDocument();
        documents.getSigningDocument().add(signingDocument);

        /**
         * EnclosureDocuments
         */
        ArrayOfSigningDocument enclosureDocuments = signingFactory.createArrayOfSigningDocument();

        /**
         * InboundEnclosures
         */
        SigningInboundEnclosuresCollection inboundEnclosures = signingFactory.createSigningInboundEnclosuresCollection();
        inboundEnclosures.setAllowAll(Boolean.FALSE);
        inboundEnclosures.setDocumentReferences(
                signingFactory.createSigningInboundEnclosuresCollectionDocumentReferences(
                        signingFactory.createArrayOfSigningDocumentInboundEnclosureReference()));

        /**
         * Recipients
         */
        ArrayOfSigningRecipientData recipients = signingFactory.createArrayOfSigningRecipientData();
        for (AddoRecipient rec : addoRecipients) {
            /**
             * RecipientData
             */
            SigningRecipientData recipientData = signingFactory.createSigningRecipientData();
            recipientData.setAddress(signingFactory.createSigningRecipientDataAddress(rec.getAddress()));
            recipientData.setCpr(signingFactory.createSigningRecipientDataCpr(rec.getCpr()));
            recipientData.setEmail(signingFactory.createSigningRecipientDataEmail(rec.getEmail()));
            recipientData.setName(signingFactory.createSigningRecipientDataName(rec.getName()));
            recipientData.setPhone(signingFactory.createSigningRecipientDataPhone(rec.getPhone()));
            recipientData.setSignedDate(signingFactory.createSigningRecipientDataSignedDate(null));
            recipientData.setTemplateData(signingFactory.createSigningRecipientDataTemplateData(null));

            recipients.getSigningRecipientData().add(recipientData);
        }

        /**
         * Signing
         */
        Signing signingObject = signingFactory.createSigning();
        signingObject.setDocuments(signingFactory.createSigningDocuments(documents));
        signingObject.setEnclosureDocuments(signingFactory.createSigningEnclosureDocuments(enclosureDocuments));
        signingObject.setInboundEnclosures(signingFactory.createSigningInboundEnclosures(inboundEnclosures));
        signingObject.setRecipients(signingFactory.createSigningRecipients(recipients));

        InitiateSigningRequest requestObject = factory.createInitiateSigningRequest();
        requestObject.setName(factory.createInitiateSigningRequestName(signingTemplateName));
        requestObject.setSigningData(factory.createInitiateSigningRequestSigningData(signingObject));
        requestObject.setSigningTemplateId(signingTemplateId);
        requestObject.setStartDate(gregorianCalendarOf(new Date()));

        return webService.initiateSigning(
                validateGUID(guid).getToken(),
                requestObject);
    }

    private XMLGregorianCalendar gregorianCalendarOf(Date date) {
        try {
            GregorianCalendar c = new GregorianCalendar();
            c.setTime(date);
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
        } catch (DatatypeConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

}
