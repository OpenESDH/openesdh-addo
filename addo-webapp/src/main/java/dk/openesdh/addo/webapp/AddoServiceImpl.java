package dk.openesdh.addo.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import dk.openesdh.addo.common.AddoService;
import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.addo.webservices.AddoWebService;

@Component("addoService")
@ManagedResource
public class AddoServiceImpl implements AddoService {

    @Autowired
    @Qualifier("AddoWebService")
    private AddoWebService webService;

    @Override
    public String login(String username, String password) throws AddoException {
        return webService.login(username, password);
    }

    @Override
    @ManagedOperation
    public String getSigningTemplates(String username, String password) {
        return webService.getSigningTemplates(username, password);
    }

}
