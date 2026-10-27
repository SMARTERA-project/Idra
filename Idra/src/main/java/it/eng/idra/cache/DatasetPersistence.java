package it.eng.idra.cache;

import com.google.gson.*;
import it.eng.idra.beans.DistributionAdditionalConfiguration;
import it.eng.idra.beans.orion.OrionDistributionConfig;
import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.dcat.DcatDatasetId;
import it.eng.idra.beans.dcat.DcatProperty;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityManager;
import javax.persistence.EntityExistsException;
import org.apache.jena.rdf.model.ResourceFactory;

/** One dataset is the atomic unit. External effects belong to the caller after commit. */
public final class DatasetPersistence {
  private final EntityManagerFactory factory;
  // Internal snapshot only: include transient catalogue IDs, never change the public Gson format.
  private static final Gson configSnapshot = new GsonBuilder()
      .excludeFieldsWithModifiers(Modifier.STATIC)
      .registerTypeAdapter(DcatProperty.class, new PropertySnapshot()).create();
  private static final Gson snapshot = new GsonBuilder()
      .excludeFieldsWithModifiers(Modifier.STATIC)
      .registerTypeAdapter(DcatProperty.class, new PropertySnapshot())
      .registerTypeAdapter(DistributionAdditionalConfiguration.class, new ConfigurationSnapshot()).create();

  public DatasetPersistence(EntityManagerFactory factory) { this.factory = factory; }

  public DcatDataset insert(DcatDataset dataset) { return write(dataset, false); }
  public DcatDataset replace(DcatDataset dataset) { return write(dataset, true); }

  /** Initial import page: prepare IDs/URLs in the DB context, without external effects. */
  public List<DcatDataset> insertPage(List<DcatDataset> datasets, Consumer<DcatDataset> prepare) {
    List<String> pristine = new ArrayList<>();
    for (DcatDataset dataset : datasets) pristine.add(snapshot.toJson(dataset));
    return DatabaseTransaction.execute(factory, em -> {
      List<DcatDataset> committed = new ArrayList<>();
      java.util.Set<java.util.Map.Entry<String,String>> identities = new java.util.HashSet<>();
      // Check the whole page while the persistence context is still clean. Interleaving
      // identity queries and persist causes Hibernate AUTO flush to repeatedly scan
      // every previously inserted dataset and its children on large RDF imports.
      for (String json : pristine) {
        DcatDataset attempt = snapshot.fromJson(json, DcatDataset.class);
        if (!identities.add(new java.util.AbstractMap.SimpleImmutableEntry<>(
            attempt.getNodeId(), attempt.getIdentifier().getValue()))) {
          throw new EntityExistsException("Duplicate dataset identity in the import page");
        }
        requireAbsentIdentity(em, attempt);
        committed.add(attempt);
      }
      for (DcatDataset attempt : committed) {
        em.persist(attempt);
        prepare.accept(attempt);
      }
      em.flush();
      return committed;
    });
  }

  private DcatDataset write(DcatDataset dataset, boolean replace) {
    // Capture pristine connector data before Hibernate assigns child IDs or wraps collections.
    String pristine = snapshot.toJson(dataset);
    return DatabaseTransaction.execute(factory, em -> {
      DcatDataset attempt = snapshot.fromJson(pristine, DcatDataset.class);
      if (attempt.getInSeries() != null) {
        for (it.eng.idra.beans.dcat.DcatDatasetSeries series : attempt.getInSeries()) {
          series.setDatasetId(attempt.getId());
          series.setNodeId(attempt.getNodeId());
        }
      }
      if (replace) {
        DcatDataset old = em.find(DcatDataset.class,
            new DcatDatasetId(attempt.getId(), attempt.getNodeId()));
        if (old == null) throw new IllegalStateException("Dataset disappeared before replacement");
        em.remove(old);
        // Flush deletes before inserting the same composite PK; neither flush nor clear commits.
        em.flush();
        em.clear();
      } else {
        requireAbsentIdentity(em, attempt);
      }
      em.persist(attempt);
      em.flush();
      return attempt;
    });
  }

  public void delete(DcatDataset dataset) {
    DatabaseTransaction.execute(factory, em -> {
      DcatDataset old = em.find(DcatDataset.class,
          new DcatDatasetId(dataset.getId(), dataset.getNodeId()));
      if (old != null) em.remove(old);
      return null;
    });
  }

  private static void requireAbsentIdentity(EntityManager em, DcatDataset dataset) {
    long count = em.createQuery("SELECT COUNT(d) FROM DcatDataset d WHERE d.nodeId=:node"
        + " AND d.identifier.value=:identifier", Long.class)
        .setParameter("node", dataset.getNodeId())
        .setParameter("identifier", dataset.getIdentifier().getValue()).getSingleResult();
    if (count != 0) {
      // A missing/stale search document must never duplicate an already persisted dataset.
      throw new EntityExistsException("Dataset identity already exists in this catalogue; reconcile the index");
    }
  }

  private static final class ConfigurationSnapshot implements
      JsonSerializer<DistributionAdditionalConfiguration>, JsonDeserializer<DistributionAdditionalConfiguration> {
    public JsonElement serialize(DistributionAdditionalConfiguration config, Type type, JsonSerializationContext context) {
      return configSnapshot.toJsonTree(config, config instanceof OrionDistributionConfig
          ? OrionDistributionConfig.class : DistributionAdditionalConfiguration.class);
    }
    public DistributionAdditionalConfiguration deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
      JsonElement kind = json.getAsJsonObject().get("type");
      Class<? extends DistributionAdditionalConfiguration> configType =
          kind != null && !kind.isJsonNull() && "ORION".equals(kind.getAsString())
              ? OrionDistributionConfig.class : DistributionAdditionalConfiguration.class;
      return configSnapshot.fromJson(json, configType);
    }
  }

  private static final class PropertySnapshot
      implements JsonSerializer<DcatProperty>, JsonDeserializer<DcatProperty> {
    public JsonElement serialize(DcatProperty property, Type type, JsonSerializationContext context) {
      JsonObject obj = new JsonObject();
      obj.addProperty("uri", property.getUri());
      obj.addProperty("value", property.getValue());
      if (property.getProperty() != null) obj.addProperty("property", property.getProperty().getURI());
      if (property.getRange() != null) obj.addProperty("range", property.getRange().getURI());
      return obj;
    }
    public DcatProperty deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
      JsonObject obj = element.getAsJsonObject();
      DcatProperty property = new DcatProperty();
      if (obj.has("uri")) property.setUri(obj.get("uri").getAsString());
      if (obj.has("value")) property.setValue(obj.get("value").getAsString());
      if (obj.has("property")) property.setProperty(ResourceFactory.createProperty(obj.get("property").getAsString()));
      if (obj.has("range")) property.setRange(ResourceFactory.createResource(obj.get("range").getAsString()));
      return property;
    }
  }
}
