package manage.hook;

import manage.model.EntityType;
import manage.model.MetaData;
import manage.oidc.Client;
import manage.oidc.OidcClient;
import manage.oidc.OpenIdConnect;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OpenIdConnectHook extends MetaDataHookAdapter {

    public final static String OIDC_CLIENT_KEY = "oidcClient";

    private OpenIdConnect openIdConnect;
    private String oidcAcsLocation;

    public OpenIdConnectHook(OpenIdConnect openIdConnect, String oidcAcsLocation) {
        this.openIdConnect = openIdConnect;
        this.oidcAcsLocation = oidcAcsLocation;
    }

    @Override
    public boolean appliesForMetaData(MetaData metaData) {
        if (EntityType.SP.getType().equals(metaData.getType())) {
            Object o = metaData.metaDataFields().get("coin:oidc_client");
            return (o instanceof String && "1".equals(o)) || (o instanceof Boolean && Boolean.class.cast(o));
        }
        return false;
    }

    @Override
    public MetaData postGet(MetaData metaData) {
        String openIdClientId = translateServiceProviderEntityId((String) metaData.getData().get("entityid"));
        Optional<Client> clientOptional = openIdConnect.getClient(openIdClientId);
        return clientOptional.map(client -> {
            client.setClientSecret(null);
            metaData.getData().put(OIDC_CLIENT_KEY, new OidcClient(client));
            return metaData;
        }).orElse(metaData);
    }

    @Override
    public MetaData prePut(MetaData previous, MetaData newMetaData) {
        if (ignoreForOIDC(newMetaData)) {
            return newMetaData;
        }
        Map<String, Object> clientMap = (Map<String, Object>) newMetaData.getData().get(OIDC_CLIENT_KEY);
        String openIdClientId = translateServiceProviderEntityId((String) previous.getData().get("entityid"));
        Optional<Client> clientOptional = openIdConnect.getClient(openIdClientId);
        if (clientOptional.isPresent()) {
            Client client = clientOptional.get();
            syncClient(newMetaData, clientMap, client);
            openIdConnect.updateClient(client);
        }
        newMetaData.getData().remove(OIDC_CLIENT_KEY);
        return newMetaData;
    }

    @Override
    public MetaData prePost(MetaData metaData) {
        if (ignoreForOIDC(metaData)) {
            return metaData;
        }
        Map<String, Object> clientMap = (Map<String, Object>) metaData.getData().get(OIDC_CLIENT_KEY);
        Client client = new Client();
        syncClient(metaData, clientMap, client);
        openIdConnect.createClient(client);

        metaData.getData().remove(OIDC_CLIENT_KEY);

        metaData.metaDataFields().put("AssertionConsumerService:0:Binding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        metaData.metaDataFields().put("AssertionConsumerService:0:Location", oidcAcsLocation);
        return metaData;
    }

    private boolean ignoreForOIDC(MetaData metaData) {
        Map<String, Object> clientMap = (Map<String, Object>) metaData.getData().get(OIDC_CLIENT_KEY);
        return CollectionUtils.isEmpty(clientMap);
    }

    private void syncClient(MetaData metaData, Map<String, Object> clientMap, Client client) {
        String entityid = (String) metaData.getData().get("entityid");
        client.setClientId(translateServiceProviderEntityId(entityid));
        String name = (String) metaData.metaDataFields().get("name:en");
        if (StringUtils.hasText(name)) {
            client.setClientName(name);
        }
        List<String> redirectUris = List.class.cast(clientMap.get("redirectUris"));
        if (!CollectionUtils.isEmpty(redirectUris)) {
            client.setRedirectUris(new HashSet<>(redirectUris));
            client.setRegisteredRedirectUri(new HashSet<>());
        }
        String grantType = String.class.cast(clientMap.get("grantType"));
        if (StringUtils.hasText(grantType)) {
            client.setGrantTypes(Collections.singleton(grantType));
        }
        String secret = (String) clientMap.get("clientSecret");
        if (StringUtils.hasText(secret)) {
            client.setClientSecret(secret);
        }
    }

    @Override
    public MetaData preDelete(MetaData metaData) {
        String entityid = (String) metaData.getData().get("entityid");
        openIdConnect.deleteClient(translateServiceProviderEntityId(entityid));
        return metaData;
    }

    public static String translateServiceProviderEntityId(String entityId) {
        return entityId.replace("@", "@@").replaceAll(":", "@");
    }

}
