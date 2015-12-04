package dk.openesdh.addo.services;

import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import java.util.List;

public interface AddoService {

    public static final String LOGIN_ERROR = "At least one security token in the message could not be validated.";

    public Boolean tryLogin(String username, String password) throws AddoException;

    public String getSigningTemplates(String username, String password) throws AddoException;

    public String initiateSigning(
            String username,
            String password,
            String signingTemplateId,
            String signingTemplateName,
            List<AddoDocument> documents,
            List<AddoDocument> enclosureDocuments,
            List<AddoRecipient> recipients,
            boolean useSequentialSigning) throws AddoException;

    public String getSigningStatus(String username, String password, String signingToken);
}
