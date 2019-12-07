package manage.control;

import manage.api.APIUser;
import manage.model.EntityType;
import manage.model.ProviderIdentifier;
import manage.repository.MetaDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static manage.mongo.MongobeeConfiguration.REVISION_POSTFIX;

@RestController
public class StatsController {

    private static final Logger LOG = LoggerFactory.getLogger(StatsController.class);

    private List<String> languages;
    private MetaDataRepository metaDataRepository;

    @Autowired
    public StatsController(MetaDataRepository metaDataRepository,
                           @Value("${product.supported_languages}") String supportedLanguages) {
        this.metaDataRepository = metaDataRepository;
        this.languages = Stream.of(supportedLanguages.split(",")).map(String::trim).collect(toList());
    }

    @GetMapping("/internal/stats/revisions")
    public List<Map> revisions(APIUser apiUser) {
        LOG.info("Revisions request by {}", apiUser.getName());
        Query query = getQueryWithDefaultFields(new Query());

        List<Map> providers = new ArrayList<>();
        Arrays.asList(EntityType.values()).forEach(type -> Arrays.asList(new String[]{type.getType(), type.getType()
                .concat(REVISION_POSTFIX)}).forEach
                (collectionName -> providers.addAll(metaDataRepository.getMongoTemplate().find(query, Map.class,
                        collectionName))));
        this.sortProvidersByEidRevisionNumber(providers);

        LOG.info("Revisions request by {} returning {} providers", apiUser.getName(), providers.size());
        return providers;
    }

    @GetMapping("/internal/stats/uniques/{type}")
    public List<Map> uniques(@PathVariable("type") String type, APIUser apiUser) {
        LOG.info("Uniques request for type {} by {}", type, apiUser.getName());
        List<EntityType> entityTypes = Arrays.asList(EntityType.values());
        Assert.isTrue(entityTypes.stream().anyMatch(entityType -> entityType.getType().equals
                (type)), String.format("Unknown type metadata, allowed are %s", entityTypes.stream().map
                (EntityType::getType).collect(toList())));

        List<Map> result = new ArrayList<>();

        List<Map> providers = metaDataRepository.getMongoTemplate().find(getQueryWithDefaultFields(new Query()), Map
                .class, type);
        providers.forEach(provider -> {
            result.add(provider);

            Set<ProviderIdentifier> subResult = new HashSet<>();
            ProviderIdentifier parent = new ProviderIdentifier(provider);
            subResult.add(parent);

            String parentId = String.class.cast(provider.get("_id"));
            Query query = getQueryWithDefaultFields(new Query(Criteria.where("revision.parentId").is(parentId)).with
                    (new Sort(Sort.Direction.ASC, "revision.number")));
            List<Map> revisions = metaDataRepository.getMongoTemplate().find(query, Map.class, type.concat
                    (REVISION_POSTFIX));

            //Traditional for loop to set the end date
            for (int i = 0; i < revisions.size(); i++) {
                Map revision = revisions.get(i);
                Object ended = (revisions.size() == i + 1) ? revision(provider).get("created") :
                        revision(revisions.get(i + 1)).get("created");
                revision(revision).put("ended", ended);

                ProviderIdentifier providerIdentifier = new ProviderIdentifier(revision);
                if (subResult.add(providerIdentifier)) {
                    result.add(revision);
                }
            }
        });
        this.sortProvidersByEidRevisionNumber(result);

        LOG.info("Uniques request for type {} returning {} providers", type, result.size());
        return result;
    }

    @GetMapping("/internal/stats/connections")
    public List<Map> connections(APIUser apiUser, @RequestParam("from") Optional<String> from) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = from.map(fromDateString -> {
            try {
                return simpleDateFormat.parse(fromDateString);
            } catch (ParseException e) {
                throw new IllegalArgumentException(String.format("Illegal date format %s, required format %s",
                        fromDateString, "yyyy-MM-dd"));
            }
        }).orElse(new Date());
        String dateString = simpleDateFormat.format(date);

        LOG.info("Connections request for {} by {}", dateString, apiUser.getName());

        List<Map> result = new ArrayList<>();
        List<Map> uniqueIdentityProviders = this.filterByCreate(date, this.uniques(EntityType.IDP.getType(), apiUser));
        List<Map> serviceProviders = this.filterByCreate(date, this.uniques(EntityType.SP.getType(), apiUser));

        uniqueIdentityProviders.forEach(idp -> {
            Map idpRevision = Map.class.cast(idp.get("revision"));
            Long idpCreated = Date.class.cast(idpRevision.get("created")).getTime();
            Long idpTerminated = this.getTime(idpRevision, "terminated");
            Long idpEnded = this.getTime(idpRevision, "ended");

            Map idpData = Map.class.cast(idp.get("data"));
            Boolean allowedAll = Boolean.class.cast(idpData.get("allowedall"));
            List<Map> spCandidates;
            if (allowedAll) {
                spCandidates = serviceProviders;
            } else {
                List<String> allowedEntities = (List<String>) List.class.cast(idpData.getOrDefault("allowedEntities",
                        new ArrayList<>())).stream()
                        .map(m -> Map.class.cast(m).get("name"))
                        .collect(toList());

                spCandidates = serviceProviders.stream().filter(sp -> allowedEntities.contains(Map.class.cast(sp.get
                        ("data")).get("entityid"))).collect(toList());
            }
            List<Map> connectedServiceProviders = spCandidates.stream().filter(sp -> {
                /**
                 * The SP may not be ended / terminated before the create date of the IdP and if the IdP is ended or
                 * terminated then the SP may noy be created afterwards
                 */
                Map spRevision = Map.class.cast(sp.get("revision"));
                Long spCreated = Date.class.cast(spRevision.get("created")).getTime();
                Long spTerminated = this.getTime(idpRevision, "terminated");
                Long spEnded = this.getTime(idpRevision, "ended");
                Map spData = Map.class.cast(sp.get("data"));
                Boolean spAllowedAll = Boolean.class.cast(spData.get("allowedall"));
                List<String> allowedEntities = (List<String>) List.class.cast(spData.getOrDefault("allowedEntities",
                        new ArrayList()))
                        .stream().map(m -> Map.class.cast(m).get("name"))
                        .collect(toList());
                boolean whiteListed = allowedEntities.contains(idpData.get("entityid"));
                return (spAllowedAll || whiteListed) &&
                        (spData.get("state").equals(idpData.get("state"))) &&
                        (spTerminated == null || spTerminated > idpCreated) &&
                        (spEnded == null || spEnded > idpCreated) &&
                        (idpEnded == null || idpEnded > spCreated) &&
                        (idpTerminated == null || idpTerminated > spCreated);
            }).map(sp -> {
                Map<String, String> subResult = new ProviderIdentifier(idp).toMap(EntityType.IDP, Optional.empty());
                Map entry = new ProviderIdentifier(sp).toMap(EntityType.SP, Optional.of(subResult));
                entry.put("date", dateString);
                return entry;
            }).collect(toList());
            result.addAll(connectedServiceProviders);
        });

        LOG.info("Connections request for {} by {} returning {} connections", dateString, apiUser.getName(), result
                .size());
        return result;
    }

    private List<Map> filterByCreate(Date date, List<Map> providers) {
        long millis = date.getTime();
        return providers.stream().filter(provider -> Date.class.cast(Map.class.cast(provider.get("revision")).get
                ("created")).getTime() < millis)
                .collect(toList());
    }

    private Long getTime(Map revision, String name) {
        Date date = Date.class.cast(revision.get(name));
        return date != null ? date.getTime() : null;
    }

    @GetMapping("/internal/stats/new_providers")
    public List<Map> newProviders(APIUser apiUser) {
        LOG.info("Providers without eid request by {}", apiUser.getName());
        Query query = new Query();
        query.addCriteria(Criteria.where("data.eid").exists(false));
        query.fields().include("_id").include("revision.parentId").include("type").include("data.entityid");

        List<Map> providers = new ArrayList<>();
        Arrays.asList(EntityType.values()).forEach(type -> Arrays.asList(new String[]{type.getType(), type.getType()
                .concat(REVISION_POSTFIX)}).forEach
                (collectionName -> providers.addAll(metaDataRepository.getMongoTemplate().find(query, Map.class,
                        collectionName))));

        LOG.info("Providers without eid request by {} returning {} providers", apiUser.getName(), providers.size());
        return providers;
    }

    @GetMapping("/internal/stats/none_allowed")
    public List<Map> noneAllowed(APIUser apiUser) {
        LOG.info("Providers without any connections request by {}", apiUser.getName());
        Query query = new Query();
        query.addCriteria(Criteria.where("data.allowedall").is(false));
        query.addCriteria((new Criteria().orOperator(Criteria.where("data.allowedEntities").exists(false), Criteria
                .where("data.allowedEntities").size(0))));
        query.fields()
                .include("_id")
                .include("revision.parentId")
                .include("type")
                .include("data.entityid")
                .include("data.allowedall")
                .include("data.allowedEntities");

        List<Map> providers = new ArrayList<>();
        Arrays.asList(EntityType.values()).forEach(type -> Arrays.asList(new String[]{type.getType(), type.getType()
                .concat(REVISION_POSTFIX)}).forEach
                (collectionName -> providers.addAll(metaDataRepository.getMongoTemplate().find(query, Map.class,
                        collectionName))));

        LOG.info("Providers without any connections request by {} returning {} providers", apiUser.getName(),
                providers.size());
        return providers;
    }

    private Map revision(Map provider) {
        return Map.class.cast(provider.get("revision"));
    }

    private Query getQueryWithDefaultFields(Query query) {
        query
                .fields()
                .include("type")
                .include("revision.number")
                .include("revision.parentId")
                .include("revision.created")
                .include("revision.terminated")
                .include("data.eid")
                .include("data.state")
                .include("data.entityid")
                .include("data.allowedall")
                .include("data.allowedEntities")
                .include("data.metaDataFields.coin:institution_id");
        this.languages.forEach(language -> query.fields().include("data.metaDataFields.name" + language));

        return query;
    }

    private void sortProvidersByEidRevisionNumber(List<Map> providers) {
        Collections.sort(providers, (Map m1, Map m2) -> {
            Long eid1 = metadataId(m1, "data", "eid");
            Long eid2 = metadataId(m2, "data", "eid");
            if (!eid1.equals(eid2)) {
                return eid1.compareTo(eid2);
            }
            Long number1 = metadataId(m1, "revision", "number");
            Long number2 = metadataId(m2, "revision", "number");
            return number1.compareTo(number2);
        });
    }

    private Long metadataId(Map map, String parent, String identifier) {
        Object id = Map.class.cast(map.get(parent)).get(identifier);
        if (id instanceof Integer) {
            return Long.valueOf(Integer.class.cast(id));
        }
        if (id instanceof Long) {
            return Long.valueOf(Long.class.cast(id));
        }
        if (id == null) {
            throw new RuntimeException(String.format("Identifier %s does not exists in Map %s", identifier, map));
        }
        throw new RuntimeException(String.format("Identifier %s exists in Map %s but is of type %s", identifier, map,
                id.getClass().getName()));
    }

}
