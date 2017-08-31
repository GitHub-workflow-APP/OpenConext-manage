package mr.control;

import mr.conf.Features;
import mr.exception.EndpointNotAllowed;
import mr.format.EngineBlockFormatter;
import mr.migration.JanusMigration;
import mr.migration.JanusMigrationValidation;
import mr.model.MetaData;
import mr.repository.MetaDataRepository;
import mr.shibboleth.FederatedUser;
import mr.web.PreemptiveAuthenticationHttpComponentsClientHttpRequestFactory;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class SystemController {

    private RestTemplate restTemplate;
    private String pushUser;
    private String pushPassword;
    private String pushUri;
    private JanusMigrationValidation janusMigrationValidation;
    private JanusMigration janusMigration;
    private MetaDataRepository metaDataRepository;

    @Autowired
    public SystemController(JanusMigration janusMigration,
                            JanusMigrationValidation janusMigrationValidation,
                            MetaDataRepository metaDataRepository,
                            @Value("${push.url}") String pushUri,
                            @Value("${push.user}") String user,
                            @Value("${push.password}") String password) throws MalformedURLException {
        this.janusMigration = janusMigration;
        this.janusMigrationValidation = janusMigrationValidation;
        this.metaDataRepository = metaDataRepository;
        this.pushUri = pushUri;
        this.pushUser = user;
        this.pushPassword = password;
        this.restTemplate = new RestTemplate(getRequestFactory());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/client/playground/migrate")
    public List<Map<String, Long>> migrate(FederatedUser federatedUser) {
        if (!federatedUser.featureAllowed(Features.MIGRATION)) {
            throw new EndpointNotAllowed();
        }
        return janusMigration.doMigrate();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/client/playground/pushPreview")
    public Map<String, Map<String, Map<String, Object>>> pushPreview(FederatedUser federatedUser) {
        if (!federatedUser.featureAllowed(Features.PUSH_PREVIEW)) {
            throw new EndpointNotAllowed();
        }

        EngineBlockFormatter formatter = new EngineBlockFormatter();

        Map<String, Map<String, Map<String, Object>>> results =
            new HashMap<>();
        Map<String, Map<String, Object>> connections = new HashMap<>();
        results.put("connections", connections);

        List<MetaData> serviceProviders = metaDataRepository.getMongoTemplate().findAll(MetaData.class, "saml20_sp");
        serviceProviders.forEach(sp ->
            connections.put(sp.getId(), formatter.parseServiceProvider(sp)));

        List<MetaData> identityProviders = metaDataRepository.getMongoTemplate().findAll(MetaData.class, "saml20_idp");
        identityProviders.forEach(idp ->
            connections.put(idp.getId(), formatter.parseIdentityProvider(idp)));
        return results;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/client/playground/push")
    public ResponseEntity<Map> push(FederatedUser federatedUser) {
        if (!federatedUser.featureAllowed(Features.PUSH)) {
            throw new EndpointNotAllowed();
        }
        Map<String, Map<String, Map<String, Object>>> json = this.pushPreview(federatedUser);
        ResponseEntity<String> response = this.restTemplate.postForEntity(pushUri, json, String.class);
        HttpStatus statusCode = response.getStatusCode();

        Map<String ,Object> result = new HashMap<>();
        result.put("status", statusCode);
        result.put("response", response);

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/client/playground/validate")
    public Map<String, Object> validate(FederatedUser federatedUser) {
        if (!federatedUser.featureAllowed(Features.VALIDATION)) {
            throw new EndpointNotAllowed();
        }
        return janusMigrationValidation.validateMigration();
    }

    private ClientHttpRequestFactory getRequestFactory() throws MalformedURLException {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().evictExpiredConnections().evictIdleConnections(10l, TimeUnit.SECONDS);
        BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
        basicCredentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(pushUser, pushPassword));
        httpClientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
        //httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(timeOut).setConnectTimeout(timeOut).setSocketTimeout(timeOut).build());

        CloseableHttpClient httpClient = httpClientBuilder.build();
        return new PreemptiveAuthenticationHttpComponentsClientHttpRequestFactory(httpClient, pushUri);
    }


}
