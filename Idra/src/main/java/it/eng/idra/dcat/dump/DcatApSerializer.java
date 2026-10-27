/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2025 Engineering Ingegneria Informatica S.p.A.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/

package it.eng.idra.dcat.dump;

import it.eng.idra.beans.IdraProperty;
import it.eng.idra.beans.dcat.DCATAP;
import it.eng.idra.beans.dcat.DcatApFormat;
import it.eng.idra.beans.dcat.DcatApProfile;
import it.eng.idra.beans.dcat.DcatApWriteType;
import it.eng.idra.beans.dcat.DcatDataService;
import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.dcat.DcatDistribution;
import it.eng.idra.beans.dcat.DcatKeyword;
import it.eng.idra.beans.dcat.DcatProperty;
import it.eng.idra.beans.dcat.DctFrequency;
import it.eng.idra.beans.dcat.DctLicenseDocument;
import it.eng.idra.beans.dcat.DctLocation;
import it.eng.idra.beans.dcat.DctPeriodOfTime;
import it.eng.idra.beans.dcat.DctStandard;
import it.eng.idra.beans.dcat.ELI;
import it.eng.idra.beans.dcat.FoafAgent;
import it.eng.idra.beans.dcat.Relationship;
import it.eng.idra.beans.dcat.SkosConcept;
import it.eng.idra.beans.dcat.SkosPrefLabel;
import it.eng.idra.beans.dcat.SpdxChecksum;
import it.eng.idra.beans.dcat.VcardOrganization;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.beans.search.SearchResult;
import it.eng.idra.management.FederationCore;
import it.eng.idra.utils.PropertyManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.XSDhexBinary;
import org.apache.jena.datatypes.xsd.impl.XSDDateType;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.ResourceRequiredException;
//import org.apache.jena.shared.BadURIException;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.VCARD4;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.apache.jena.rdf.model.ResourceFactory;

// TODO: Auto-generated Javadoc
/**
 * The Class DcatApSerializer.
 */
public class DcatApSerializer {

  /** The node resources. */
  static Map<Integer, OdmsCatalogue> nodeResources = null;

  /** The Constant iriFactory. */
  @SuppressWarnings("deprecation")
  protected static final IRIFactory iriFactory = IRIFactory.jenaImplementation();

  /** The Constant DCATAP_IT_BASE_URI. */
  public static final String DCATAP_IT_BASE_URI = "http://dati.gov.it/onto/dcatapit#";

  /** The Constant THEME_BASE_URI. */
  public static final String THEME_BASE_URI = "http://publications.europa.eu/resource/authority/data-theme/";

  /** The Constant SUBJECT_BASE_URI. */
  public static final String SUBJECT_BASE_URI = "http://eurovoc.europa.eu/";

  /** The Constant FREQUENCY_BASE_URI. */
  public static final String FREQUENCY_BASE_URI = "http://publications.europa.eu/resource/authority/frequency/";

  /** The Constant LANGUAGE_BASE_URI. */
  public static final String LANGUAGE_BASE_URI = "http://publications.europa.eu/resource/authority/language/";

  /** The Constant GEO_BASE_URI. */
  public static final String GEO_BASE_URI = "http://publications.europa.eu/mdr/authority/place/";

  /** The Constant GEO_BASE_URI_ALT. */
  public static final String GEO_BASE_URI_ALT = "http://geonames.org/";

  /** The Constant FORMAT_BASE_URI. */
  public static final String FORMAT_BASE_URI = "http://publications.europa.eu/resource/authority/file-type/";

  /** The Constant FORMAT_BASE_URI. */
  public static final String MEDIATYPE_BASE_URI = "https://www.iana.org/assignments/media-types/";

  /** The Constant LICENSE_TYPE_BASE_URI. */
  public static final String LICENSE_TYPE_BASE_URI = "http://purl.org/adms/licencetype/";

  /** Concepts of the ADMS licence type vocabulary (http://purl.org/adms/licencetype/). */
  static final java.util.Set<String> ADMS_LICENCE_TYPES = new java.util.HashSet<>(Arrays.asList(
      "Attribution", "GrantBack", "JurisdictionWithinTheEU", "KnownPatentEncumbrance",
      "NoDerivativeWork", "NominalCost", "NonCommercialUseOnly", "OtherRestrictiveClauses",
      "PublicDomain", "ReservedNames-Endorsement-Trademark", "RoyaltiesRequired", "UnknownIPR",
      "ViralEffect-ShareAlike"));

  /**
   * Licence type as an IRI: an http(s) IRI as it is, an ADMS licence type code in its
   * vocabulary. Anything else (for example a portal licence code such as "cc-by") is not a
   * licence type and is not exported.
   *
   * @param value the stored licence type
   * @return the licence type IRI, or null
   */
  static String licenceTypeUri(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String type = value.trim();
    if (isValidUri(type)) {
      return type;
    }
    return ADMS_LICENCE_TYPES.contains(type) ? LICENSE_TYPE_BASE_URI + type : null;
  }

  private static boolean isBlank(DcatProperty property) {
    return property == null || StringUtils.isBlank(property.getValue());
  }

  /** The logger. */
  protected static Logger logger = LogManager.getLogger(DcatApSerializer.class);

  /** The file path. */
  private static String filePath = PropertyManager.getProperty(IdraProperty.DUMP_FILE_PATH);

  /** The file name. */
  private static String fileName = PropertyManager.getProperty(IdraProperty.DUMP_FILE_NAME);

  static {

  }

  /**
   * Instantiates a new dcat ap serializer.
   */
  public DcatApSerializer() {
  }

  /**
   * Dataset to model.
   *
   * @param dataset the dataset
   * @param profile the profile
   * @return the model
   */
  private static Model datasetToModel(DcatDataset dataset, DcatApProfile profile) {

    Model model = initializeModel();

    try {
      switch (profile) {

        case DCATAP_IT:
          model.setNsPrefix("dcatapit", "http://dati.gov.it/onto/dcatapit#");
          model = DcatApItSerializer.addDatasetToModel(dataset, model);
          break;

        /*
         * ADD HERE MORE CASES FOR FURTHER PROFILES
         */

        default:
          model = addDatasetToModel(dataset, model);
          break;
      }

    } catch (Exception e) {
      logger.error("Error while converting dataset " + dataset.getId() + " to model, reason: "
          + e.getMessage());
    }

    return model;
  }

  /**
   * initializeModel.
   *
   * @return the model
   */
  private static Model initializeModel() {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefix("dct", DCTerms.getURI());
    model.setNsPrefix("dcat", DCAT.getURI());
    model.setNsPrefix("adms", "http://www.w3.org/ns/adms#");
    model.setNsPrefix("foaf", FOAF.getURI());
    model.setNsPrefix("owl", OWL.getURI());
    model.setNsPrefix("rdfs", RDFS.getURI());
    model.setNsPrefix("schema", "http://schema.org#");
    model.setNsPrefix("skos", SKOS.getURI());
    model.setNsPrefix("spdx", "http://spdx.org/rdf/terms#");
    model.setNsPrefix("xsd", XMLSchema.NAMESPACE);
    model.setNsPrefix("vcard", VCARD4.getURI());
    model.setNsPrefix("locn", "http://www.w3.org/ns/locn#");
    model.setNsPrefix("dcatap", "http://data.europa.eu/r5r/");
    model.setNsPrefix("rdf", RDF.getURI());
    model.setNsPrefix("eli", "http://data.europa.eu/eli/ontology#");// ns1
    return model;
  }

  /**
   * Datasets to model.
   *
   * @param datasets the datasets
   * @param profile  the profile
   * @return the model
   */
  private static Model datasetsToModel(List<DcatDataset> datasets, DcatApProfile profile) {

    Model model = initializeModel();

    for (DcatDataset d : datasets) {
      try {
        switch (profile) {

          case DCATAP_IT:
            model.setNsPrefix("dcatapit", "http://dati.gov.it/onto/dcatapit#");
            model = DcatApItSerializer.addDatasetToModel(d, model);
            break;

          /*
           * ADD HERE MORE CASES FOR FURTHER PROFILES
           */

          default:
            model = addDatasetToModel(d, model);
            break;
        }

      } catch (Exception e) {
        logger.error(
            "Error while converting dataset " + d.getId() + " to model, reason: " + e.getMessage());
      }
    }

    return model;

  }

  /**
   * Adds the dataset to model.
   *
   * @param dataset the dataset
   * @param model   the model
   * @return the model
   */
  protected static Model addDatasetToModel(DcatDataset dataset, Model model) {
    it.eng.idra.utils.NgsiDcatReader.restoreMetadata(dataset);

    // Synthesize the dataset URI from its stable internal identifiers instead of
    // reusing the landingPage value. Reason: when the landingPage happens to also
    // appear as a foaf:page in the source RDF (common in DCAT-AP-Stat dumps), the
    // documentation serializer creates a sibling resource at the same URI with
    // type=foaf:Document — Jena then picks foaf:Document as the dataset element
    // type in the RDF/XML output, producing invalid DCAT-AP (the dataset is no
    // longer a dcat:Dataset). Consistent with the catalog URN scheme
    // (urn:idra:catalogue:<id>) already used elsewhere in the serializer.
    String datasetUri = StringUtils.isNotBlank(dataset.getResourceUri()) ? dataset.getResourceUri()
        : "urn:idra:dataset:" + dataset.getNodeId() + ":" + dataset.getId();
    Resource datasetResource = model.createResource(datasetUri, DCAT.Dataset);

    DcatRdfMetadata.write(dataset, datasetResource);
    addMultiLangTitleDescription(dataset.getDatasetDetails(),
        dataset.getTitle(), dataset.getDescription(), datasetResource, model);

    serializeConcept(dataset.getTheme(), model, datasetResource);
    // dct:subject — modelled as SKOS concepts like dcat:theme.
    serializeConcept(dataset.getSubject(), model, datasetResource);

    serializeContactPoint(dataset.getContactPoint(), model, datasetResource);

    if (dataset.getKeywordDetails() != null && !dataset.getKeywordDetails().isEmpty()) {
      dataset.getKeywordDetails().stream().filter(keyword -> keyword != null && StringUtils.isNotBlank(keyword.getValue()))
          .forEach(keyword -> {
            String language = StringUtils.trimToNull(keyword.getLanguage());
            if (language != null) {
              try {
                datasetResource.addLiteral(DCAT.keyword, model.createLiteral(keyword.getValue(), language));
              } catch (Exception e) {
                datasetResource.addLiteral(DCAT.keyword, keyword.getValue());
              }
            } else {
              datasetResource.addLiteral(DCAT.keyword, keyword.getValue());
            }
          });
    } else {
      dataset.getKeywords().stream().filter(keyword -> StringUtils.isNotBlank(keyword))
          .forEach(keyword -> datasetResource.addLiteral(DCAT.keyword, keyword));
    }

    // addDcatPropertyAsLiteral(dataset.getAccessRights(), datasetResource, model);

    if (isValidUri(dataset.getAccessRights().getValue())) {
      addDcatPropertyAsResource(dataset.getAccessRights(), datasetResource, model, true);
    } else {
      addDcatPropertyAsLiteral(dataset.getAccessRights(), datasetResource, model);
    }

    serializeDctStandard(dataset.getConformsTo(), datasetResource, model);

    List<DcatProperty> documentationList = dataset.getDocumentation();
    if (documentationList != null) {
      documentationList.stream().filter(item -> isValidUri(item.getValue()))
          .forEach(item -> addDcatPropertyAsResource(item, datasetResource, model, true));
    }

    List<DcatProperty> relatedResourceList = dataset.getRelatedResource();
    if (relatedResourceList != null) {
      relatedResourceList.stream().filter(item -> isValidUri(item.getValue()))
          .forEach(item -> addDcatPropertyAsResource(item, datasetResource, model, true));
    }

    serializeFrequency(dataset.getFrequency(), model, datasetResource);

    List<DcatProperty> hasVersion = dataset.getHasVersion();
    if (hasVersion != null) {
      hasVersion.stream().filter(item -> isValidUri(item.getValue()))
          .forEach(item -> addDcatPropertyAsResource(item, datasetResource, model, true));
    }

    List<DcatProperty> isVersionOf = dataset.getIsVersionOf();
    if (isVersionOf != null) {
      isVersionOf.stream().filter(item -> isValidUri(item.getValue()))
          .forEach(item -> addDcatPropertyAsResource(item, datasetResource, model, true));
    }

    serializeLandingPage(dataset.getLandingPage(), datasetUri, model, datasetResource);

    // datasetResource.addProperty(model.createProperty(dataset.getLandingPage().getUri()),
    // model.createResource(dataset.getLandingPage().getRange())
    // .addProperty(dataset.getLandingPage().getProperty(),
    // dataset.getLandingPage().getRange()));

    // Property p = model.createProperty(dataset.getLandingPage().getUri());
    // dataset.getLandingPage().getProperty().addProperty(p,
    // model.createResource(dataset.getLandingPage().getValue(),
    // dataset.getLandingPage().getRange()));

    serializeLanguage(dataset.getLanguage(), model, datasetResource);

    List<DcatProperty> provenance = dataset.getProvenance();
    if (provenance != null) {
      provenance.stream().forEach(item -> addDcatPropertyAsLiteral(item, datasetResource, model));
    }

    DcatRdfDates.add(datasetResource, dataset.getReleaseDate());
    DcatRdfDates.add(datasetResource, dataset.getUpdateDate());

    addDcatPropertyAsLiteral(dataset.getIdentifier(), datasetResource, model);

    List<DcatProperty> otherIdentifier = dataset.getOtherIdentifier();
    if (otherIdentifier != null) {
      otherIdentifier.stream().filter(id -> StringUtils.isNotBlank(id.getValue())).forEach(id -> {
        datasetResource.addProperty(model.createProperty(id.getProperty().getURI()),
            model.createResource().addLiteral(SKOS.notation, id.getValue()));
      });
    }

    List<DcatProperty> sample = dataset.getSample();
    if (sample != null) {
      sample.stream().filter(item -> isValidUri(item.getValue()))
          .forEach(item -> addDcatPropertyAsResource(item, datasetResource, model, true));
    }

    List<DcatProperty> source = dataset.getSource();
    if (source != null) {
      source.stream().filter(item -> isValidUri(item.getValue()))
          .forEach(item -> addDcatPropertyAsResource(item, datasetResource, model, true));
    }

    serializeSpatialCoverage(dataset.getSpatialCoverage(), model, datasetResource);

    serializeTemporalCoverage(dataset.getTemporalCoverage(), model, datasetResource);

    addDcatPropertyAsLiteral(dataset.getType(), datasetResource, model);
    addDcatPropertyAsLiteral(dataset.getVersion(), datasetResource, model);

    List<DcatProperty> versionNotes = dataset.getVersionNotes();
    if (versionNotes != null && (dataset.getVersionNoteDetails() == null || dataset.getVersionNoteDetails().isEmpty())) {
      versionNotes.stream().forEach(item -> addDcatPropertyAsLiteral(item, datasetResource, model));
    }

    // new, add also List<DcatDatasetSeries> inSeries;
    /*
     * logger.info(
     * "applicableLegislation size: " +
     * (applicableLegislation != null && !applicableLegislation.isEmpty() ?
     * applicableLegislation.size() : null));
     */
    List<DcatProperty> applicableLegislation = dataset.getApplicableLegislation();
    if (applicableLegislation != null) {
      applicableLegislation.stream()
          .filter(item -> StringUtils.isNotBlank(item.getValue()))
          .forEach(item -> {
            String safeUri = toSafeResourceUri(item.getValue());
            if (StringUtils.isNotBlank(safeUri)) {
              Resource legalResource = model.createResource(safeUri);
              legalResource.addProperty(RDF.type, ELI.LegalResource);
              datasetResource.addProperty(DCATAP.applicableLegislation, legalResource);
            } else {
              datasetResource.addProperty(DCATAP.applicableLegislation, model.createLiteral(item.getValue()));
            }
          });
    }

    // For wasGeneratedBy
    List<DcatProperty> wasGeneratedBy = dataset.getWasGeneratedBy();
    if (wasGeneratedBy != null) {
      wasGeneratedBy.stream()
          .filter(item -> StringUtils.isNotBlank(item.getValue()))
          .forEach(item -> {
            if (isValidUri(item.getValue())) {
              Resource resource = model.createResource(item.getValue());
              datasetResource.addProperty(ResourceFactory.createProperty("http://www.w3.org/ns/prov#wasGeneratedBy"),
                  resource);
            } else {
              datasetResource.addProperty(ResourceFactory.createProperty("http://www.w3.org/ns/prov#wasGeneratedBy"),
                  model.createLiteral(item.getValue()));
            }
          });
    }

    // For HVDCategory
    List<DcatProperty> HVDCategory = dataset.getHVDCategory();
    if (HVDCategory != null) {
      HVDCategory.stream()
          .filter(item -> StringUtils.isNotBlank(item.getValue()))
          .forEach(item -> {
            if (isValidUri(item.getValue())) {
              Resource resource = model.createResource(item.getValue());
              datasetResource.addProperty(DCATAP.hvdCategory, resource);
            } else {
              datasetResource.addProperty(DCATAP.hvdCategory, model.createLiteral(item.getValue()));
            }
          });
    }

    if (StringUtils.isNotBlank(dataset.getTemporalResolution().getValue())) {
      if (isValidUri(dataset.getTemporalResolution().getValue())) {
        datasetResource.addProperty(DCAT.temporalResolution,
            model.createResource(dataset.getTemporalResolution().getValue()));
      } else {
        datasetResource.addProperty(DCAT.temporalResolution,
            model.createTypedLiteral(dataset.getTemporalResolution().getValue(),org.apache.jena.datatypes.xsd.XSDDatatype.XSDduration));
      }
    }

    // Serialize qualifiedRelation
    List<Relationship> qualifiedRelation = dataset.getQualifiedRelation();
    if (qualifiedRelation != null && !qualifiedRelation.isEmpty()) {
      for (Relationship relationship : qualifiedRelation) {

        Resource relationshipResource = model.createResource(Relationship.getRdfClass());

        if (StringUtils.isNotBlank(relationship.getHad_role().getValue())) {
          if (isValidUri(relationship.getHad_role().getValue())) {
            relationshipResource.addProperty(DCAT.hadRole,
                model.createResource(relationship.getHad_role().getValue()));
          } else {
            relationshipResource.addProperty(DCAT.hadRole,
                model.createLiteral(relationship.getHad_role().getValue()));
          }
        }

        if (StringUtils.isNotBlank(relationship.getRelation().getValue())) {
          if (isValidUri(relationship.getRelation().getValue())) {
            relationshipResource.addProperty(DCTerms.relation,
                model.createResource(relationship.getRelation().getValue()));
          } else {
            relationshipResource.addProperty(DCTerms.relation,
                model.createLiteral(relationship.getRelation().getValue()));
          }
        }

        datasetResource.addProperty(DCAT.qualifiedRelation, relationshipResource);
      }
    }

    for (DcatDistribution distribution : dataset.getDistributions()) {
      addDistributionToModel(model, datasetResource, distribution);
    }

    // Dataset metadata must not depend on whether the enclosing catalogue was loaded.
    if (dataset.getPublisher() != null) {
      serializeFoafAgent(dataset.getPublisher(), model, datasetResource);
    }
    DcatMetadataEnvelope.write(dataset, datasetResource);

    OdmsCatalogue node = nodeResources != null ? nodeResources.get(Integer.valueOf(dataset.getNodeId())) : null;
    if (node == null) return model;

    /*
     * Add the Catalogue with the Dataset to the global Model.
     * node.getHost() may be a non-IRI identifier (e.g. a hash for DCATDUMP nodes);
     * fall back to homepage or a urn:idra:catalogue:<id> when invalid.
     */
    String catalogueUri = toSafeResourceUri(node.getHost());
    if (catalogueUri == null) {
      catalogueUri = toSafeResourceUri(node.getHomepage());
    }
    if (catalogueUri == null) {
      catalogueUri = "urn:idra:catalogue:" + node.getId();
    }
    // The catalogue is described once per model; later datasets only add their membership link.
    boolean described = model.contains(model.getResource(catalogueUri), RDF.type, DCAT.Catalog);
    model.createResource(catalogueUri, DCAT.Catalog).addProperty(DCAT.dataset, datasetResource);
    if (described) return model;
    DcatRdfMetadata.writeCatalogue(node,model.getResource(catalogueUri));
    model.getResource(catalogueUri).addLiteral(DCTerms.title, node.getName());
    if (StringUtils.isNotBlank(node.getDescription()))
      model.getResource(catalogueUri).addLiteral(DCTerms.description, node.getDescription());
    Resource catalogueResource = model.getResource(catalogueUri);
    DcatRdfDates.add(catalogueResource, DCTerms.issued,
        node.getRegisterDate() == null ? null : node.getRegisterDate().toString());
    DcatRdfDates.add(catalogueResource, DCTerms.modified,
        node.getLastUpdateDate() == null ? null : node.getLastUpdateDate().toString());

    /*
     * ** Create the Publisher for the DCATCatalogue as a new FOAFAgent with info
     * from the federated Catalogue
     */

    String publisherResourceUri = node.getPublisherUrl();
    if (StringUtils.isBlank(publisherResourceUri) || !isValidUri(publisherResourceUri)) {

      if (!node.getHomepage().equalsIgnoreCase(node.getHost())) {
        publisherResourceUri = node.getHomepage();
      } else {
        publisherResourceUri = node.getHomepage() + "/" + node.getId();
      }
    }

    /*
     * Check if the publisher is already present in the global Model and if any
     * create it, either or both for dataset and catalog
     */

    if (StringUtils.isNotBlank(node.getPublisherName())) {
      serializeFoafAgent(new FoafAgent(DCTerms.publisher.getURI(), publisherResourceUri,
          java.util.Collections.singletonList(node.getPublisherName()), node.getPublisherEmail(),
          node.getPublisherUrl(), "", "", String.valueOf(node.getId())), model, catalogueResource);
    }
    DcatCatalogueSource.write(node, catalogueResource);

    return model;

  }

  /**
   * Serialize temporal coverage.
   *
   * @param temporalCoverage the temporal coverage
   * @param model            the model
   * @param datasetResource  the dataset resource
   */
  protected static void serializeTemporalCoverage(List<DctPeriodOfTime> temporalCoverage, Model model,
      Resource datasetResource) {
    if (temporalCoverage == null) {
      return;
    }
    for (DctPeriodOfTime period : temporalCoverage) {
      if (period == null) continue;
      // Don't emit a <dct:PeriodOfTime> if all four date fields are blank — Jena would
      // otherwise write an empty literal with xsd:date datatype, producing invalid RDF.
      boolean hasStart = period.getStartDate() != null
          && StringUtils.isNotBlank(period.getStartDate().getValue());
      boolean hasEnd = period.getEndDate() != null
          && StringUtils.isNotBlank(period.getEndDate().getValue());
      boolean hasBeginning = period.getBeginning() != null
          && StringUtils.isNotBlank(period.getBeginning().getValue());
      boolean hasEndPoint = period.getEnd() != null
          && StringUtils.isNotBlank(period.getEnd().getValue());

      if (!hasStart && !hasEnd && !hasBeginning && !hasEndPoint) {
        continue;
      }

      Resource periodResource = model.createResource(DctPeriodOfTime.getRdfClass());
      if (hasStart) {
        DcatRdfDates.add(periodResource, period.getStartDate());
      }
      if (hasEnd) {
        DcatRdfDates.add(periodResource, period.getEndDate());
      }
      if (hasBeginning) {
        DcatRdfDates.add(periodResource, period.getBeginning());
      }
      if (hasEndPoint) {
        DcatRdfDates.add(periodResource, period.getEnd());
      }
      datasetResource.addProperty(StringUtils.isBlank(period.getUri())
          ? DCTerms.temporal : model.createProperty(period.getUri()), periodResource);
    }
  }

  /**
   * Serialize spatial coverage.
   *
   * @param spatialCoverage the spatial coverage
   * @param model           the model
   * @param parentResource  the parent resource
   */
  private static final java.util.regex.Pattern WKT = java.util.regex.Pattern.compile(
      "^\\s*(<[^>]+>\\s*)?(POINT|LINESTRING|POLYGON|MULTIPOINT|MULTILINESTRING|MULTIPOLYGON|GEOMETRYCOLLECTION)\\b.*",
      java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

  /** WKT and GeoJSON keep their GeoSPARQL datatype (DCAT-AP bbox/centroid/geometry); other encodings stay plain literals. */
  private static void addGeometryLiteral(DcatProperty property, Resource target, Model model) {
    String value = property.getValue();
    String datatype = value == null ? null
        : WKT.matcher(value).matches() ? "http://www.opengis.net/ont/geosparql#wktLiteral"
        : value.trim().startsWith("{") && value.contains("\"type\"") && value.contains("\"coordinates\"")
            ? "http://www.opengis.net/ont/geosparql#geoJSONLiteral" : null;
    if (datatype != null) {
      target.addLiteral(property.getProperty(), model.createTypedLiteral(value,
          org.apache.jena.datatypes.TypeMapper.getInstance().getSafeTypeByName(datatype)));
    } else {
      addDcatPropertyAsLiteral(property, target, model);
    }
  }

  protected static void serializeSpatialCoverage(List<DctLocation> spatialCoverage, Model model,
      Resource parentResource) {

    if (spatialCoverage == null) {
      return;
    }

    for (DctLocation location : spatialCoverage) {
      // Don't emit an empty <dct:Location/> when no sub-property carries a value.
      boolean hasGeoId = location.getGeographicalIdentifier() != null
          && StringUtils.isNotBlank(location.getGeographicalIdentifier().getValue());
      boolean hasGeometry = location.getGeometry() != null
          && StringUtils.isNotBlank(location.getGeometry().getValue());
      boolean hasGeoName = location.getGeographicalName() != null
          && StringUtils.isNotBlank(location.getGeographicalName().getValue());
      boolean hasBbox = location.getBbox() != null
          && StringUtils.isNotBlank(location.getBbox().getValue());
      boolean hasCentroid = location.getCentroid() != null
          && StringUtils.isNotBlank(location.getCentroid().getValue());

      if (!hasGeoId && !hasGeometry && !hasGeoName && !hasBbox && !hasCentroid) {
        continue;
      }

      String locationIri = null;
      if (hasGeoId) {
        String geoUri = location.getGeographicalIdentifier().getValue();
        if (IRIFactory.iriImplementation().create(geoUri).hasViolation(false)) {
          location.getGeographicalIdentifier().setValue(GEO_BASE_URI + geoUri);
        }
        // An IRI identifier is the location itself (dct:spatial <IRI>), not a literal on an anonymous node.
        String identifier = location.getGeographicalIdentifier().getValue();
        if (identifier.matches("^(https?|urn):.+") && isValidUri(identifier)) locationIri = identifier;
      }
      Resource spatialResource = locationIri != null
          ? model.createResource(locationIri, DctLocation.getRdfClass())
          : model.createResource(DctLocation.getRdfClass());
      if (hasGeoId && locationIri == null) {
        addDcatPropertyAsLiteral(location.getGeographicalIdentifier(), spatialResource, model);
      }

      if (hasGeometry) {
        addGeometryLiteral(location.getGeometry(), spatialResource, model);
      }

      // TODO Geographical Name as SKOS CONCEPT
      if (hasGeoName) {
        addDcatPropertyAsResource(location.getGeographicalName(), spatialResource, model, false);
      }

      if (hasBbox) {
        addGeometryLiteral(location.getBbox(), spatialResource, model);
      }
      if (hasCentroid) {
        addGeometryLiteral(location.getCentroid(), spatialResource, model);
      }

      // JSON connectors can provide spatial coverage without an explicit predicate URI.
      parentResource.addProperty(StringUtils.isBlank(location.getUri())
          ? DCTerms.spatial : model.createProperty(location.getUri()), spatialResource);
    }
  }

  /**
   * Serialize language.
   *
   * @param language        the language
   * @param model           the model
   * @param datasetResource the dataset resource
   */
  protected static void serializeLanguage(List<DcatProperty> language, Model model,
      Resource datasetResource) {

    if (language != null) {
      language.stream().filter(lang -> StringUtils.isNotBlank(lang.getValue())).forEach(lang -> {
        try {
          datasetResource.addProperty(lang.getProperty(), model.createResource(
              iriFactory.construct(lang.getValue()).toURI().toString(), DCTerms.LinguisticSystem));
        } catch (Exception e) {
          // iriFactory throws IRIException (not URISyntaxException) when the value
          // is a bare ISO 639 code like "eng"/"ukr"/"enc" — common from Zenodo and
          // other sources that store language codes rather than full IRIs. Fall back
          // to the EU authority vocabulary, which uses uppercase 3-letter codes.
          datasetResource.addProperty(lang.getProperty(), model.createResource(
              LANGUAGE_BASE_URI + languageCode(lang.getValue()), DCTerms.LinguisticSystem));
        }
      });
    }
  }

  /**
   * Serialize frequency.
   *
   * @param frequency       the frequency
   * @param model           the model
   * @param datasetResource the dataset resource
   */
  protected static void serializeFrequency(DcatProperty frequency, Model model,
      Resource datasetResource) {

    // No source frequency, no dct:accrualPeriodicity: Idra never writes UNKNOWN on its own.
    String value = frequency == null || frequency.getValue() == null ? ""
        : normalizeUri(frequency.getValue());
    if (value.isEmpty()) {
      return;
    }
    // A full authority IRI or its code (any case) are the same EU concept.
    String code = value.startsWith(FREQUENCY_BASE_URI) ? value.substring(FREQUENCY_BASE_URI.length())
        : value;
    Resource target;
    if (DctFrequency.isCode(code) || DctFrequency.isCode(code.toUpperCase(java.util.Locale.ROOT))) {
      target = model.createResource(FREQUENCY_BASE_URI
          + (DctFrequency.isCode(code) ? code : code.toUpperCase(java.util.Locale.ROOT)), DCTerms.Frequency);
    } else if (DctFrequency.fromLabel(value) != null) {
      target = model.createResource(FREQUENCY_BASE_URI + DctFrequency.fromLabel(value),
          DCTerms.Frequency);
    } else if (isValidUri(value) && value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
      target = model.createResource(value, DCTerms.Frequency);
    } else {
      // Source text that is not a vocabulary code stays as the label of the frequency.
      target = model.createResource(DCTerms.Frequency).addProperty(RDFS.label, value);
    }
    datasetResource.addProperty(DCTerms.accrualPeriodicity, target);
  }

  /**
   * Serialize the landing page as a foaf:Document (DCAT-AP range), except when it is the dataset IRI
   * itself: the dataset must stay a dcat:Dataset.
   */
  protected static void serializeLandingPage(DcatProperty landingPage, String datasetUri,
      Model model, Resource datasetResource) {
    String value = landingPage == null ? null : normalizeUri(landingPage.getValue());
    if (StringUtils.isBlank(value)) {
      return;
    }
    if (!isValidUri(value)) {
      datasetResource.addProperty(DCAT.landingPage, model.createLiteral(landingPage.getValue()));
    } else if (value.equals(datasetUri)) {
      datasetResource.addProperty(DCAT.landingPage, model.createResource(value));
    } else {
      datasetResource.addProperty(DCAT.landingPage, model.createResource(value, FOAF.Document));
    }
  }

  /**
   * Serialize contact point.
   *
   * @param contactPointList the contact point list
   * @param model            the model
   * @param datasetResource  the dataset resource
   */
  protected static void serializeContactPoint(List<VcardOrganization> contactPointList, Model model,
      Resource datasetResource) {

    if (contactPointList != null && !contactPointList.isEmpty()) {
      for (VcardOrganization contactPoint : contactPointList) {
        try {
          Resource contactPointR = null;

          if (StringUtils.isNotBlank(contactPoint.getResourceUri())) {
            contactPointR = model.createResource(contactPoint.getResourceUri(),
                VcardOrganization.getRdfClass());
          } else {
            contactPointR = model.createResource(VcardOrganization.getRdfClass());
          }

          // .addProperty(RDF.type, VCARD4.Kind)
          addDcatPropertyAsLiteral(contactPoint.getFn(), contactPointR, model);

          // Fix hasEmail value if needed
          String hasEmailValue = contactPoint.getHasEmail().getValue();
          // && CommonUtil.checkIfIsEmail(hasEmailValue)
          if (StringUtils.isNotBlank(hasEmailValue)) {
            if (!hasEmailValue.startsWith("mailto:")) {
              contactPoint.getHasEmail().setValue("mailto:" + hasEmailValue);
            }
            addDcatPropertyAsResource(contactPoint.getHasEmail(), contactPointR, model, false);
          }

          addDcatPropertyAsResource(contactPoint.getHasUrl(), contactPointR, model, false);

          // Add contactPoint property to dataset Resource;
          datasetResource.addProperty(model.createProperty(contactPoint.getPropertyUri()),
              contactPointR);

        } catch (Exception ignore) {
          logger.debug(ignore.getLocalizedMessage());
        }
      }
    }

  }

  /**
   * Serialize foaf agent.
   *
   * @param agent          the agent
   * @param model          the model
   * @param parentResource the parent resource
   */
  protected static void serializeFoafAgent(FoafAgent agent, Model model, Resource parentResource) {

    if (agent != null) {
      Resource agentResource = null;

      if (StringUtils.isNotBlank(agent.getResourceUri()) && isValidUri(agent.getResourceUri())) {
        agentResource = model.createResource(agent.getResourceUri(), FoafAgent.getRdfClass());
      } else {
        agentResource = model.createResource(FoafAgent.getRdfClass());
      }

      // addDcatPropertyAsLiteral(agent.getName(), agentResource, model);
      if (agent.getName() != null) {
        for (DcatProperty name : agent.getName()) {
          addDcatPropertyAsLiteral(name, agentResource, model);
        }
      }
      // IRI values (mailto:, concept IRIs such as publisher types) stay IRIs; plain text stays text.
      addDcatPropertyAsResource(agent.getMbox(), agentResource, model, false);
      addDcatPropertyAsResource(agent.getType(), agentResource, model, false);
      addDcatPropertyAsLiteral(agent.getIdentifier(), agentResource, model);
      addDcatPropertyAsResource(agent.getHomepage(), agentResource, model, false);

      parentResource.addProperty(model.createProperty(agent.getPropertyUri()), agentResource);

    }
  }

  /**
   * Whether the value is an absolute IRI (has a scheme and no whitespace).
   *
   * @param value the candidate IRI
   * @return true if it can be used as a resource IRI
   */
  static boolean isAbsoluteIri(String value) {
    if (StringUtils.isBlank(value) || value.chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    try {
      return new java.net.URI(value).isAbsolute();
    } catch (java.net.URISyntaxException e) {
      return false;
    }
  }

  /**
   * Serialize concept.
   *
   * @param <T>            the generic type
   * @param conceptList    the concept list
   * @param model          the model
   * @param parentResource the parent resource
   */
  protected static <T extends SkosConcept> void serializeConcept(List<T> conceptList, Model model,
      Resource parentResource) {

    if (conceptList != null && !conceptList.isEmpty()) {

      for (T concept : conceptList) {
        Resource conceptR = null;
        if (concept != null) {
          List<SkosPrefLabel> labelList = concept.getPrefLabel();

          if (isAbsoluteIri(concept.getResourceUri())) {
            conceptR = model.createResource(concept.getResourceUri(), SKOS.Concept);
          } else {
            // A non-absolute value (e.g. a bare code) would make the whole RDF/XML dump fail
            // with "Not an RDF IRI": keep the concept as a blank node with its labels.
            conceptR = model.createResource(SKOS.Concept);
          }

          if (labelList != null && !labelList.isEmpty()) {
            for (SkosPrefLabel l : labelList) {
              conceptR.addProperty(SKOS.prefLabel,
                  model.createLiteral(l.getValue(), l.getLanguage()));
            }
          }

          parentResource.addProperty(model.createProperty(concept.getPropertyUri()), conceptR);

        }

        // if (StringUtils.isNotBlank(concept.getValue()))
        // datasetResource.addProperty(concept.getProperty(),
        // model.createResource(
        // IRIFactory.jenaImplementation().construct(concept.getValue()).toURI().toString(),
        // SKOS.Concept));

        // } catch (URISyntaxException e) {
        // datasetResource.addProperty(concept.getProperty(),
        // model.createResource(THEME_BASE_URI + concept.getValue(), SKOS.Concept));
        // }
      }
    }
  }

  /**
   * Adds the distribution to model.
   *
   * @param model           the model
   * @param datasetResource the dataset resource
   * @param distribution    the distribution
   */
  private static void addDistributionToModel(Model model, Resource datasetResource,
      DcatDistribution distribution) {

    Resource distResource = model.createResource(StringUtils.isNotBlank(distribution.getResourceUri()) ? distribution.getResourceUri() : "urn:idra:distribution:" + distribution.getNodeId() + ":" + distribution.getId(), DcatDistribution.getRdfClass());

    addDcatPropertyAsResource(distribution.getAccessUrl(), distResource, model, false);

    addMultiLangTitleDescription(distribution.getDistributionDetails(),
        distribution.getTitle(), distribution.getDescription(), distResource, model);

    serializeFormat(distribution.getFormat(), model, distResource);

    serializeMediaType(distribution.getMediaType(), model, distResource);

    serializeLicense(distribution.getLicense(), model, distResource);

    serializeByteSize(distribution.getByteSize(), model, distResource);

    serializeChecksum(distribution.getChecksum(), model, distResource);

    serializeLanguage(distribution.getLanguage(), model, distResource);

    List<DcatProperty> distDocumentation = distribution.getDocumentation();
    if (distDocumentation != null) {
      distDocumentation.stream().filter(item -> StringUtils.isNotBlank(item.getValue()))
          .forEach(item -> distResource.addProperty(item.getProperty(), item.getValue()));
    }

    addDcatPropertyAsResource(distribution.getDownloadUrl(), distResource, model, false);

    serializeDctStandard(distribution.getLinkedSchemas(), distResource, model);

    // mediaType was already exported by serializeMediaType above.

    DcatRdfDates.add(distResource, distribution.getReleaseDate());
    DcatRdfDates.add(distResource, distribution.getUpdateDate());

    // addDcatPropertyAsLiteral(distribution.getRights(), distResource, model);

    if (distribution.getRights() != null && isValidUri(distribution.getRights().getValue())) {
      addDcatPropertyAsResource(distribution.getRights(), distResource, model, true);
    } else {
      addDcatPropertyAsLiteral(distribution.getRights(), distResource, model);
    }

    // Property p = model.createProperty(distribution.getRights().getUri());
    // distResource.addProperty(p,
    // model.createResource(distribution.getRights().getValue(),
    // distribution.getRights().getRange()));

    serializeConcept(Arrays.asList(distribution.getStatus()), model, distResource);

    // Title (and description) already emitted near the top of this method via
    // addMultiLangTitleDescription, which preserves multilingual variants.

    // new
    // Serialize accessService(s)
    List<DcatDataService> accessServices = distribution.getAccessService();
    if (accessServices != null && !accessServices.isEmpty()) {
      for (DcatDataService service : accessServices) {

        Resource serviceResource = model.createResource(StringUtils.isNotBlank(service.getResourceUri()) ? service.getResourceUri()
            : "urn:idra:service:" + distribution.getNodeId() + ":" + service.getDataServiceId(), DcatDataService.getRdfClass());
        DcatRdfMetadata.writeService(service, serviceResource);

        if (service.getEndpointURL() != null) {
          service.getEndpointURL().stream()
              .filter(item -> StringUtils.isNotBlank(item.getValue()))
              .forEach(item -> {
                if (isValidUri(item.getValue())) {
                  serviceResource.addProperty(DCAT.endpointURL, model.createResource(item.getValue()));
                } else {
                  serviceResource.addProperty(DCAT.endpointURL, model.createLiteral(item.getValue()));
                }
              });
        }

        if (service.getEndpointDescription() != null) {
          service.getEndpointDescription().stream()
              .filter(item -> StringUtils.isNotBlank(item.getValue()))
              .forEach(item -> {
                if (isValidUri(item.getValue())) {
                  serviceResource.addProperty(DCAT.endpointDescription, model.createResource(item.getValue()));
                } else {
                  serviceResource.addProperty(DCAT.endpointDescription, model.createLiteral(item.getValue()));
                }
              });
        }

        /*
         * if (service.getApplicableLegislation() != null) {
         * service.getApplicableLegislation().stream()
         * .filter(item -> StringUtils.isNotBlank(item.getValue()))
         * .forEach(item -> {
         * if (isValidUri(item.getValue())) {
         * Resource legalResource = model.createResource(item.getValue());
         * legalResource.addProperty(RDF.type, ELI.LegalResource);
         * serviceResource.addProperty(DCATAP.applicableLegislation, legalResource);
         * } else {
         * serviceResource.addProperty(DCATAP.applicableLegislation,
         * model.createLiteral(item.getValue()));
         * }
         * });
         * }
         */

        /*
         * if (service.getContactPoint() != null &&
         * !service.getContactPoint().isEmpty()) {
         * serializeContactPoint(service.getContactPoint(), model, serviceResource);
         * }
         */

        /*
         * if (StringUtils.isNotBlank(service.getLicence().getValue())) {
         * if (isValidUri(service.getLicence().getValue())) {
         * serviceResource.addProperty(DCTerms.license,
         * model.createResource(service.getLicence().getValue()));
         * } else {
         * serviceResource.addProperty(DCTerms.license,
         * model.createLiteral(service.getLicence().getValue()));
         * }
         * }
         */

        if (service.getRights() != null) {
          service.getRights().stream()
              .filter(item -> StringUtils.isNotBlank(item.getValue()))
              .forEach(item -> {
                // serviceResource.addProperty(DCTerms.rights,
                // model.createLiteral(item.getValue()));
                if (isValidUri(item.getValue())) {
                  serviceResource.addProperty(DCTerms.accessRights, model.createResource(item.getValue()));
                } else {
                  serviceResource.addProperty(DCTerms.accessRights, model.createLiteral(item.getValue()));
                }
              });
        }

        if (service.getServesDataset() != null) {
          service.getServesDataset().stream()
              .filter(item -> StringUtils.isNotBlank(item.getValue()))
              .forEach(item -> {
                if (isValidUri(item.getValue())) {
                  serviceResource.addProperty(DCAT.servesDataset, model.createResource(item.getValue()));
                } else {
                  serviceResource.addProperty(DCAT.servesDataset, model.createLiteral(item.getValue()));
                }
              });
        }

        if (!DcatRdfMetadata.hasDetails(service)) {
          addDcatPropertyAsLiteral(service.getTitle(), serviceResource, model);
        }

        // Link the distribution to the DataService
        distResource.addProperty(DCAT.accessService, serviceResource);
      }
    }

    /*
     * logger.info(
     * "applicableLegislation size: " +
     * (applicableLegislation != null && !applicableLegislation.isEmpty() ?
     * applicableLegislation.size() : null));
     */
    List<DcatProperty> applicableLegislation = distribution.getApplicableLegislation();
    if (applicableLegislation != null) {
      applicableLegislation.stream()
          .filter(item -> StringUtils.isNotBlank(item.getValue()))
          .forEach(item -> {
            String safeUri = toSafeResourceUri(item.getValue());
            if (StringUtils.isNotBlank(safeUri)) {
              Resource legalResource = model.createResource(safeUri);
              legalResource.addProperty(RDF.type, ELI.LegalResource);
              distResource.addProperty(DCATAP.applicableLegislation, legalResource);
            } else {
              distResource.addProperty(DCATAP.applicableLegislation, model.createLiteral(item.getValue()));
            }
          });
    }

    // addDcatPropertyAsResource(distribution.getHasPolicy(), distResource, model,
    // false);

    if (distribution.getHasPolicy() != null && StringUtils.isNotBlank(distribution.getHasPolicy().getValue())) {
      if (isValidUri(distribution.getHasPolicy().getValue())) {
        distResource.addProperty(model.createProperty("https://www.w3.org/ns/odrl/2/"),
            model.createResource(distribution.getHasPolicy().getValue()));
      } else {
        distResource.addProperty(model.createProperty("https://www.w3.org/ns/odrl/2/"),
            model.createLiteral(distribution.getHasPolicy().getValue()));
      }
    }

    if (distribution.getAvailability() != null && StringUtils.isNotBlank(distribution.getAvailability().getValue())) {
      if (isValidUri(distribution.getAvailability().getValue())) {
        distResource.addProperty(DCATAP.availability,
            model.createResource(distribution.getAvailability().getValue()));
      } else {
        distResource.addProperty(DCATAP.availability,
            model.createLiteral(distribution.getAvailability().getValue()));
      }
    }
    if (distribution.getCompressionFormat() != null && StringUtils.isNotBlank(distribution.getCompressionFormat().getValue())) {
      if (isValidUri(distribution.getCompressionFormat().getValue())) {
        distResource.addProperty(DCAT.compressFormat,
            model.createResource(distribution.getCompressionFormat().getValue()));
      } else {
        distResource.addProperty(DCAT.compressFormat,
            model.createLiteral(distribution.getCompressionFormat().getValue()));
      }
    }
    if (distribution.getPackagingFormat() != null && StringUtils.isNotBlank(distribution.getPackagingFormat().getValue())) {
      if (isValidUri(distribution.getPackagingFormat().getValue())) {
        distResource.addProperty(DCAT.packageFormat,
            model.createResource(distribution.getPackagingFormat().getValue()));
      } else {
        distResource.addProperty(DCAT.packageFormat,
            model.createLiteral(distribution.getPackagingFormat().getValue()));
      }
    }

    if (distribution.getTemporalResolution() != null && StringUtils.isNotBlank(distribution.getTemporalResolution().getValue())) {
      if (isValidUri(distribution.getTemporalResolution().getValue())) {
        distResource.addProperty(DCAT.temporalResolution,
            model.createResource(distribution.getTemporalResolution().getValue()));
      } else {
        distResource.addProperty(DCAT.temporalResolution,
            model.createTypedLiteral(distribution.getTemporalResolution().getValue(),org.apache.jena.datatypes.xsd.XSDDatatype.XSDduration));
      }
    }

    if (distribution.getSpatialResolution() != null && StringUtils.isNotBlank(distribution.getSpatialResolution().getValue())) {
      if (isValidUri(distribution.getSpatialResolution().getValue())) {
        distResource.addProperty(DCAT.spatialResolutionInMeters,
            model.createResource(distribution.getSpatialResolution().getValue()));
      } else {
        distResource.addProperty(DCAT.spatialResolutionInMeters,
            model.createTypedLiteral(distribution.getSpatialResolution().getValue(),org.apache.jena.datatypes.xsd.XSDDatatype.XSDdecimal));
      }
    }

    datasetResource.addProperty(DCAT.distribution, distResource);
  }

  /**
   * Serialize byte size.
   *
   * @param property     the property
   * @param model        the model
   * @param distResource the dist resource
   */
  protected static void serializeByteSize(DcatProperty property, Model model,
      Resource distResource) {
    if (property == null || StringUtils.isBlank(property.getValue())) return;
    try {
      // DCAT-AP: xsd:nonNegativeInteger. A fractional or negative size is not a byte count.
      BigInteger size = new BigDecimal(property.getValue().trim()).toBigIntegerExact();
      if (size.signum() < 0) {
        return;
      }
      distResource.addProperty(DCAT.byteSize,
          model.createTypedLiteral(size.toString(), XSDDatatype.XSDnonNegativeInteger));
    } catch (NumberFormatException | ArithmeticException e) {
      logger.debug("The value: " + property.getValue() + " is not a valid byteSize - SKIPPED");
    }
  }

  /**
   * Serialize checksum.
   *
   * @param checksum     the checksum
   * @param model        the model
   * @param distResource the dist resource
   */
  protected static void serializeChecksum(SpdxChecksum checksum, Model model,
      Resource distResource) {

    // Non-RDF connectors also create placeholder beans when no checksum was supplied.
    if (checksum == null || checksum.getChecksumValue() == null
        || StringUtils.isBlank(checksum.getChecksumValue().getValue())) return;
    Resource value = model.createResource(SpdxChecksum.getRdfClass());
    if (checksum.getAlgorithm() != null && StringUtils.isNotBlank(checksum.getAlgorithm().getValue())) {
      String algorithm = checksum.getAlgorithm().getValue();
      if (algorithm.startsWith("checksumAlgorithm_")) algorithm = "http://spdx.org/rdf/terms#" + algorithm;
      org.apache.jena.rdf.model.Property predicate = model.createProperty("http://spdx.org/rdf/terms#algorithm");
      if (isAbsoluteIri(algorithm)) value.addProperty(predicate, model.createResource(algorithm));
      else value.addProperty(predicate, algorithm);
    }
    value.addProperty(model.createProperty("http://spdx.org/rdf/terms#checksumValue"),
        checksum.getChecksumValue().getValue(), XSDhexBinary.XSDhexBinary);
    distResource.addProperty(model.createProperty("http://spdx.org/rdf/terms#checksum"), value);
  }

  /**
   * Serialize license.
   *
   * @param license      the license
   * @param model        the model
   * @param distResource the dist resource
   */
  protected static void serializeLicense(DctLicenseDocument license, Model model,
      Resource distResource) {

    if (license != null) {

      // The dct:license property IRI is the placeholder of an empty licence, never the licence.
      String uri = DCTerms.license.getURI().equals(license.getUri()) ? null : license.getUri();
      String licenseTypeUri = licenceTypeUri(license.getType() == null ? null
          : license.getType().getValue());
      if (StringUtils.isBlank(uri) && licenseTypeUri == null && isBlank(license.getName())
          && isBlank(license.getVersionInfo())) {
        return;
      }
      Resource licenseResource = null;

      // Initialize license Resource
      if (StringUtils.isNotBlank(uri)) {
        licenseResource = model.createResource(uri, DctLicenseDocument.getRdfClass());
      } else {
        licenseResource = model.createResource(DctLicenseDocument.getRdfClass());
      }

      addDcatPropertyAsLiteral(license.getName(), licenseResource, model);
      addDcatPropertyAsLiteral(license.getVersionInfo(), licenseResource, model);
      if (licenseTypeUri != null) {
        DcatProperty normalizedType = new DcatProperty(license.getType().getProperty(),
            license.getType().getRange(), licenseTypeUri);
        addDcatPropertyAsResource(normalizedType, licenseResource, model, false);
      }

      // Add license Resource as property object of the parent Resource
      distResource.addProperty(model.createProperty(DCTerms.license.getURI()), licenseResource);

    }
  }

  /**
   * Serialize format.
   *
   * @param format       the format
   * @param model        the model
   * @param distResource the dist resource
   */
  protected static void serializeFormat(DcatProperty format, Model model, Resource distResource) {

    if (format == null || StringUtils.isBlank(format.getValue())) {
      return;
    }
    String value = normalizeUri(format.getValue());
    String euFileType = euFileType(value);
    if (euFileType != null) {
      distResource.addProperty(DCTerms.format,
          model.createResource(euFileType, DCTerms.MediaTypeOrExtent));
    } else if (isAbsoluteIri(value)) {
      // An IRI written by the source is source data, even outside the EU vocabulary.
      distResource.addProperty(DCTerms.format,
          model.createResource(value, DCTerms.MediaTypeOrExtent));
    } else {
      // Source text that is no EU file type: never turned into a file-type IRI that does not exist.
      distResource.addProperty(DCTerms.format, model.createResource(DCTerms.MediaTypeOrExtent)
          .addProperty(RDFS.label, format.getValue().trim()));
    }
  }

  /** EU file-type codes of the pinned DCAT-AP 3.0.1 vocabulary. */
  private static final java.util.Set<String> EU_FILE_TYPES = loadCodes("/eu-file-types.txt");

  /** Common source spellings of EU file types. */
  private static final Map<String, String> FILE_TYPE_ALIASES = Map.of("TURTLE", "RDF_TURTLE",
      "TTL", "RDF_TURTLE", "JSONLD", "JSON_LD", "WMS", "WMS_SRVC", "WFS", "WFS_SRVC",
      "RDFXML", "RDF_XML");

  private static java.util.Set<String> loadCodes(String resource) {
    try (java.io.InputStream in = DcatApSerializer.class.getResourceAsStream(resource)) {
      if (in == null) {
        return java.util.Set.of();
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      return java.util.Set.of();
    }
  }

  /**
   * The EU file-type IRI of a source format (code or authority IRI, any case, with or without a
   * leading dot); null when the vocabulary has no such concept.
   */
  static String euFileType(String format) {
    if (StringUtils.isBlank(format)) {
      return null;
    }
    String code = format.trim();
    if (code.toLowerCase(java.util.Locale.ROOT).startsWith(FORMAT_BASE_URI)) {
      code = code.substring(FORMAT_BASE_URI.length());
    } else if (code.contains(":") || code.contains("/")) {
      return null;
    }
    code = code.replaceFirst("^\\.", "").toUpperCase(java.util.Locale.ROOT).replace('-', '_')
        .replace(' ', '_');
    code = FILE_TYPE_ALIASES.getOrDefault(code, code);
    return EU_FILE_TYPES.contains(code) ? FORMAT_BASE_URI + code : null;
  }

  /** EU language code (ISO 639 three letters, upper case) of a bare source code. */
  static String languageCode(String code) {
    String value = code.trim();
    if (value.length() == 2) {
      try {
        return new java.util.Locale(value).getISO3Language().toUpperCase(java.util.Locale.ROOT);
      } catch (java.util.MissingResourceException unknown) {
        return value.toUpperCase(java.util.Locale.ROOT);
      }
    }
    return value.toUpperCase(java.util.Locale.ROOT);
  }

  /**
   * Serialize mediaType.
   *
   * @param mediatype    the format
   * @param model        the model
   * @param distResource the dist resource
   */
  protected static void serializeMediaType(DcatProperty mediatype, Model model, Resource distResource) {

    if (mediatype == null || StringUtils.isBlank(mediatype.getValue())) {
      return;
    }
    String value = normalizeUri(mediatype.getValue());
    String iana = ianaMediaType(value);
    if (iana != null) {
      distResource.addProperty(DCAT.mediaType, model.createResource(iana, DCTerms.MediaType));
    } else if (isAbsoluteIri(value)) {
      distResource.addProperty(DCAT.mediaType, model.createResource(value, DCTerms.MediaType));
    } else {
      // Never a media-types/<text> IRI that IANA does not register.
      distResource.addProperty(DCAT.mediaType, model.createResource(DCTerms.MediaType)
          .addProperty(RDFS.label, mediatype.getValue().trim()));
    }
  }

  /** IANA types of the format codes some sources (OpenDataSoft) write as media type. */
  private static final Map<String, String> MEDIA_TYPE_CODES = Map.ofEntries(
      Map.entry("rdfxml", "application/rdf+xml"), Map.entry("turtle", "text/turtle"),
      Map.entry("n3", "text/n3"), Map.entry("jsonld", "application/ld+json"),
      Map.entry("geojson", "application/geo+json"), Map.entry("json", "application/json"),
      Map.entry("csv", "text/csv"), Map.entry("xls", "application/vnd.ms-excel"),
      Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      Map.entry("kml", "application/vnd.google-earth.kml+xml"),
      Map.entry("gpx", "application/gpx+xml"), Map.entry("parquet", "application/vnd.apache.parquet"),
      Map.entry("jsonl", "application/jsonl"), Map.entry("shp", "application/vnd.shp"),
      Map.entry("pdf", "application/pdf"), Map.entry("xml", "application/xml"),
      Map.entry("zip", "application/zip"));

  /**
   * The IANA IRI of a media type given as "type/subtype", as an IANA IRI or as one of the format codes
   * above; null for anything else.
   */
  static String ianaMediaType(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String text = value.trim();
    // An IANA IRI written by the source (http or https) is kept as it is.
    for (String base : List.of(MEDIATYPE_BASE_URI, "http://www.iana.org/assignments/media-types/")) {
      if (text.startsWith(base)) {
        return text.substring(base.length()).matches("^[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+$") ? text : null;
      }
    }
    text = MEDIA_TYPE_CODES.getOrDefault(text.toLowerCase(java.util.Locale.ROOT), text);
    return text.matches("^[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+$")
        ? MEDIATYPE_BASE_URI + text.toLowerCase(java.util.Locale.ROOT) : null;
  }

  /** Keep source IRIs intact and never modify the persisted/cached bean while exporting. */
  private static void serializeVocabularyValue(DcatProperty property, String base,
      Model model, Resource distribution) {
    String value = normalizeUri(property.getValue());
    // An absolute identifier is source data even when its vocabulary is unknown.
    // Legacy short codes keep the existing expansion; membership is audited separately.
    String uri = value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") ? value : base + value;
    try {
      if (new URI(uri).isAbsolute() && !iriFactory.create(uri).hasViolation(false)) {
        Resource resource = property.getRange() == null ? model.createResource(uri)
            : model.createResource(uri, property.getRange());
        distribution.addProperty(property.getProperty(), resource);
        return;
      }
    } catch (URISyntaxException invalid) {
      // Preserve invalid source text without claiming an IRI or an authority code.
    }
    distribution.addProperty(property.getProperty(), model.createLiteral(property.getValue()));
  }

  /**
   * Serialize dct standard.
   *
   * @param standardList   the standard list
   * @param parentResource the parent resource
   * @param model          the model
   */
  protected static void serializeDctStandard(List<DctStandard> standardList,
      Resource parentResource, Model model) {

    if (standardList != null && !standardList.isEmpty()) {
      standardList.stream().filter(standard -> standard != null).forEach(standard -> {

        Resource standardResource = null;
        // Connectors use the dct:conformsTo property IRI as a placeholder "uri" and keep the
        // standard IRI in "identifier": the placeholder is never the standard itself.
        String uri = DCTerms.conformsTo.getURI().equals(standard.getUri()) ? null
            : standard.getUri();
        String identifier = standard.getIdentifier() == null ? null
            : standard.getIdentifier().getValue();
        if (StringUtils.isBlank(uri) && isValidUri(identifier)) {
          uri = identifier.trim();
        }

        if (StringUtils.isNotBlank(uri)) {
          standardResource = model.createResource(uri, DctStandard.getRdfClass());
        } else {
          standardResource = model.createResource(DctStandard.getRdfClass());
        }

        for (DcatProperty p : Arrays.asList(standard.getDescription(), standard.getIdentifier(),
            standard.getTitle())) {
          if (p != null && StringUtils.isNotBlank(p.getValue())) {
            standardResource.addLiteral(p.getProperty(), p.getValue());
          }
        }

        for (DcatProperty p : standard.getReferenceDocumentation()) {
          if (p != null && StringUtils.isNotBlank(p.getValue())) {
            standardResource.addProperty(p.getProperty(), p.getValue(), XSDDateType.XSDanyURI);
          }
        }

        parentResource.addProperty(DCTerms.conformsTo, standardResource);

      });
    }

  }

  /**
   * Adds the dcat property as resource.
   *
   * @param property       the property
   * @param parentResource the parent resource
   * @param model          the model
   * @param useRange       the use range
   */
  /*
   * Helper method used to add a Property, which should have a Resource as its
   * Object, to a passed Resource e.g. hasUrl, hasEmail or hasTelephone -> type
   * Optionally use the DCATProperty Range to create a typed Resource
   */
  protected static void addDcatPropertyAsResource(DcatProperty property, Resource parentResource,
      Model model, boolean useRange) {
    if (property == null) return;
    if (StringUtils.isNotBlank(property.getValue())) {
      String normalizedUri = normalizeUri(property.getValue());
      try {
        if (StringUtils.isNotBlank(normalizedUri) && isValidUri(normalizedUri) && useRange) {
          parentResource.addProperty(property.getProperty(), model.createResource(
              iriFactory.construct(normalizedUri).toURI().toString(), property.getRange()));
        } else if (StringUtils.isNotBlank(normalizedUri) && isValidUri(normalizedUri)) {
          parentResource.addProperty(property.getProperty(),
              model.createResource(iriFactory.construct(normalizedUri).toURI().toString()));
        } else {
          parentResource.addProperty(property.getProperty(),
              model.createLiteral(property.getValue()));
        }
      } catch (ResourceRequiredException | URISyntaxException e) {

        // Add anyway the property as string value
        System.out.println("ERROR in addDcatPropertyAsResource for " + property.getProperty() + ": "
            + e.getMessage());
        parentResource.addProperty(property.getProperty(),
            model.createLiteral(property.getValue()));
      }
    }
  }

  /**
   * Adds the dcat property as literal.
   *
   * @param property       the property
   * @param parentResource the parent resource
   * @param model          the model
   */
  /*
   * Helper method used to add a Property, which should have a Literal as its
   * Object, to a passed Resource
   */
  protected static void addDcatPropertyAsLiteral(DcatProperty property, Resource parentResource,
      Model model) {
    if (property == null) return;
    // Add the property as literal value
    if (StringUtils.isNotBlank(property.getValue())) {
      String value = property.getValue();

      logger.info("Value: " + value);
      logger.info("Property: " + property.getProperty());
      logger.info("Property Value: " + property.getValue());
      parentResource.addProperty(property.getProperty(), property.getLanguage() != null
          ? model.createLiteral(property.getValue(), property.getLanguage()) : model.createLiteral(property.getValue()));
    }
  }

  /**
   * Adds dct:title and dct:description language variants from a list of {@link it.eng.idra.beans.dcat.DcatDetails}.
   * Falls back to single fallbackTitle/fallbackDescription when the details list is empty.
   * A row is emitted for each non-blank title/description; the row's language tag is preserved
   * when present (an empty/null language produces a plain literal, matching source RDF that
   * omits xml:lang).
   */
  protected static void addMultiLangTitleDescription(
      java.util.List<it.eng.idra.beans.dcat.DcatDetails> details,
      DcatProperty fallbackTitle, DcatProperty fallbackDescription,
      Resource parentResource, Model model) {

    if (details != null && !details.isEmpty()) {
      java.util.Set<String> titleLangs = new java.util.HashSet<>();
      java.util.Set<String> descLangs = new java.util.HashSet<>();
      for (it.eng.idra.beans.dcat.DcatDetails d : details) {
        if (d == null) continue;
        String lang = StringUtils.trimToNull(d.getLanguage());
        String langKey = lang == null ? "" : lang.toLowerCase();
        if (StringUtils.isNotBlank(d.getTitle()) && titleLangs.add(langKey + "|" + d.getTitle())) {
          if (lang != null) {
            try {
              parentResource.addProperty(DCTerms.title, model.createLiteral(d.getTitle(), lang));
            } catch (Exception e) {
              parentResource.addProperty(DCTerms.title, model.createLiteral(d.getTitle()));
            }
          } else {
            parentResource.addProperty(DCTerms.title, model.createLiteral(d.getTitle()));
          }
        }
        if (StringUtils.isNotBlank(d.getDescription()) && descLangs.add(langKey + "|" + d.getDescription())) {
          if (lang != null) {
            try {
              parentResource.addProperty(DCTerms.description,
                  model.createLiteral(d.getDescription(), lang));
            } catch (Exception e) {
              parentResource.addProperty(DCTerms.description, model.createLiteral(d.getDescription()));
            }
          } else {
            parentResource.addProperty(DCTerms.description, model.createLiteral(d.getDescription()));
          }
        }
      }
      // The display fallback has no language metadata. Use it only when that
      // field has no detail values; otherwise it would invent a plain literal.
      if (fallbackTitle != null && StringUtils.isNotBlank(fallbackTitle.getValue())
          && titleLangs.isEmpty()) {
        parentResource.addProperty(DCTerms.title, model.createLiteral(fallbackTitle.getValue()));
      }
      if (fallbackDescription != null && StringUtils.isNotBlank(fallbackDescription.getValue())
          && descLangs.isEmpty()) {
        parentResource.addProperty(DCTerms.description,
            model.createLiteral(fallbackDescription.getValue()));
      }
    } else {
      addDcatPropertyAsLiteral(fallbackTitle, parentResource, model);
      addDcatPropertyAsLiteral(fallbackDescription, parentResource, model);
    }
  }

  /**
   * Adds the dcat property as typed literal.
   *
   * @param property       the property
   * @param dataType       the data type
   * @param parentResource the parent resource
   * @param model          the model
   */
  /*
   * Helper method used to add a Property, which should have a Typed Literal as
   * its Object, to a passed Resource
   */
  protected static void addDcatPropertyAsTypedLiteral(DcatProperty property, RDFDatatype dataType,
      Resource parentResource, Model model) {

    // Add the property as literal value
    if (StringUtils.isNotBlank(property.getValue())) {
      parentResource.addProperty(property.getProperty(), property.getValue(), dataType);
    }
  }

  /**
   * DCAT-AP 3.0.1 ranges: an object of one of these properties is an instance of the range class.
   * Agents and concepts are left out: their class brings mandatory names and labels that a source
   * without them cannot provide.
   */
  private static final Map<Property, Resource> RANGE_CLASSES = Map.ofEntries(
      Map.entry(DCTerms.language, DCTerms.LinguisticSystem),
      Map.entry(DCTerms.format, DCTerms.MediaTypeOrExtent),
      Map.entry(DCAT.mediaType, DCTerms.MediaType),
      Map.entry(ResourceFactory.createProperty(DCAT.NS + "compressFormat"), DCTerms.MediaType),
      Map.entry(ResourceFactory.createProperty(DCAT.NS + "packageFormat"), DCTerms.MediaType),
      Map.entry(DCTerms.accrualPeriodicity, DCTerms.Frequency),
      Map.entry(DCTerms.license, DCTerms.LicenseDocument),
      Map.entry(DCTerms.rights, DCTerms.RightsStatement),
      Map.entry(DCTerms.accessRights, DCTerms.RightsStatement),
      Map.entry(DCAT.landingPage, FOAF.Document),
      Map.entry(FOAF.page, FOAF.Document),
      Map.entry(FOAF.homepage, FOAF.Document),
      Map.entry(DCTerms.spatial, DCTerms.Location),
      Map.entry(DCTerms.temporal, DCTerms.PeriodOfTime),
      Map.entry(DCTerms.conformsTo, DCTerms.Standard),
      Map.entry(DCTerms.provenance, DCTerms.ProvenanceStatement),
      Map.entry(ResourceFactory.createProperty("http://www.w3.org/ns/adms#identifier"),
          ResourceFactory.createResource("http://www.w3.org/ns/adms#Identifier")),
      Map.entry(ResourceFactory.createProperty("http://spdx.org/rdf/terms#checksum"),
          ResourceFactory.createResource("http://spdx.org/rdf/terms#Checksum")));

  /** Object properties whose DCAT-AP range is a class, often written as plain text by the sources. */
  private static final Map<Property, Resource> TEXT_AS_NODE = Map.of(
      DCTerms.accrualPeriodicity, DCTerms.Frequency,
      DCTerms.accessRights, DCTerms.RightsStatement,
      DCTerms.rights, DCTerms.RightsStatement,
      DCTerms.provenance, DCTerms.ProvenanceStatement,
      DCTerms.license, DCTerms.LicenseDocument,
      DCTerms.format, DCTerms.MediaTypeOrExtent,
      DCAT.mediaType, DCTerms.MediaType);

  /**
   * Text values of the properties above become what DCAT-AP expects, with the same content: an EU
   * file type or IANA media type when the text is one, otherwise a node of the range class labelled
   * with the text. Nothing is added or guessed beyond the text itself.
   */
  static void literalsToNodes(Model model) {
    TEXT_AS_NODE.forEach((property, range) -> {
      for (Statement st : model.listStatements(null, property, (RDFNode) null).toList()) {
        if (!st.getObject().isLiteral()) {
          continue;
        }
        String text = st.getLiteral().getLexicalForm().trim();
        model.remove(st);
        if (text.isEmpty()) {
          continue;
        }
        String iri = property.equals(DCTerms.format) ? euFileType(text)
            : property.equals(DCTerms.accrualPeriodicity) && DctFrequency.fromLabel(text) != null
                ? FREQUENCY_BASE_URI + DctFrequency.fromLabel(text)
            : property.equals(DCAT.mediaType) ? ianaMediaType(text) : null;
        boolean described = iri == null && model.listObjectsOfProperty(st.getSubject(), property)
            .toList().stream().anyMatch(o -> o.isResource()
                && o.asResource().hasProperty(RDFS.label, text));
        if (iri != null) {
          st.getSubject().addProperty(property, model.createResource(iri, range));
        } else if (!described) {
          st.getSubject().addProperty(property, model.createResource(range).addProperty(RDFS.label, text));
        }
      }
    });
  }

  /**
   * Gives untyped objects of the properties above their DCAT-AP range class. Source triples copied
   * from the metadata envelope keep their values but often omit these classes.
   */
  static void addRangeClasses(Model model) {
    literalsToNodes(model);
    List<Statement> missing = new java.util.ArrayList<>();
    RANGE_CLASSES.forEach((property, range) -> model.listStatements(null, property, (RDFNode) null)
        .forEachRemaining(st -> {
          if (st.getObject().isResource() && !st.getResource().hasProperty(RDF.type)) {
            missing.add(model.createStatement(st.getResource(), RDF.type, range));
          }
        }));
    model.add(missing);
    // A source byte size written as text ("7753606") is the same number: DCAT-AP types it as
    // xsd:nonNegativeInteger. Other values are left as they are.
    List<Statement> sizes = model.listStatements(null, DCAT.byteSize, (RDFNode) null).toList();
    for (Statement st : sizes) {
      if (st.getObject().isLiteral()
          && !XSDDatatype.XSDnonNegativeInteger.getURI().equals(st.getLiteral().getDatatypeURI())
          && st.getLiteral().getLexicalForm().trim().matches("\\d+")) {
        model.remove(st);
        model.add(st.getSubject(), DCAT.byteSize, model.createTypedLiteral(
            new BigInteger(st.getLiteral().getLexicalForm().trim()).toString(),
            XSDDatatype.XSDnonNegativeInteger));
      }
    }
  }

  /**
   * Write model to string.
   *
   * @param model  the model
   * @param format the format
   * @return the string
   */
  static String writeModelToString(Model model, DcatApFormat format) {

    StringWriter outputWriter = new StringWriter();
    model.write(outputWriter, format.formatName());
    return outputWriter.toString();
  }

  /**
   * Write model to file and zip.
   *
   * @param model    the model
   * @param format   the format
   * @param filePath the file path
   * @param fileName the file name
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void writeModelToFileAndZip(Model model, DcatApFormat format, String filePath,
      String fileName) throws IOException {
    writeModelToFile(model, format, filePath, fileName);
    writeModelToZipFile(model, format, filePath, fileName);
  }

  /**
   * Write model to file.
   *
   * @param model    the model
   * @param format   the format
   * @param filePath the file path
   * @param fileName the file name
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void writeModelToFile(Model model, DcatApFormat format, String filePath,
      String fileName) throws IOException {
    /* This method is used to serialize the Jena-model to file. */

    Writer out = null;
    logger.info("Writing model to file: " + filePath + fileName);

    Instant tick = Instant.now();
    try {
      // Explicit UTF-8 — FileWriter uses the platform default charset, which on
      // Windows is typically cp1252 and corrupts non-ASCII RDF literals.
      out = new OutputStreamWriter(new FileOutputStream(filePath + fileName),
          StandardCharsets.UTF_8);
      model.write(out, format.name());
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    } finally {
      if (out != null) {
        out.close();
      }

      Instant tock = Instant.now();
      logger.info("File writing completed in: " + Duration.between(tick, tock).toString());

    }
  }

  /**
   * Write model to zip file.
   *
   * @param model    the model
   * @param format   the format
   * @param filePath the file path
   * @param fileName the file name
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void writeModelToZipFile(Model model, DcatApFormat format, String filePath,
      String fileName) throws IOException {

    logger.info("Writing model to file: " + filePath + fileName + ".zip");
    Instant tick = Instant.now();
    try {
      File f = new File(filePath + fileName + ".zip");
      ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f));
      ZipEntry e = new ZipEntry(fileName);
      out.putNextEntry(e);

      byte[] data = writeModelToString(model, format).getBytes(StandardCharsets.UTF_8);
      out.write(data, 0, data.length);
      out.closeEntry();
      out.close();

    } finally {
      Instant tock = Instant.now();
      logger.info("File writing completed in: " + Duration.between(tick, tock).toString());

    }
  }

  // public static String datasetToDCATAP(DCATDataset dataset, DCATAPFormat
  // format) {
  // return (writeModelToString(datasetToModel(dataset), format));
  // }

  // public static List<String> datasetsToDCATAPList(List<DCATDataset> datasets,
  // DCATAPFormat format) {
  // return datasets.stream().map(item -> writeModelToString(datasetToModel(item),
  // format))
  // .collect(Collectors.toList());
  // }

  /**
   * Search result to dcat ap.
   *
   * @param result    the result
   * @param format    the format
   * @param profile   the profile
   * @param writeType the write type
   * @return the string
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static String searchResultToDcatAp(SearchResult result, DcatApFormat format,
      DcatApProfile profile, DcatApWriteType writeType) throws IOException {

    nodeResources = FederationCore.getOdmsCatalogues().stream()
        .collect(Collectors.toMap(OdmsCatalogue::getId, node -> node));

    Model model = datasetsToModel(result.getResults(), profile);
    // FILE is the complete federation export; paged search results stay scoped to their matches.
    if(writeType.equals(DcatApWriteType.FILE)) for(OdmsCatalogue node:nodeResources.values())
      if(Boolean.TRUE.equals(node.isActive())) addStandaloneCatalogueToModel(node,model);
    addRangeClasses(model);

    model.setNsPrefix("co", "http://purl.org/ontology/co/core#");
    model.createResource("http://purl.org/ontology/co/core#Counter")
        .addProperty(DCTerms.description, "The total count of matching datasets").addLiteral(
            model.createProperty("http://purl.org/ontology/co/core#count"), result.getCount());

    if (writeType.equals(DcatApWriteType.FILE)) {

      writeModelToFileAndZip(model, format, filePath, fileName);
      return writeModelToString(model, format);
      // } else if (writeType.equals(DCATAPWriteType.ZIPFILE)) {
      //
      // writeModelToZipFile(model, format,
      // PropertyManager.getProperty(IdraProperty.DUMP_FILE_PATH),
      // "datasetDump");
      // return "";
    } else {

      return writeModelToString(model, format);
    }

  }

  /**
   * Search result to dcat ap by node.
   *
   * @param nodeId    the node id
   * @param result    the result
   * @param format    the format
   * @param profile   the profile
   * @param writeType the write type
   * @return the string
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static String searchResultToDcatApByNode(String nodeId, SearchResult result,
      DcatApFormat format, DcatApProfile profile, DcatApWriteType writeType) throws IOException {

    nodeResources = FederationCore.getOdmsCatalogues().stream()
        .collect(Collectors.toMap(OdmsCatalogue::getId, node -> node));

    Model model = datasetsToModel(result.getResults(), profile);
    OdmsCatalogue node=nodeResources.get(Integer.valueOf(nodeId));
    if(node!=null) addStandaloneCatalogueToModel(node,model);
    addRangeClasses(model);

    model.setNsPrefix("co", "http://purl.org/ontology/co/core#");
    model.createResource("http://purl.org/ontology/co/core#Counter")
        .addProperty(DCTerms.description, "The total count of matching datasets").addLiteral(
            model.createProperty("http://purl.org/ontology/co/core#count"), result.getCount());

    if (writeType.equals(DcatApWriteType.FILE)) {

      writeModelToFileAndZip(model, format, filePath, fileName + "_node_" + nodeId);

      return writeModelToString(model, format);
      // } else if (writeType.equals(DCATAPWriteType.ZIPFILE)) {
      //
      // writeModelToZipFile(model, format,
      // PropertyManager.getProperty(IdraProperty.DUMP_FILE_PATH),
      // "datasetDump");
      // return "";
    } else {

      return writeModelToString(model, format);
    }

  }

  /**
   * Checks if is valid uri.
   *
   * @param uri the uri
   * @return true, if is valid uri
   */
  public static void addStandaloneCatalogueToModel(OdmsCatalogue node,Model model) {
    if(node.getStandaloneRdf()==null || node.getStandaloneRdf().isBlank()) return;
    org.apache.jena.riot.RDFParser.fromString(node.getStandaloneRdf())
        .lang(org.apache.jena.riot.Lang.TURTLE).parse(model);
  }

  protected static boolean isValidUri(String uri) {
    if (uri == null || uri.trim().isEmpty()) {
      return false;
    }

    uri = normalizeUri(uri);
    if (StringUtils.isBlank(uri)) {
      return false;
    }

    try {
      // Basic URI check
      URI parsedUri = new URI(uri);
      String scheme = parsedUri.getScheme();
      boolean isHttp = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));

      // Additional IRI spec validation if needed
      boolean hasIriViolations = iriFactory.create(uri).hasViolation(false);

      return isHttp && !hasIriViolations;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Removes common wrappers and surrounding whitespace from URI candidates.
   *
   * @param uri raw uri value
   * @return normalized uri candidate
   */
  protected static String normalizeUri(String uri) {
    if (uri == null) {
      return null;
    }

    String normalized = uri.trim();

    if (normalized.startsWith("<") && normalized.endsWith(">") && normalized.length() > 2) {
      normalized = normalized.substring(1, normalized.length() - 1).trim();
    }

    return normalized;
  }

  /**
   * Converts a URI candidate to a canonical string safe for Jena resource creation.
   *
   * @param uri raw uri value
   * @return canonical uri or null when invalid
   */
  protected static String toSafeResourceUri(String uri) {
    if (!isValidUri(uri)) {
      return null;
    }

    try {
      return iriFactory.construct(normalizeUri(uri)).toURI().toString();
    } catch (Exception e) {
      return null;
    }
  }

}
