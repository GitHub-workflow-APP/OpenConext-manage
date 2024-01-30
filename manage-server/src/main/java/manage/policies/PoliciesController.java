package manage.policies;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import manage.api.APIUser;
import manage.control.MetaDataController;
import manage.model.EntityType;
import manage.model.MetaData;
import manage.service.MetaDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
public class PoliciesController {

    private static final Logger LOG = LoggerFactory.getLogger(MetaDataController.class);

    private final MetaDataService metaDataService;
    private final ObjectMapper objectMapper;
    private final PolicyIdpAccessEnforcer policyIdpAccessEnforcer;
    private final List<Map<String, String>> allowedAttributes;
    private final List<Map<String, String>> samlAllowedAttributes;
    private final TypeReference<List<Map<String, String>>> typeReference = new TypeReference<>() {
    };

    @SneakyThrows
    public PoliciesController(MetaDataService metaDataService,
                              ObjectMapper objectMapper,
                              PolicyIdpAccessEnforcer policyIdpAccessEnforcer) {
        this.metaDataService = metaDataService;
        this.objectMapper = objectMapper;
        this.policyIdpAccessEnforcer = policyIdpAccessEnforcer;
        this.allowedAttributes = this.attributes("policies/allowed_attributes.json");
        this.samlAllowedAttributes = this.attributes("policies/extra_saml_attributes.json");
    }

    private List<Map<String, String>> attributes(String path) throws IOException {
        return this.objectMapper.readValue(new ClassPathResource(path).getInputStream(), typeReference);
    }

    @PreAuthorize("hasRole('READ')")
    @GetMapping("/internal/protected/policies")
    public List<PdpPolicyDefinition> policies(APIUser apiUser) {
        List<MetaData> policies = this.metaDataService.findAllByType(EntityType.PDP.getType());

        return policies.stream()
                .filter()
                .map(policy -> enrichPolicyDefinition(new PdpPolicyDefinition(policy)))
                .collect(toList());
    }

    @PreAuthorize("hasRole('READ')")
    @GetMapping("/internal/protected/policies/{id}")
    public PdpPolicyDefinition policies(@PathVariable("id") String id) {
        return enrichPolicyDefinition(new PdpPolicyDefinition(this.metaDataService.getMetaDataAndValidate(EntityType.PDP.getType(), id)));
    }

    @PreAuthorize("hasRole('READ')")
    @PostMapping("/internal/protected/policies/{id}")
    public PdpPolicyDefinition create(APIUser apiUser, @RequestBody PdpPolicyDefinition policyDefinition) {
        Map<String, Object> data = new HashMap<>();

        MetaData metaData = new MetaData(EntityType.PDP.getType(), data);
        return this.metaDataService.doPost();
    }

    @PreAuthorize("hasRole('READ')")
    @RequestMapping(method = GET, value = {"/client/attributes", "/internal/protected/attributes"})
    public List<Map<String, String>> getAllowedAttributes() {
        return this.allowedAttributes;
    }

    @RequestMapping(method = GET, value = {"/client/saml-attributes", "/internal/protected/saml-attributes"})
    public List<Map<String, String>> getAllowedSamlAttributes() {
        return this.samlAllowedAttributes;
    }

    private PdpPolicyDefinition enrichPolicyDefinition(PdpPolicyDefinition policyDefinition) {
        List<String> serviceProviderIds = policyDefinition.getServiceProviderIds();
        if (!CollectionUtils.isEmpty(serviceProviderIds)) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("entityid", serviceProviderIds);
            List<Map> serviceProviders = metaDataService.searchEntityByType(EntityType.SP.getType(), properties, false);
            //we might be missing some RP's
            if (serviceProviders.size() < serviceProviderIds.size()) {
                List<String> allEntityIDs = serviceProviders.stream().map(entity -> entityID(entity)).collect(toList());
                List<String> rpProviderIds = serviceProviderIds.stream().filter(id -> !allEntityIDs.contains(id)).collect(toList());
                properties.put("entityid", rpProviderIds);
                List<Map> relyingParties = metaDataService.searchEntityByType(EntityType.RP.getType(), properties, false);
                serviceProviders.addAll(relyingParties);
            }
            policyDefinition.setServiceProviderNames(serviceProviders.stream().map(sp -> name(sp)).collect(toList()));
            policyDefinition.setServiceProviderNamesNl(serviceProviders.stream().map(sp -> nameNL(sp)).collect(toList()));
        }
        List<String> identityProviderIds = policyDefinition.getIdentityProviderIds();
        if (!CollectionUtils.isEmpty(identityProviderIds)) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("entityid", identityProviderIds);
            List<Map> identityProviders = metaDataService.searchEntityByType(EntityType.IDP.getType(), properties, false);
            policyDefinition.setIdentityProviderNames(identityProviders.stream().map(idp -> name(idp)).collect(toList()));
            policyDefinition.setServiceProviderNamesNl(identityProviders.stream().map(idp -> nameNL(idp)).collect(toList()));
        }
        if (policyDefinition.getType().equals("step")) {
            policyDefinition.getLoas().forEach(loa -> loa.getCidrNotations()
                    .forEach(notation -> notation.setIpInfo(IPAddressProvider.getIpInfo(notation.getIpAddress(), notation.getPrefix()))));
        }
        return policyDefinition;
    }

    @SuppressWarnings("unchecked")
    private String entityID(Map<String, Object> entity) {
        return (String) ((Map)entity.get("data")).get("entity");
    }

    @SuppressWarnings("unchecked")
    private String name(Map<String, Object> entity) {
        return (String) ((Map)((Map)entity.get("data")).get("metaDataFields")).get("name:en");
    }

    @SuppressWarnings("unchecked")
    private String nameNL(Map<String, Object> entity) {
        return (String) ((Map)((Map)entity.get("data")).get("metaDataFields")).get("name:nl");
    }
}
