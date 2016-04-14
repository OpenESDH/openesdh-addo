package dk.openesdh.addo.services;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dk.openesdh.addo.model.AddoPasswordEncoder;
import dk.openesdh.addo.models.AddoModel;
import dk.openesdh.repo.exceptions.DomainException;
import dk.openesdh.repo.services.authorities.UserSavingContext;
import dk.openesdh.repo.services.authorities.UsersService;

@Component
public class AddoUserServiceExtensionRegister {

    @Autowired
    @Qualifier("UsersService")
    private UsersService userService;
    @Autowired
    private AddoService addoService;
    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;

    @PostConstruct
    public void init() {
        userService.registerUserJsonDecorator(getDecorator());
        userService.registerUserValidator(getValidator());
        userService.registerBeforeSaveAction(getBeforeSaveAction());
        userService.registerAfterSaveAction(getAfterSaveAction());
    }

    private Consumer<JSONObject> getDecorator() {
        return json -> {
            if (json.containsKey(AddoModel.ADDO_PREFIX)) {
                JSONObject addoNs = (JSONObject) json.get(AddoModel.ADDO_PREFIX);
                addoNs.remove(AddoModel.PROP_ADDO_PASSWORD.getLocalName());
            } else {
                json.put(AddoModel.ADDO_PREFIX, new JSONObject());
            }
        };
    }

    private boolean isAddoCredentialsChanged(Map<QName, Serializable> props) {
        if (props.containsKey(AddoModel.PROP_ADDO_USERNAME) || props.containsKey(AddoModel.PROP_ADDO_PASSWORD)) {
            return StringUtils.isNotEmpty((String) props.get(AddoModel.PROP_ADDO_PASSWORD));
        }
        return false;
    }

    private Consumer<UserSavingContext> getValidator() {
        return context -> {
            if (isAddoCredentialsChanged(context.getProps())) {
                String addoUsername = (String) context.getProps().get(AddoModel.PROP_ADDO_USERNAME);
                String password = AddoPasswordEncoder.encode((String) context.getProps().get(AddoModel.PROP_ADDO_PASSWORD));
                if (!addoService.tryLogin(addoUsername, password)) {
                    throw new DomainException("ADDO.USER.INCORECT_PASSWORD")
                            .forField("addoUsername");
                }
            }
        };
    }

    private Consumer<UserSavingContext> getBeforeSaveAction() {
        return context -> {
            //do not save addo user creadiantials
            context.getProps().remove(AddoModel.PROP_ADDO_USERNAME);
            context.getProps().remove(AddoModel.PROP_ADDO_PASSWORD);
        };
    }

    private Consumer<UserSavingContext> getAfterSaveAction() {
        return context -> {
            if (isAddoCredentialsChanged(context.getCopiedProps())) {
                Map<QName, Serializable> props = new HashMap<>();
                props.put(AddoModel.PROP_ADDO_USERNAME,
                        context.getCopiedProps().get(AddoModel.PROP_ADDO_USERNAME));
                props.put(AddoModel.PROP_ADDO_PASSWORD,
                        AddoPasswordEncoder.encode((String) context.getCopiedProps().get(AddoModel.PROP_ADDO_PASSWORD)));
                nodeService.addAspect(context.getNodeRef(), AddoModel.ASPECT_ADDO_CREDENTIALS, props);
            }
        };
    }
}
