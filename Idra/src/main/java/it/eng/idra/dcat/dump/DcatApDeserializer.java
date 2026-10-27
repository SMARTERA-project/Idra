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

import it.eng.idra.beans.dcat.DCATAP;
import it.eng.idra.beans.dcat.DcatApFormat;
import it.eng.idra.beans.dcat.DcatApProfileNotValidException;
import it.eng.idra.beans.dcat.DcatDataService;
import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.beans.dcat.DcatProperty;
import it.eng.idra.beans.dcat.DcatDatasetSeries;
import it.eng.idra.beans.dcat.DcatDetails;
import it.eng.idra.beans.dcat.DcatDistribution;
import it.eng.idra.beans.dcat.DcatKeyword;
import it.eng.idra.beans.dcat.DctLicenseDocument;
import it.eng.idra.beans.dcat.DctLocation;
import it.eng.idra.beans.dcat.DctPeriodOfTime;
import it.eng.idra.beans.dcat.DctStandard;
import it.eng.idra.beans.dcat.FoafAgent;
import it.eng.idra.beans.dcat.Relationship;
import it.eng.idra.beans.dcat.SkosConcept;
import it.eng.idra.beans.dcat.SkosConceptStatus;
import it.eng.idra.beans.dcat.SkosConceptSubject;
import it.eng.idra.beans.dcat.SkosConceptTheme;
import it.eng.idra.beans.dcat.SkosPrefLabel;
import it.eng.idra.beans.dcat.SpdxChecksum;
import it.eng.idra.beans.dcat.VcardOrganization;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.management.FederationCore;
import it.eng.idra.utils.CommonUtil;
import it.eng.idra.utils.DcatDetailsUtil;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.LiteralRequiredException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.ResourceRequiredException;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.VCARD4;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO: Auto-generated Javadoc
/**
 * The Class DcatApDeserializer.
 */
public class DcatApDeserializer implements IdcatApDeserialize {

  /** The logger. */
  protected static Logger logger = LogManager.getLogger(DcatApDeserializer.class);

  /** The Constant rdfDatasetPattern. */
  protected static final Pattern rdfDatasetPattern = Pattern
      .compile("\\w*<dcat:Dataset rdf:about=\\\"(.*)\\\"");

  /** The Constant turtleDatasetPattern. */
  protected static final Pattern turtleDatasetPattern = Pattern
      .compile("<(.*)>\\R\\s*a dcat:Dataset");

  /** The Constant THEME_BASE_URI. */
  protected static final String THEME_BASE_URI = "http://publications.europa.eu/resource/authority/data-theme/";

  /** The Constant GEO_BASE_URI. */
  protected static final String GEO_BASE_URI = "http://publications.europa.eu/mdr/authority/place";

  /** The Constant GEO_BASE_URI_ALT. */
  protected static final String GEO_BASE_URI_ALT = "http://www.geonames.org";

  /**
   * Instantiates a new dcat ap deserializer.
   */
  public DcatApDeserializer() {
  }

  /**
   * Instantiates a new dcat ap deserializer.
   *
   * @param modelText the model text
   * @param node      the node
   * @return the model
   * @throws RiotException the riot exception
   */
  public Model dumpToModel(String modelText, OdmsCatalogue node) throws RiotException {

    String nodeBaseUri = node.getHost();
    // create an empty model
    Model model = ModelFactory.createDefaultModel();
    for (DcatApFormat format : DcatApFormat.values()) {
      try {
        model.read(new ByteArrayInputStream(modelText.getBytes(StandardCharsets.UTF_8)),
            nodeBaseUri, format.formatName());
        node.setDcatFormat(format);
        break;
      } catch (RiotException e) {
        if (!e.getMessage().contains("Content is not allowed in prolog") && !e.getMessage()
            .contains("[line: 1, col: 1 ] " + "Expected BNode or IRI: Got: [DIRECTIVE:prefix]")) {
          throw e;
        } else {
          continue;
        }
      }
    }
    return model;
  }

  /**
   * Instantiates a new dcat ap deserializer.
   *
   * @param nodeId          the node id
   * @param datasetResource the dataset resource
   * @return the dcat dataset
   * @throws DcatApProfileNotValidException the dcat ap profile not valid
   *                                        exception
   */
  public DcatDataset resourceToDataset(String nodeId, Resource datasetResource)
      throws DcatApProfileNotValidException {
    // Properties to be mapped among different CKAN fallback fields

    String title = null;
    String description = null;
    String releaseDate = null;
    String updateDate = null;
    String version = null;
    List<SkosConceptTheme> theme = new ArrayList<SkosConceptTheme>();
    List<String> keywords = new ArrayList<String>();
    List<String> documentation = new ArrayList<String>();
    List<String> sample = new ArrayList<String>();
    List<String> versionNotes = new ArrayList<String>();
    List<String> relatedResource = new ArrayList<String>();

    List<DcatDistribution> distributionList = new ArrayList<DcatDistribution>();

    // new
    List<String> applicableLegislation = new ArrayList<String>();
    List<DctLocation> geographicalCoverage = new ArrayList<DctLocation>();
    List<DcatDetails> titles = new ArrayList<DcatDetails>();
    List<DcatDetails> descriptions = new ArrayList<DcatDetails>();
    List<DctPeriodOfTime> temporalCoverageList = new ArrayList<DctPeriodOfTime>();
    List<DcatDatasetSeries> inSeries = new ArrayList<DcatDatasetSeries>();
    List<Relationship> qualifiedRelation = new ArrayList<Relationship>();
    String temporalResolution = null;
    List<String> wasGeneratedBy = new ArrayList<String>();
    List<String> HVDCategory = new ArrayList<String>();

    if (datasetResource.hasProperty(DCTerms.title)) {
      title = datasetResource.getRequiredProperty(DCTerms.title).getString();
    }
    if (datasetResource.hasProperty(DCTerms.description)) {
      description = datasetResource.getRequiredProperty(DCTerms.description).getString();
    }

    theme = deserializeConcept(nodeId, datasetResource, DCAT.theme, SkosConceptTheme.class);
    // dct:subject — modelled the same way as theme (SKOS concepts).
    List<SkosConceptSubject> subjects = deserializeConcept(nodeId, datasetResource,
        DCTerms.subject, SkosConceptSubject.class);
    FoafAgent publisher = null;
    publisher = deserializeFoafAgent(nodeId, datasetResource.getProperty(DCTerms.publisher));
    List<VcardOrganization> contactPointList = null;
    contactPointList = deserializeContactPoint(nodeId, datasetResource);

    List<DcatKeyword> keywordDetails =
        DcatDetailsUtil.extractKeywordDetails(datasetResource, DCAT.keyword);
    for (DcatKeyword keywordDetail : keywordDetails) {
      if (keywordDetail != null && StringUtils.isNotBlank(keywordDetail.getValue())) {
        keywords.add(keywordDetail.getValue());
      }
    }
    List<DctStandard> conformsTo = null;
    conformsTo = deserializeDctStandard(nodeId, datasetResource);
    String accessRights = null;
    if (datasetResource.hasProperty(DCTerms.accessRights)) {
      Statement accessRightsStmt = datasetResource.getProperty(DCTerms.accessRights);
      try {
        accessRights = accessRightsStmt.getString();
      } catch (LiteralRequiredException e) {
        accessRights = accessRightsStmt.getResource().getURI();
      }
    }

    // Iterate over documentation properties
    StmtIterator dit = datasetResource.listProperties(FOAF.page);
    while (dit.hasNext()) {
      String documentationValue = getStatementValue(dit.next());
      if (StringUtils.isNotBlank(documentationValue)) {
        documentation.add(documentationValue);
      }
    }

    // Iterate over related properties
    StmtIterator relIt = datasetResource.listProperties(DCTerms.relation);
    while (relIt.hasNext()) {
      String related = getStatementValue(relIt.next());
      if (StringUtils.isNotBlank(related)) {
        relatedResource.add(related);
      }
    }
    String frequency = null;
    frequency = deserializeFrequency(datasetResource);

    // Iterate over hasVersion properties
    List<String> hasVersion = new ArrayList<String>();
    StmtIterator hasVit = datasetResource.listProperties(DCTerms.hasVersion);
    while (hasVit.hasNext()) {
      String hasVersionValue = getStatementValue(hasVit.next());
      if (StringUtils.isNotBlank(hasVersionValue)) {
        hasVersion.add(hasVersionValue);
      }
    }

    // Iterate over isVersionOf properties
    List<String> isVersionOf = new ArrayList<String>();
    StmtIterator isVit = datasetResource.listProperties(DCTerms.isVersionOf);
    while (isVit.hasNext()) {
      Statement isV = isVit.next();
      try {
        isVersionOf.add(isV.getString());
      } catch (LiteralRequiredException e) {
        isVersionOf.add(isV.getResource().getURI());
      }
    }

    // dcat:landingPage is optional: only the source value (IRI or literal URL) is kept,
    // the dataset IRI is never used in its place.
    String landingPage = "";
    Statement landingStatement = datasetResource.getProperty(DCAT.landingPage);
    if (landingStatement != null) {
      RDFNode landingNode = landingStatement.getObject();
      landingPage = landingNode.isURIResource() ? landingNode.asResource().getURI()
          : landingNode.isLiteral() ? landingNode.asLiteral().getLexicalForm().trim() : "";
    }
    List<String> language = null;
    language = deserializeLanguage(datasetResource);

    // Iterate over provenance properties
    List<String> provenance = new ArrayList<String>();
    StmtIterator provIt = datasetResource.listProperties(DCTerms.provenance);
    while (provIt.hasNext()) {
      String provenanceValue = getStatementValue(provIt.next());
      if (StringUtils.isNotBlank(provenanceValue)) {
        provenance.add(provenanceValue);
      }
    }

    if (datasetResource.hasProperty(DCTerms.issued)) {
      releaseDate = extractDate(datasetResource.getProperty(DCTerms.issued));
    }

    if (datasetResource.hasProperty(DCTerms.modified)) {
      updateDate = extractDate(datasetResource.getProperty(DCTerms.modified));
    }

    String identifier = null;
    if (datasetResource.hasProperty(DCTerms.identifier)) {
      identifier = getStatementValue(datasetResource.getProperty(DCTerms.identifier));
    } else {
      identifier = landingPage;
    }

    // Iterate over otherIdentifier properties
    List<String> otherIdentifier = null;
    otherIdentifier = deserializeOtherIdentifier(datasetResource);

    // Iterate over sample properties
    StmtIterator sampleIt = datasetResource
        .listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/adms#sample"));
    while (sampleIt.hasNext()) {
      String sampleValue = getStatementValue(sampleIt.next());
      if (StringUtils.isNotBlank(sampleValue)) {
        sample.add(sampleValue);
      }
    }

    // Iterate over source properties
    List<String> source = new ArrayList<String>();
    StmtIterator sourceIt = datasetResource.listProperties(DCTerms.source);
    while (sourceIt.hasNext()) {
      Statement sourceStm = sourceIt.next();
      try {
        source.add(sourceStm.getString());
      } catch (LiteralRequiredException e) {
        source.add(sourceStm.getResource().getURI());
      }
    }

    // Handle spatial property — list ALL dct:spatial values, not just the first.
    List<DctLocation> spatialCoverageList = deserializeAllSpatialCoverage(nodeId, datasetResource);

    // Handle temporal property
    DctPeriodOfTime temporalCoverage = null;
    temporalCoverage = deserializeTemporalCoverage(nodeId, datasetResource);
    String type = null;
    if (datasetResource.hasProperty(DCTerms.type)) {
      type = getStatementValue(datasetResource.getProperty(DCTerms.type));
    }

    if (datasetResource.hasProperty(OWL.versionInfo)) {
      version = datasetResource.getProperty(OWL.versionInfo).getString();
    }

    // Iterate over versionNotes properties
    StmtIterator vnotesIt = datasetResource
        .listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/adms#versionNotes"));
    while (vnotesIt.hasNext()) {
      String versionNotesValue = getStatementValue(vnotesIt.next());
      if (StringUtils.isNotBlank(versionNotesValue)) {
        versionNotes.add(versionNotesValue);
      }
    }

    // Handle distributions
    StmtIterator distrIt = datasetResource.listProperties(DCAT.distribution);
    while (distrIt.hasNext()) {
      distributionList.add(resourceToDcatDistribution(distrIt.next().getResource(), nodeId));
    }

    // Iterate over applicableLegislation properties
    StmtIterator legIt = datasetResource.listProperties(DCATAP.applicableLegislation);
    while (legIt.hasNext()) {
      Statement stmt = legIt.next();
      try {
        applicableLegislation.add(stmt.getString());
        // logger.info("applicableLegislation: " + stmt.getString());
      } catch (LiteralRequiredException e) {
        applicableLegislation.add(stmt.getResource().getURI());
        // logger.info("applicableLegislation: " + stmt.getResource().getURI());
      }
    }

    // geographicalCoverage properties
    if (spatialCoverageList != null && !spatialCoverageList.isEmpty()) {
      geographicalCoverage.addAll(spatialCoverageList);
    }

    /*
     * DcatDetails dcatDetails = new DcatDetails();
     * dcatDetails.setTitle(title);
     * dcatDetails.setDescription(description);
     * // Handle titles
     * titles.add(dcatDetails);
     * // Handle descriptions
     * descriptions.add(dcatDetails);
     */

    // Handle temporalCoverage
    temporalCoverageList.add(temporalCoverage);

    // Iterate over inSeries properties and map them to DcatDatasetSeries objects
    /*
     * StmtIterator seriesIt = datasetResource
     * .listProperties(ResourceFactory.createProperty(
     * "http://www.w3.org/ns/dcat#inSeries"));
     * while (seriesIt.hasNext()) {
     * Statement stmt = seriesIt.next();
     * if (stmt.getObject().isResource()) {
     * // Create the DcatDatasetSeries object
     * inSeries.add(new DcatDatasetSeries(applicableLegislation, contactPointList,
     * descriptions,
     * frequency, geographicalCoverage, updateDate, publisher, releaseDate,
     * temporalCoverageList, titles, nodeId,
     * identifier));
     * }
     * }
     * 
     * // Handle qualifiedRelation
     * Relationship relationship = new
     * Relationship(datasetResource.getProperty(DCAT.hadRole).getString(),
     * datasetResource.getProperty(DCTerms.relation).getString(),nodeId);
     */
    // qualifiedRelation.add(relationship);

    // Iterate over qualifiedRelation properties
    // StmtIterator qrelIt = datasetResource
    // .listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/dcat#qualifiedRelation"));
    // while (qrelIt.hasNext()) {
    // qualifiedRelation.add(relationship);// qrelIt.next().getString()
    // }

    // Extract temporalResolution property
    if (datasetResource.hasProperty(DCAT.temporalResolution)) {
      try {
        temporalResolution = datasetResource.getProperty(DCAT.temporalResolution).getString();
      } catch (LiteralRequiredException e) {
        temporalResolution = datasetResource.getProperty(DCAT.temporalResolution).getResource().getURI();

      }
    }

    // Iterate over wasGeneratedBy properties
    StmtIterator wasGeneratedByIt = datasetResource
        .listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/prov#wasGeneratedBy"));
    while (wasGeneratedByIt.hasNext()) {
      Statement stmt = wasGeneratedByIt.next();
      try {
        wasGeneratedBy.add(stmt.getString());
      } catch (LiteralRequiredException e) {
        wasGeneratedBy.add(stmt.getResource().getURI());
      }
    }

    // Iterate over HVDCategory properties
    StmtIterator HVDCategoryIt = datasetResource.listProperties(DCATAP.hvdCategory);
    while (HVDCategoryIt.hasNext()) {
      Statement stmt = HVDCategoryIt.next();
      try {
        HVDCategory.add(stmt.getString());
      } catch (LiteralRequiredException e) {
        HVDCategory.add(stmt.getResource().getURI());
      }
    }

    // Iterate over qualifiedRelation properties
    if (datasetResource.hasProperty(DCAT.qualifiedRelation)) {
      StmtIterator qualifiedRelationIt = datasetResource.listProperties(DCAT.qualifiedRelation);
      while (qualifiedRelationIt.hasNext()) {
        Statement stmt = qualifiedRelationIt.next();
        if (stmt.getObject().isResource()) {
          Resource qualifiedRelationRes = stmt.getResource();

          String hadRole = null;
          if (qualifiedRelationRes.hasProperty(DCAT.hadRole)) {
            try {
              hadRole = qualifiedRelationRes.getProperty(DCAT.hadRole).getString();
            } catch (LiteralRequiredException e) {
              hadRole = qualifiedRelationRes.getProperty(DCAT.hadRole).getResource().getURI();
            }
          }

          String relation = null;
          if (qualifiedRelationRes.hasProperty(DCTerms.relation)) {
            try {
              relation = qualifiedRelationRes.getProperty(DCTerms.relation).getString();
            } catch (LiteralRequiredException e) {
              relation = qualifiedRelationRes.getProperty(DCTerms.relation).getResource().getURI();
            }
          }

          Relationship relationship = new Relationship(hadRole, relation, nodeId);
          qualifiedRelation.add(relationship);
        }
      }
    }

    DcatDataset mapped;
    mapped = new DcatDataset(nodeId, identifier, title, description, distributionList, theme,
        publisher, contactPointList, keywords, accessRights, conformsTo, documentation, frequency,
        hasVersion, isVersionOf, landingPage, language, provenance, releaseDate, updateDate,
        otherIdentifier, sample, source, geographicalCoverage, temporalCoverageList, type, version,
        versionNotes, null, null, subjects != null ? subjects : new ArrayList<SkosConceptSubject>(), relatedResource, applicableLegislation,
        inSeries, qualifiedRelation, temporalResolution, wasGeneratedBy, HVDCategory);
    DcatRdfMetadata.read(mapped, datasetResource, this);
    mapped.setKeywordDetails(keywordDetails);
    mapped.setDatasetDetails(
        DcatDetailsUtil.extractDatasetDetails(datasetResource, DCTerms.title, DCTerms.description));

    distributionList = null;
    contactPointList = null;
    publisher = null;
    conformsTo = null;
    spatialCoverageList = null;
    temporalCoverage = null;
    keywords = null;
    theme = null;
    documentation = null;
    relatedResource = null;
    hasVersion = null;
    isVersionOf = null;
    language = null;
    provenance = null;
    otherIdentifier = null;
    sample = null;
    source = null;
    versionNotes = null;
    applicableLegislation = null;
    inSeries = null;
    qualifiedRelation = null;
    temporalResolution = null;
    wasGeneratedBy = null;
    HVDCategory = null;

    DcatMetadataEnvelope.capture(mapped, datasetResource, "RDF:" + nodeId);
    return mapped;

  }

  /**
   * Extract date.
   *
   * @param dateStatement the date statement
   * @return the string
   */
  protected String extractDate(Statement dateStatement) {
    try {
      String lexical = dateStatement.getLiteral().getLexicalForm();
      // P1/P3: try ISO 8601 dateTime with timezone (xsd:dateTime).
      // CommonUtil.fromLocalToUtcDate lowercases the input, which breaks the
      // literal 'T' separator in its format pattern, causing all ISO dates to
      // parse as null and default to 1970-01-01T00:00:00Z in DcatDataset.
      try {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            ZonedDateTime.parse(lexical, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
      } catch (DateTimeParseException ignore) {
      }
      // Try ISO 8601 date only (xsd:date) — normalize to midnight UTC
      try {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            LocalDate.parse(lexical, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC));
      } catch (DateTimeParseException ignore) {
      }
      // Fallback for non-ISO formats (locale-specific, legacy catalogues)
      return CommonUtil.fromLocalToUtcDate(CommonUtil.fixBadUtcDate(lexical), null);
    } catch (Exception ignore) {
      return null;
    }

  }

  /**
   * Gets statement value as literal string when available, otherwise as resource URI.
   *
   * @param statement the statement
   * @return the statement value or null
   */
  protected String getStatementValue(Statement statement) {
    if (statement == null) {
      return null;
    }
    try {
      return statement.getString();
    } catch (LiteralRequiredException e) {
      try {
        Resource res = statement.getResource();
        return res != null ? res.getURI() : null;
      } catch (ResourceRequiredException ignore) {
        return null;
      }
    }
  }

  /**
   * Deserialize concept.
   *
   * @param <T>            the generic type
   * @param nodeId         the node ID
   * @param parentResource the parent resource
   * @param toExtractP     the to extract P
   * @param type           the type
   * @return the list
   */

  public <T extends SkosConcept> List<T> deserializeConcept(String nodeId, Resource parentResource,
      Property toExtractP, Class<T> type) {
    List<T> conceptList = new ArrayList<T>();

    Resource conceptR = null;

    // Iterate over concept properties
    StmtIterator conceptIt = parentResource.listProperties(toExtractP);
    while (conceptIt.hasNext()) {

      List<SkosPrefLabel> labelList = null;
      String conceptUri = null;

      conceptR = conceptIt.next().getResource();
      if (conceptR != null && StringUtils.isNotBlank(conceptUri = conceptR.getURI())) {

        if (conceptR.hasProperty(SKOS.prefLabel)) {

          labelList = new ArrayList<SkosPrefLabel>();
          StmtIterator labelIt = conceptR.listProperties(SKOS.prefLabel);
          while (labelIt.hasNext()) {
            Statement labelS = labelIt.next();
            labelList.add(new SkosPrefLabel(labelS.getLanguage(), labelS.getString(), nodeId));
          }

          // For theme, the label is the Final label. e.g.
          // http://publications.europa.eu/resource/authority/data-theme/GOVE
        } else if (toExtractP.getURI().equals(DCAT.theme.getURI())) {
          String extractedLabel = extractThemeFromUri(conceptUri);
          labelList = new ArrayList<SkosPrefLabel>();
          String knownLabel = FederationCore.getEnglishDcatTheme(extractedLabel);
          if (StringUtils.isNotBlank(knownLabel)) {
            labelList.add(new SkosPrefLabel("ENG", knownLabel, nodeId));
          }

          // For subject, the label is the entire URI. e.g. http://eurovoc.europa.eu/106
        } else if (toExtractP.getURI().equals(DCTerms.subject.getURI())) {
          labelList = new ArrayList<SkosPrefLabel>();
        }

        try {
          conceptList.add(type.getDeclaredConstructor(SkosConcept.class)
              .newInstance(new SkosConcept(toExtractP.getURI(), conceptUri, labelList, nodeId)));
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
            | InvocationTargetException | NoSuchMethodException | SecurityException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }
    return conceptList;
  }

  /**
   * Deserialize language.
   *
   * @param datasetResource the dataset resource
   * @return the list
   */
  public List<String> deserializeLanguage(Resource datasetResource) {

    List<String> language = new ArrayList<String>();

    // Iterate over language properties
    StmtIterator it = datasetResource.listProperties(DCTerms.language);
    while (it.hasNext()) {
      Statement langStmt = it.next();
      try {
        String langLiteral = langStmt.getString();
        if (StringUtils.isNotBlank(langLiteral)) {
          language.add(langLiteral);
        }
      } catch (LiteralRequiredException e) {
        Resource languageR = langStmt.getResource();
        String languageUri = null;
        if (languageR != null && StringUtils.isNotBlank(languageUri = languageR.getURI())) {
          if (!IRIFactory.iriImplementation().create(languageUri).hasViolation(false)) {
            language.add(languageUri);
          } else {
            language.add(languageUri);
          }
        }
      }
    }
    return language;
  }

  /**
   * Deserialize temporal coverage.
   *
   * @param nodeId          the node ID
   * @param datasetResource the dataset resource
   * @return the dct period of time
   */
  public DctPeriodOfTime deserializeTemporalCoverage(String nodeId, Resource datasetResource) {

    String startDate = null;
    String endDate = null;
    Resource temporalResource = datasetResource.getPropertyResourceValue(DCTerms.temporal);
    String beginning = null;
    String end = null;

    if (temporalResource != null) {

      // schema.org/startDate fallback used by some DCAT-AP-Stat dumps that emit
      // schema:startDate on the PeriodOfTime instead of dcat:startDate.
      Property schemaStart = ResourceFactory.createProperty("http://schema.org/startDate");
      Property schemaEnd = ResourceFactory.createProperty("http://schema.org/endDate");

      if (temporalResource.hasProperty(DCAT.startDate)) {
        try {
          startDate = temporalResource.getProperty(DCAT.startDate).getString();
        } catch (LiteralRequiredException e) {
          startDate = temporalResource.getProperty(DCAT.startDate).getResource().getURI();
        }
      } else if (temporalResource.hasProperty(schemaStart)) {
        startDate = getStatementValue(temporalResource.getProperty(schemaStart));
      }

      if (temporalResource.hasProperty(DCAT.endDate)) {
        try {
          endDate = temporalResource.getProperty(DCAT.endDate).getString();
        } catch (LiteralRequiredException e) {
          endDate = temporalResource.getProperty(DCAT.endDate).getResource().getURI();
        }
      } else if (temporalResource.hasProperty(schemaEnd)) {
        endDate = getStatementValue(temporalResource.getProperty(schemaEnd));
      }

      /*
       * if (temporalResource
       * .hasProperty(ResourceFactory.createProperty("http://schema.org#startDate")))
       * {
       * startDate = temporalResource
       * .getProperty(ResourceFactory.createProperty("http://schema.org#startDate")).
       * getString();
       * }
       * 
       * if (temporalResource
       * .hasProperty(ResourceFactory.createProperty("http://schema.org#endDate"))) {
       * endDate = temporalResource
       * .getProperty(ResourceFactory.createProperty("http://schema.org#endDate")).
       * getString();
       * }
       */

      if (temporalResource
          .hasProperty(ResourceFactory.createProperty("https://www.w3.org/2006/time#hasBeginning"))) {
        try {
          beginning = temporalResource
              .getProperty(ResourceFactory.createProperty("https://www.w3.org/2006/time#hasBeginning")).getString();
        } catch (LiteralRequiredException e) {
          beginning = temporalResource
              .getProperty(ResourceFactory.createProperty("https://www.w3.org/2006/time#hasBeginning")).getResource()
              .getURI();
        }
      }

      if (temporalResource
          .hasProperty(ResourceFactory.createProperty("https://www.w3.org/2006/time#hasEnd"))) {
        try {
          end = temporalResource
              .getProperty(ResourceFactory.createProperty("https://www.w3.org/2006/time#hasEnd")).getString();
        } catch (LiteralRequiredException e) {
          end = temporalResource
              .getProperty(ResourceFactory.createProperty("https://www.w3.org/2006/time#hasEnd")).getResource()
              .getURI();
        }
      }

      return new DctPeriodOfTime(DCTerms.temporal.getURI(), startDate, endDate, nodeId, beginning, end);

    }

    return null;
  }

  /**
   * Deserialize spatial coverage.
   *
   * @param nodeId          the node ID
   * @param datasetResource the dataset resource
   * @return the dct location
   */
  public DctLocation deserializeSpatialCoverage(String nodeId, Resource datasetResource) {
    // Backward-compatible wrapper: return the first location only.
    List<DctLocation> all = deserializeAllSpatialCoverage(nodeId, datasetResource);
    return all.isEmpty() ? null : all.get(0);
  }

  /**
   * Deserialize all spatial coverages attached to the dataset.
   * DCAT-AP allows multiple dct:spatial properties (e.g. statistical datasets
   * referencing 30+ country URIs); the older single-value method silently dropped
   * everything except the first.
   *
   * @param nodeId          the node ID
   * @param datasetResource the dataset resource
   * @return the list of dct locations (empty if none)
   */
  public List<DctLocation> deserializeAllSpatialCoverage(String nodeId, Resource datasetResource) {
    List<DctLocation> locations = new ArrayList<DctLocation>();
    StmtIterator spatialIt = datasetResource.listProperties(DCTerms.spatial);
    while (spatialIt.hasNext()) {
      Statement st = spatialIt.next();
      if (!st.getObject().isResource()) {
        // Plain literal as dct:spatial (uncommon) — preserve as geographicalIdentifier.
        String literal = StringUtils.trimToNull(st.getObject().toString());
        if (literal != null) {
          locations.add(new DctLocation(DCTerms.spatial.getURI(), literal, null, null, nodeId,
              null, null));
        }
        continue;
      }
      Resource spatialResource = st.getResource();
      DctLocation loc = extractSpatialLocation(nodeId, spatialResource);
      if (loc != null) {
        locations.add(loc);
      }
    }
    return locations;
  }

  private DctLocation extractSpatialLocation(String nodeId, Resource spatialResource) {
    if (spatialResource == null) {
      return null;
    }
    String geographicalIdentifier = null;
    String geographicalName = null;
    String geometry = null;
    String bbox = null;
    String centroid = null;

    if (spatialResource.hasProperty(ResourceFactory
        .createProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso"))) {
      geographicalIdentifier = spatialResource
          .getProperty(ResourceFactory
              .createProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso"))
          .getString();
    }
    if (spatialResource
        .hasProperty(ResourceFactory.createProperty("http://www.w3.org/ns/locn#geometry"))) {
      geometry = spatialResource
          .getProperty(ResourceFactory.createProperty("http://www.w3.org/ns/locn#geometry"))
          .getString();
    }
    if (spatialResource.hasProperty(
        ResourceFactory.createProperty("http://www.w3.org/ns/locn#geographicalName"))) {
      Resource geoNameResource = spatialResource.getPropertyResourceValue(
          ResourceFactory.createProperty("http://www.w3.org/ns/locn#geographicalName"));
      if (geoNameResource != null) {
        geographicalName = geoNameResource.getURI();
      }
    }

    // Accept ANY URI as geographicalIdentifier instead of restricting to the older
    // GEO_BASE_URI / GEO_BASE_URI_ALT authority bases. Modern DCAT-AP-Stat dumps use
    // http://publications.europa.eu/resource/authority/country/* and many other URIs
    // that don't match the legacy prefixes.
    if (StringUtils.isBlank(geographicalIdentifier)) {
      String spatialResourceUri = spatialResource.getURI();
      if (StringUtils.isNotBlank(spatialResourceUri)) {
        geographicalIdentifier = spatialResourceUri;
      }
    }

    if (spatialResource.hasProperty(DCAT.bbox)) {
      try {
        bbox = spatialResource.getProperty(DCAT.bbox).getString();
      } catch (LiteralRequiredException e) {
        bbox = spatialResource.getProperty(DCAT.bbox).getResource().getURI();
      }
    }
    if (spatialResource.hasProperty(DCAT.centroid)) {
      try {
        centroid = spatialResource.getProperty(DCAT.centroid).getString();
      } catch (LiteralRequiredException e) {
        centroid = spatialResource.getProperty(DCAT.centroid).getResource().getURI();
      }
    }

    // Skip locations that carry no information.
    if (StringUtils.isBlank(geographicalIdentifier)
        && StringUtils.isBlank(geographicalName)
        && StringUtils.isBlank(geometry)
        && StringUtils.isBlank(bbox)
        && StringUtils.isBlank(centroid)) {
      return null;
    }
    return new DctLocation(DCTerms.spatial.getURI(), geographicalIdentifier, geographicalName,
        geometry, nodeId, bbox, centroid);
  }

  /**
   * Deserialize other identifier.
   *
   * @param datasetResource the dataset resource
   * @return the list
   */
  public List<String> deserializeOtherIdentifier(Resource datasetResource) {

    List<String> otherIdentifier = new ArrayList<String>();
    StmtIterator othIdIt = datasetResource
        .listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/adms#identifier"));
    while (othIdIt.hasNext()) {
      Statement st = othIdIt.next();

      try {
        String literal = st.getString();
        if (StringUtils.isNotBlank(literal)) {
          otherIdentifier.add(literal);
        }
      } catch (LiteralRequiredException e) {
        Resource othIdResource = st.getResource();
        if (othIdResource == null) {
          continue;
        }
        // Prefer the explicit SKOS notation when present (legacy DCAT-AP convention),
        // otherwise fall back to the resource URI itself — this is how DCAT-AP v3
        // dumps express DOI/handle identifiers (e.g. adms:Identifier rdf:about="https://doi.org/...").
        if (othIdResource.hasProperty(SKOS.notation)) {
          otherIdentifier.add(othIdResource.getProperty(SKOS.notation).getString());
        } else if (StringUtils.isNotBlank(othIdResource.getURI())) {
          otherIdentifier.add(othIdResource.getURI());
        }
      }

    }
    return otherIdentifier;
  }

  /**
   * Deserialize DCT standard.
   *
   * @param nodeId          the node ID
   * @param datasetResource the dataset resource
   * @return the list
   */
  public List<DctStandard> deserializeDctStandard(String nodeId, Resource datasetResource) {

    List<DctStandard> standardList = new ArrayList<DctStandard>();

    // Iterate over conformsTo/linked Schemas properties
    StmtIterator cit = datasetResource.listProperties(DCTerms.conformsTo);
    Property referenceProperty = ResourceFactory
        .createProperty(DcatApSerializer.DCATAP_IT_BASE_URI + "referenceDocumentation");

    while (cit.hasNext()) {

      String uri = null;
      String identifier = null;
      String toTitle = null;
      String toDescription = null;
      List<String> toReference = new ArrayList<String>();

      Resource standardResource = cit.next().getResource();
      uri = standardResource.getURI();
      if (standardResource.hasProperty(DCTerms.identifier)) {
        identifier = standardResource.getProperty(DCTerms.identifier).getString();
      }
      if (standardResource.hasProperty(DCTerms.title)) {
        toTitle = standardResource.getProperty(DCTerms.title).getString();
      }
      if (standardResource.hasProperty(DCTerms.description)) {
        toDescription = standardResource.getProperty(DCTerms.description).getString();
      }

      StmtIterator referenceIt = standardResource.listProperties(referenceProperty);
      while (referenceIt.hasNext()) {

        toReference.add(referenceIt.next().getString());
      }

      standardList
          .add(new DctStandard(uri, identifier, toTitle, toDescription, toReference, nodeId));
    }
    return standardList;
  }

  /**
   * Deserialize contact point.
   *
   * @param nodeId          the node ID
   * @param datasetResource the dataset resource
   * @return the list
   */
  public List<VcardOrganization> deserializeContactPoint(String nodeId, Resource datasetResource) {

    List<VcardOrganization> contactPointList = new ArrayList<VcardOrganization>();

    // Iterate over contact points
    StmtIterator cit = datasetResource.listProperties(DCAT.contactPoint);
    while (cit.hasNext()) {

      String vcardUri = null;
      String vcardFn = null;
      String vcardHasEmail = null;
      String vcardHasUrl = null;
      String vcardHasTelephoneValue = null;
      String vcardHasTelephoneType = null;

      Resource contactResource = cit.next().getResource();
      if (contactResource != null) {

        vcardUri = contactResource.getURI();
        if (contactResource.hasProperty(VCARD4.fn)) {
          vcardFn = contactResource.getProperty(VCARD4.fn).getString();
        }
        try {
          if (contactResource.hasProperty(VCARD4.hasEmail)) {
            vcardHasEmail = contactResource.getProperty(VCARD4.hasEmail).getResource().getURI();
          }

        } catch (ResourceRequiredException e) {
          vcardHasEmail = contactResource.getProperty(VCARD4.hasEmail).getString();
        }

        if (contactResource.hasProperty(VCARD4.hasURL)) {
          try {
            vcardHasUrl = contactResource.getProperty(VCARD4.hasURL).getString();
          } catch (LiteralRequiredException e) {
            vcardHasUrl = contactResource.getProperty(VCARD4.hasURL).getResource().getURI();
          }
        }

        if (contactResource.hasProperty(VCARD4.hasTelephone)) {
          try {
            vcardHasTelephoneValue = contactResource.getProperty(VCARD4.hasTelephone).getString();
          } catch (LiteralRequiredException e) {
            Resource hasTelephoneR = contactResource.getProperty(VCARD4.hasTelephone).getResource();
            if (hasTelephoneR != null) {
              if (hasTelephoneR.hasProperty(VCARD4.value)) {
                vcardHasTelephoneValue = hasTelephoneR.getProperty(VCARD4.value).getString();
              }
              if (hasTelephoneR.hasProperty(RDF.type)) {
                vcardHasTelephoneType = hasTelephoneR.getPropertyResourceValue(RDF.type).getURI();
              }
            }
          }
        }

        contactPointList.add(new VcardOrganization(DCAT.contactPoint.getURI(), vcardUri, vcardFn,
            vcardHasEmail, vcardHasUrl, vcardHasTelephoneValue, vcardHasTelephoneType, nodeId));
      }
    }
    return contactPointList;
  }

  /**
   * Deserialize FOAF agent.
   *
   * @param nodeId         the node ID
   * @param agentStatement the agent statement
   * @return the foaf agent
   */
  public FoafAgent deserializeFoafAgent(String nodeId, Statement agentStatement) {

    String agentIdentifier = null;
    String agentUri = null;
    String agentName = null;
    String agentMbox = null;
    String agentHomepage = null;
    String agentType = null;
    Resource agentResource = null;
    List<String> agentNames = new ArrayList<String>();
    List<String> agentLanguages = new ArrayList<String>();

    // An agent given only as a name keeps the name instead of failing the whole dataset.
    if (agentStatement != null && agentStatement.getObject().isLiteral()) {
      agentNames.add(agentStatement.getString());
      FoafAgent named = new FoafAgent(agentStatement.getPredicate().getURI(), null, agentNames, null, null,
          null, null, nodeId);
      named.setNameLanguages(java.util.Collections.singletonList(agentStatement.getLiteral().getLanguage()));
      return named;
    }

    if (agentStatement != null && (agentResource = agentStatement.getResource()) != null) {

      agentUri = agentResource.getURI();
      // Every foaf:name (DCAT-AP: one per language), with its language tag.
      for (Statement nameStatement : agentResource.listProperties(FOAF.name).toList()) {
        agentName = lexicalOrUri(nameStatement);
        if (agentName == null) continue;
        agentNames.add(agentName);
        agentLanguages.add(nameStatement.getObject().isLiteral() ? nameStatement.getLiteral().getLanguage() : "");
      }

      if (agentResource.hasProperty(FOAF.mbox)) {
        agentMbox = lexicalOrUri(agentResource.getProperty(FOAF.mbox));
      }

      if (agentResource.hasProperty(FOAF.homepage)) {
        Resource homepageR = agentResource.getPropertyResourceValue(FOAF.homepage);
        if (homepageR != null) {
          agentHomepage = homepageR.getURI();
        } else {
          agentHomepage = agentResource.getProperty(FOAF.homepage).getString();
        }
      }
      // DCAT-AP gives the agent type as a concept IRI (e.g. a publisher-type), older dumps as text.
      if (agentResource.hasProperty(DCTerms.type)) {
        agentType = lexicalOrUri(agentResource.getProperty(DCTerms.type));
      }
      if (agentResource.hasProperty(DCTerms.identifier)) {
        agentIdentifier = lexicalOrUri(agentResource.getProperty(DCTerms.identifier));
      }

      FoafAgent agent = new FoafAgent(agentStatement.getPredicate().getURI(), agentUri, agentNames, agentMbox,
          agentHomepage, agentType, agentIdentifier, nodeId);
      agent.setNameLanguages(agentLanguages);
      return agent;

    }
    return null;
  }

  /** Literal lexical form or IRI of a statement object; null for blank nodes. */
  private static String lexicalOrUri(Statement statement) {
    if (statement == null) {
      return null;
    }
    RDFNode object = statement.getObject();
    if (object.isLiteral()) {
      return object.asLiteral().getLexicalForm();
    }
    return object.isURIResource() ? object.asResource().getURI() : null;
  }

  /**
   * Deserialize frequency.
   *
   * @param datasetResource the dataset resource
   * @return the string
   */
  public String deserializeFrequency(Resource datasetResource) {
    String frequencyUri = null;
    if (datasetResource.hasProperty(DCTerms.accrualPeriodicity)) {
      Resource frequencyR = datasetResource.getPropertyResourceValue(DCTerms.accrualPeriodicity);

      if (frequencyR != null && StringUtils.isNotBlank(frequencyUri = frequencyR.getURI())) {
        if (!IRIFactory.iriImplementation().create(frequencyUri).hasViolation(false)) {
          return CommonUtil.extractFrequencyFromUri(frequencyUri);
        } else {
          return frequencyUri;
        }
      }
    }
    return null;
  }

  /**
   * DcatDistribution.
   *
   * @param r      the r
   * @param nodeId the node id
   * @return the dcat distribution
   */
  public DcatDataService resourceToDataService(Resource r,String nodeId) {
    DcatDataService service = new DcatDataService(
        resourceValues(r,DCATAP.applicableLegislation),deserializeContactPoint(nodeId,r),
        resourceValues(r,org.apache.jena.sparql.vocabulary.FOAF.page),
        resourceValues(r,DCAT.endpointDescription),resourceValues(r,DCAT.endpointURL),
        resourceValues(r,DCATAP.hvdCategory),one(r,DCTerms.license),resourceValues(r,DCTerms.rights),
        resourceValues(r,DCAT.servesDataset),one(r,DCTerms.title),nodeId);
    service.setResourceUri(r.isURIResource()?r.getURI():null);readServiceProjection(service,r,nodeId);return service;
  }

  /** DCAT-AP 3 DataService fields beyond the legacy constructor: multilingual text, keywords, themes, profiles, publisher. */
  void readServiceProjection(DcatDataService service,Resource r,String nodeId) {
    java.util.List<it.eng.idra.beans.dcat.DcatDetails> details=it.eng.idra.utils.DcatDetailsUtil.extractDatasetDetails(r,DCTerms.title,DCTerms.description);
    details.forEach(detail -> detail.setNodeId(nodeId));
    service.setDataServiceDetails(details);
    service.setKeywordDetails(it.eng.idra.utils.DcatDetailsUtil.extractKeywordDetails(r,DCAT.keyword));
    service.setTheme(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(r,DCAT.theme),DCAT.theme.getURI()));
    service.setConformsTo(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(r,DCTerms.conformsTo),DCTerms.conformsTo.getURI()));
    service.setLandingPage(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(r,DCAT.landingPage),DCAT.landingPage.getURI()));
    service.setFormat(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(r,DCTerms.format),DCTerms.format.getURI()));
    if(service.getDocumentation()==null || service.getDocumentation().stream().allMatch(p -> p==null || StringUtils.isBlank(p.getValue())))
      service.setDocumentation(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(r,org.apache.jena.sparql.vocabulary.FOAF.page),org.apache.jena.sparql.vocabulary.FOAF.page.getURI()));
    if(r.hasProperty(DCTerms.publisher))service.setPublisher(deserializeFoafAgent(nodeId,r.getProperty(DCTerms.publisher)));
  }

  private static String one(Resource r,org.apache.jena.rdf.model.Property p) {
    java.util.List<String> values=resourceValues(r,p);return values.isEmpty()?null:values.get(0);
  }

  public void readCatalogue(Model model,OdmsCatalogue node) {
    java.util.List<Resource> catalogues=model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,DCAT.Catalog).toList();
    Resource catalogue=null;
    for(Resource r:catalogues) if(r.isURIResource() && (r.getURI().equals(node.getHost()) || r.getURI().equals(node.getHomepage()))) catalogue=r;
    if(catalogue==null && catalogues.size()==1)catalogue=catalogues.get(0);
    if(catalogue==null && catalogues.size()>1) catalogue=rootCatalogue(model,catalogues);
    boolean noDatasets = !model.contains(null,org.apache.jena.vocabulary.RDF.type,DCAT.Dataset);
    if(catalogue==null && catalogues.size()>1) {
      if(noDatasets) throw new IllegalArgumentException("Ambiguous multi-catalogue dump without a matching catalogue");
      logger.warn("Catalogue {}: {} dcat:Catalog nodes and no single root; catalogue-level metadata (records, services, coverage) not read",
          node.getId(), catalogues.size());
      node.setSourceMetadata(null);
      return;
    }
    String nodeId=String.valueOf(node.getId());
    java.util.List<DcatDatasetSeries> standaloneSeries=new java.util.ArrayList<>();
    model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,
        model.createResource("http://www.w3.org/ns/dcat#DatasetSeries")).forEachRemaining(r -> {
      if(!model.contains(null,model.createProperty("http://www.w3.org/ns/dcat#inSeries"),r))
        standaloneSeries.add(DcatRdfMetadata.readSeries(r,nodeId,null,this));
    });
    node.setDatasetSeries(standaloneSeries);
    if(!standaloneSeries.isEmpty()) node.setDcatVersion(it.eng.idra.beans.dcat.DcatApVersion.DCAT_AP_3);
    node.setStandaloneRdf(noDatasets ? DcatApSerializer.writeModelToString(model,DcatApFormat.TURTLE) : null);
    if(catalogue==null) {
      java.util.List<DcatDataService> services=new java.util.ArrayList<>();
      model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,DCAT.DataService).forEachRemaining(r -> {
        if(!model.contains(null,DCAT.accessService,r))services.add(resourceToDataService(r,nodeId));
      });
      node.setService(services);node.setRecord(new java.util.ArrayList<>());
      node.setSourceMetadata(null);
      return;
    }
    node.setSourceMetadata(DcatCatalogueSource.capture(catalogue));
    node.setApplicableLegislation(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(catalogue,DCATAP.applicableLegislation),DCATAP.applicableLegislation.getURI()));
    if(catalogue.hasProperty(DCTerms.creator))node.setCreator(deserializeFoafAgent(nodeId,catalogue.getProperty(DCTerms.creator)));
    node.setGeographicalCoverage(deserializeAllSpatialCoverage(nodeId,catalogue));
    DctPeriodOfTime period=deserializeTemporalCoverage(nodeId,catalogue);
    node.setTemporalCoverage(period==null?new java.util.ArrayList<>():new java.util.ArrayList<>(java.util.Arrays.asList(period)));
    java.util.List<DcatDataService> services=new java.util.ArrayList<>();
    catalogue.listProperties(DCAT.service).forEachRemaining(st->{if(st.getObject().isResource())services.add(resourceToDataService(st.getResource(),nodeId));});
    if(noDatasets) model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,DCAT.DataService).forEachRemaining(r -> {
      if(!catalogueServicePresent(services,r) && !model.contains(null,DCAT.accessService,r)) services.add(resourceToDataService(r,nodeId));
    });
    node.setService(services);
    java.util.List<it.eng.idra.beans.dcat.DcatCatalogueRecord> records=new java.util.ArrayList<>();
    catalogue.listProperties(DCAT.record).forEachRemaining(st->{
      if(!st.getObject().isResource())return;
      Resource r=st.getResource();
      java.util.List<it.eng.idra.beans.dcat.DcatDetails> details=it.eng.idra.utils.DcatDetailsUtil.extractDatasetDetails(r,DCTerms.title,DCTerms.description);
      details.forEach(detail -> detail.setNodeId(nodeId));
      it.eng.idra.beans.dcat.DcatCatalogueRecord record=new it.eng.idra.beans.dcat.DcatCatalogueRecord(
          deserializeDctStandard(nodeId,r),one(r,org.apache.jena.rdf.model.ResourceFactory.createProperty("http://www.w3.org/ns/adms#status")),details,new java.util.ArrayList<>(),
          one(r,DCTerms.issued),one(r,DCTerms.modified),one(r,org.apache.jena.sparql.vocabulary.FOAF.primaryTopic),details,nodeId);
      record.setResourceUri(r.isURIResource()?r.getURI():null);
      Statement source=r.getProperty(DCTerms.source);
      // DCAT-AP: dct:source is the original CatalogRecord; an unnamed (blank) original has no identity to keep.
      if(source!=null && source.getObject().isURIResource())
        record.setSource(new it.eng.idra.beans.dcat.DcatProperty(DCTerms.source,DCAT.CatalogRecord,source.getResource().getURI()));
      record.setLanguages(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(r,DCTerms.language),DCTerms.language.getURI()));
      records.add(record);
    });
    node.setRecord(records);
  }

  /**
   * DCAT 3 dumps may reference other catalogues (dcat:catalog, dct:hasPart/isPartOf). The described catalogue
   * is the only one listing datasets, services or records; among several listing catalogues, the only one not
   * contained in another. Otherwise null (ambiguous).
   */
  static Resource rootCatalogue(Model model, java.util.List<Resource> catalogues) {
    java.util.List<Resource> listing=catalogues.stream()
        .filter(c -> c.hasProperty(DCAT.dataset) || c.hasProperty(DCAT.service) || c.hasProperty(DCAT.record))
        .collect(java.util.stream.Collectors.toList());
    if(listing.size()==1)return listing.get(0);
    java.util.List<Resource> candidates=listing.isEmpty()?catalogues:listing;
    java.util.Set<Resource> contained=new java.util.HashSet<>();
    for(Resource c:catalogues) {
      c.listProperties(DCAT.catalog).forEachRemaining(st -> {if(st.getObject().isResource())contained.add(st.getResource());});
      c.listProperties(DCTerms.hasPart).forEachRemaining(st -> {if(st.getObject().isResource())contained.add(st.getResource());});
      c.listProperties(DCTerms.isPartOf).forEachRemaining(st -> {if(st.getObject().isResource() && catalogues.contains(st.getResource()))contained.add(c);});
    }
    java.util.List<Resource> roots=candidates.stream().filter(c -> !contained.contains(c)).collect(java.util.stream.Collectors.toList());
    return roots.size()==1 ? roots.get(0) : null;
  }

  private static boolean catalogueServicePresent(java.util.List<DcatDataService> services,Resource resource) {
    return resource.isURIResource() && services.stream().anyMatch(s -> resource.getURI().equals(s.getResourceUri()));
  }

  private static java.util.List<String> resourceValues(Resource resource, org.apache.jena.rdf.model.Property predicate) {
    java.util.List<String> values=new java.util.ArrayList<>();
    resource.listProperties(predicate).forEachRemaining(s -> values.add(s.getObject().isURIResource() ? s.getResource().getURI()
        : s.getObject().isLiteral() ? s.getLiteral().getLexicalForm() : s.getObject().toString()));
    return values;
  }

  public DcatDistribution resourceToDcatDistribution(Resource r, String nodeId) {

    String accessUrl = null;
    String description = null;
    String format = null;
    String documentation = null;
    String downloadUrl = null;
    String releaseDate = null;
    String updateDate = null;
    SpdxChecksum checksum = null;
    String licenseUri = null;
    String licenseName = null;
    String licenseVersion = null;
    String licenseType = null;
    SkosConceptStatus status = null;

    // new
    List<DcatDataService> accessService = new ArrayList<DcatDataService>();
    List<String> applicableLegislation = new ArrayList<String>();
    String availability = null;
    String compressionFormat = null;
    String hasPolicy = null;
    String packagingFormat = null;
    String spatialResolution = null;
    String temporalResolution = null;
    List<String> documentationList = new ArrayList<String>();
    List<String> endpointDescriptionList = new ArrayList<String>();
    List<String> endpointUrlList = new ArrayList<String>();
    List<String> rightsList = new ArrayList<String>();

    // Manage required accessURL property
    if (r.hasProperty(DCAT.accessURL)) {
      Resource accessR = r.getPropertyResourceValue(DCAT.accessURL);
      if (accessR != null && StringUtils.isNotBlank(accessUrl = accessR.getURI())) {
        System.out.println(accessR.getURI());
      } else {
        throw new PropertyNotFoundException(DCAT.accessURL);
      }
    }

    if (r.hasProperty(DCTerms.description)) {
      description = getStatementValue(r.getProperty(DCTerms.description));
    }

    if (r.hasProperty(DCTerms.format)) {
      format = deserializeFormat(r);
    }

    DctLicenseDocument license = null;
    if (r.hasProperty(DCTerms.license)) {
      Resource licenseR = r.getPropertyResourceValue(DCTerms.license);

      licenseUri = licenseR.getURI();
      if (licenseR.hasProperty(FOAF.name)) {
        licenseName = licenseR.getProperty(FOAF.name).getString();
      }
      if (licenseR.hasProperty(DCTerms.type)) {
        licenseType = licenseR.getPropertyResourceValue(DCTerms.type).getURI();
      }
      if (licenseR.hasProperty(OWL.versionInfo)) {
        licenseVersion = licenseR.getProperty(OWL.versionInfo).getString();
      }
      license = new DctLicenseDocument(licenseUri, licenseName, licenseType, licenseVersion,
          nodeId);
    }
    String byteSize = null;
    if (r.hasProperty(DCAT.byteSize)) {
      byteSize = r.getProperty(DCAT.byteSize).getString();
    }

    if (r.hasProperty(ResourceFactory.createProperty("http://spdx.org/rdf/terms#checksum"))) {

      checksum = deserializeChecksum(nodeId, r);
    }

    if (r.hasProperty(FOAF.page)) {
      documentation = getStatementValue(r.getProperty(FOAF.page));
      if (StringUtils.isNotBlank(documentation)) {
        documentationList.add(documentation);
      }
    }
    // Manage downloadURL property
    if (r.hasProperty(DCAT.downloadURL)) {
      Resource downloadR = r.getPropertyResourceValue(DCAT.downloadURL);
      if (downloadR != null) {
        downloadUrl = downloadR.getURI();
      }
    }
    String language = null;
    if (r.hasProperty(DCTerms.language)) {
      try {
        language = r.getPropertyResourceValue(DCTerms.language).getURI();
      } catch (Exception ignore) {
        System.out.println(ignore.getLocalizedMessage());
      }
    }
    List<DctStandard> linkedSchemas = null;
    linkedSchemas = deserializeDctStandard(nodeId, r);
    String mediaType = null;
    if (r.hasProperty(DCAT.mediaType)) {
      mediaType = deserializeMediaType(r);
      // mediaType = r.getProperty(DCAT.mediaType).getString();
    }

    if (r.hasProperty(DCTerms.issued)) {
      releaseDate = extractDate(r.getProperty(DCTerms.issued));
    }
    if (r.hasProperty(DCTerms.modified)) {
      updateDate = extractDate(r.getProperty(DCTerms.modified));
    }

    String rights = null;
    if (r.hasProperty(DCTerms.rights)) {
      rights = getStatementValue(r.getProperty(DCTerms.rights));
    }

    try {
      status = deserializeConcept(nodeId, r,
          ResourceFactory.createProperty("http://www.w3.org/ns/adms#status"),
          SkosConceptStatus.class).get(0);
    } catch (IndexOutOfBoundsException ignore) {
      System.out.println(ignore.getLocalizedMessage());
    }
    String title = null;
    if (r.hasProperty(DCTerms.title)) {
      title = r.getProperty(DCTerms.title).getString();
    }

    // Handle applicableLegislation
    if (r.hasProperty(DCATAP.applicableLegislation)) {
      StmtIterator legIt = r.listProperties(DCATAP.applicableLegislation);
      while (legIt.hasNext()) {
        Statement stmt = legIt.next();
        try {
          applicableLegislation.add(stmt.getString());
          // logger.info("applicableLegislation: " + stmt.getString());
        } catch (LiteralRequiredException e) {
          applicableLegislation.add(stmt.getResource().getURI());
          // logger.info("applicableLegislation: " + stmt.getResource().getURI());
        }
      }
    }

    // Iterate over accessService properties
    if (r.hasProperty(DCAT.accessService)) {
      StmtIterator accessServiceIt = r.listProperties(DCAT.accessService);
      while (accessServiceIt.hasNext()) {
        Statement stmt = accessServiceIt.next();
        if (stmt.getObject().isResource()) {
          Resource serviceRes = stmt.getResource();

          // DataService ID (URI)
          // String dataServiceId = serviceRes.getURI();

          // contactPoint
          // List<VcardOrganization> contactPoints = deserializeContactPoint(nodeId,
          // serviceRes);

          // licence (dct:license)
          /*
           * String licence = null;
           * if (serviceRes.hasProperty(DCTerms.license)) {
           * try {
           * licence = serviceRes.getProperty(DCTerms.license).getString();
           * } catch (LiteralRequiredException e) {
           * licence = serviceRes.getProperty(DCTerms.license).getResource().getURI();
           * }
           * }
           */

          String titleAS = null;
          if (serviceRes.hasProperty(DCTerms.title)) {
            try {
              titleAS = serviceRes.getProperty(DCTerms.title).getString();
            } catch (LiteralRequiredException e) {
              titleAS = serviceRes.getProperty(DCTerms.title).getResource().getURI();
            }
          }

          // rights (dct:rights)
          if (serviceRes.hasProperty(DCTerms.accessRights)) {
            StmtIterator rightsIt = serviceRes.listProperties(DCTerms.accessRights);
            while (rightsIt.hasNext()) {
              Statement rightsStmt = rightsIt.next();
              try {
                rightsList.add(rightsStmt.getString());
              } catch (LiteralRequiredException e) {
                rightsList.add(rightsStmt.getResource().getURI());
              }
            }
          }
          // servesDataset (dcat:servesDataset)
          List<String> servesDataset = new ArrayList<>();
          if (serviceRes.hasProperty(DCAT.servesDataset)) {
            StmtIterator servesIt = serviceRes.listProperties(DCAT.servesDataset);
            while (servesIt.hasNext()) {
              Statement servesStmt = servesIt.next();
              try {
                servesDataset.add(servesStmt.getString());
              } catch (LiteralRequiredException e) {
                servesDataset.add(servesStmt.getResource().getURI());
              }
            }
          }
          // Extract endpointDescription property
          if (serviceRes.hasProperty(DCAT.endpointDescription)) {
            try {
              endpointDescriptionList.add(serviceRes.getProperty(DCAT.endpointDescription).getString());
            } catch (LiteralRequiredException e) {
              endpointDescriptionList.add(serviceRes.getProperty(DCAT.endpointDescription).getResource().getURI());
            }

          }

          // Extract endpointURL property
          if (serviceRes.hasProperty(DCAT.endpointURL)) {
            StmtIterator endpointUrlIt = serviceRes.listProperties(DCAT.endpointURL);
            while (endpointUrlIt.hasNext()) {
              Statement endpointUrlStmt = endpointUrlIt.next();
              try {
                endpointUrlList.add(endpointUrlStmt.getString());
              } catch (LiteralRequiredException e) {
                endpointUrlList.add(endpointUrlStmt.getResource().getURI());
              }
            }
          }

          DcatDataService dataService = new DcatDataService(
              null, // applicableLegislation,
              null,
              null, // documentationList,
              endpointDescriptionList,
              endpointUrlList,
              null,
              null,
              rightsList,
              servesDataset,
              titleAS,
              nodeId);

          dataService.setResourceUri(serviceRes.isURIResource() ? serviceRes.getURI() : null);
          dataService.setContactPoint(deserializeContactPoint(nodeId,serviceRes));
          dataService.setApplicableLegislation(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(serviceRes,DCATAP.applicableLegislation),DCATAP.applicableLegislation.getURI()));
          dataService.setHVDCategory(it.eng.idra.utils.DcatJsonMetadata.properties(resourceValues(serviceRes,DCATAP.hvdCategory),DCATAP.hvdCategory.getURI()));
          if(serviceRes.hasProperty(DCTerms.license)) dataService.setLicence(new DcatProperty(DCTerms.license,org.apache.jena.vocabulary.RDFS.Resource,getStatementValue(serviceRes.getProperty(DCTerms.license))));
          readServiceProjection(dataService,serviceRes,nodeId);
          accessService.add(dataService);
        }
      }
    }

    // Extract availability property
    if (r.hasProperty(DCATAP.availability)) {
      try {
        availability = r.getProperty(DCATAP.availability).getString();
      } catch (LiteralRequiredException e) {
        availability = r.getProperty(DCATAP.availability).getResource().getURI();
      }
    }

    // Extract compressionFormat property
    if (r.hasProperty(DCAT.compressFormat)) {
      try {
        compressionFormat = r.getProperty(DCAT.compressFormat).getString();
      } catch (LiteralRequiredException e) {
        compressionFormat = r.getProperty(DCAT.compressFormat).getResource().getURI();
      }
    }

    // Extract hasPolicy property
    if (r.hasProperty(ResourceFactory.createProperty("https://www.w3.org/ns/odrl/2/"))) {
      try {
        hasPolicy = r.getProperty(ResourceFactory.createProperty("https://www.w3.org/ns/odrl/2/")).getString();
      } catch (LiteralRequiredException e) {
        hasPolicy = r.getProperty(ResourceFactory.createProperty("https://www.w3.org/ns/odrl/2/")).getResource()
            .getURI();
      }
    }

    // Extract packagingFormat property
    if (r.hasProperty(DCAT.packageFormat)) {
      try {
        packagingFormat = r.getProperty(DCAT.packageFormat).getString();
      } catch (LiteralRequiredException e) {
        packagingFormat = r.getProperty(DCAT.packageFormat).getResource().getURI();
      }
    }

    // Extract spatialResolution property
    if (r.hasProperty(DCAT.spatialResolutionInMeters)) {
      try {
        spatialResolution = r.getProperty(DCAT.spatialResolutionInMeters).getString();
      } catch (LiteralRequiredException e) {
        spatialResolution = r.getProperty(DCAT.spatialResolutionInMeters).getResource().getURI();

      }
    }

    // Extract temporalResolution property
    if (r.hasProperty(DCAT.temporalResolution)) {
      try {
        temporalResolution = r.getProperty(DCAT.temporalResolution).getString();
      } catch (LiteralRequiredException e) {
        temporalResolution = r.getProperty(DCAT.temporalResolution).getResource().getURI();

      }
    }

    DcatDistribution distribution = new DcatDistribution(nodeId, accessUrl, description, format, license, byteSize, checksum,
        Arrays.asList(documentation), downloadUrl, Arrays.asList(language), linkedSchemas,
        mediaType, releaseDate, updateDate, rights, status, title, accessService, applicableLegislation,
        availability, compressionFormat, hasPolicy, packagingFormat, spatialResolution, temporalResolution);
    distribution.setResourceUri(r.isURIResource() ? r.getURI() : null);
    if(r.hasProperty(DCTerms.identifier))distribution.setIdentifier(one(r,DCTerms.identifier));
    distribution.setDistributionDetails(
        DcatDetailsUtil.extractDatasetDetails(r, DCTerms.title, DCTerms.description));
    return distribution;

  }

  /**
   * Deserialize checksum.
   *
   * @param nodeId the node ID
   * @param r      the r
   * @return the spdx checksum
   */
  public SpdxChecksum deserializeChecksum(String nodeId, Resource r) {
    String checksumValue = null;
    String checksumAlgorithm = null;

    Resource checksumR = r.getPropertyResourceValue(
        ResourceFactory.createProperty("http://spdx.org/rdf/terms#checksum"));
    if (checksumR != null
        && checksumR.hasProperty(ResourceFactory.createProperty("http://spdx.org/rdf/terms#algorithm"))) {
      checksumAlgorithm = getStatementValue(
          checksumR.getProperty(ResourceFactory.createProperty("http://spdx.org/rdf/terms#algorithm")));
    }
    if (checksumR != null
        && checksumR.hasProperty(ResourceFactory.createProperty("http://spdx.org/rdf/terms#checksumValue"))) {
      checksumValue = getStatementValue(
          checksumR.getProperty(ResourceFactory.createProperty("http://spdx.org/rdf/terms#checksumValue")));
    }

    return new SpdxChecksum("http://spdx.org/rdf/terms#checksum", checksumAlgorithm, checksumValue,
        nodeId);

  }

  /**
   * Deserialize format.
   *
   * @param r the r
   * @return the string
   */
  public String deserializeFormat(Resource r) {
    if(r.hasProperty(DCTerms.format)&&r.getProperty(DCTerms.format).getObject().isLiteral())
      return r.getProperty(DCTerms.format).getLiteral().getLexicalForm();

    Resource formatR = r.getPropertyResourceValue(DCTerms.format);
    String formatUri = null;
    String format = null;
    if (formatR != null && StringUtils.isNotBlank(formatUri = formatR.getURI())) {
      if (!IRIFactory.iriImplementation().create(formatUri).hasViolation(false)) {
        format = formatUri;
      } else {
        format = formatUri;
      }

    }
    return format;
  }

  public String deserializeMediaType(Resource r) {
    if(r.hasProperty(DCAT.mediaType)&&r.getProperty(DCAT.mediaType).getObject().isLiteral())
      return r.getProperty(DCAT.mediaType).getLiteral().getLexicalForm();

    Resource mediaTypeR = r.getPropertyResourceValue(DCAT.mediaType);
    String mediaTypeUri = null;
    String mediaType = null;
    if (mediaTypeR != null && StringUtils.isNotBlank(mediaTypeUri = mediaTypeR.getURI())) {
      if (!IRIFactory.iriImplementation().create(mediaTypeUri).hasViolation(false)) {
        mediaType = mediaTypeUri;
      } else {
        mediaType = mediaTypeUri;
      }

    }
    return mediaType;
  }

  /**
   * extractFormatFromURI.
   *
   * @param uri the uri
   * @return the string
   */
  public String extractFormatFromUri(String uri) {

    Matcher matcher = Pattern
        .compile(
            "http:\\/\\/publications\\.europa\\.eu\\/resource\\/authority\\/file-type(\\/|#)(\\w*)")
        .matcher(uri);
    String result = null;

    return (matcher.find() && (result = matcher.group(2)) != null) ? result : "";

  }

  /**
   * extractMediaTypeFromURI.
   *
   * @param uri the uri
   * @return the string
   */
  public String extractMediaTypeFromUri(String uri) {
    // Captures full IANA media type (e.g. "application/json") from URIs like
    // https://www.iana.org/assignments/media-types/application/json
    Matcher matcher = Pattern
        .compile(
            "https?://www\\.iana\\.org/assignments/media-types[/#]([\\w.+\\-]+(?:/[\\w.+\\-]+)?)")
        .matcher(uri);
    String result = null;

    return (matcher.find() && (result = matcher.group(1)) != null) ? result : "";

  }

  /**
   * extractThemeFromURI.
   *
   * @param uri the uri
   * @return the string
   */
  public String extractThemeFromUri(String uri) {

    Matcher matcher = Pattern.compile(
        "http:\\/\\/publications\\.europa\\.eu\\/resource\\/authority\\/data-theme(\\/|#)(\\w*)")
        .matcher(uri);
    String result = null;

    return (matcher.find() && (result = matcher.group(2)) != null) ? result : "";

  }

  /**
   * Extract subject from URI.
   *
   * @param uri the uri
   * @return the string
   */
  public String extractSubjectFromUri(String uri) {

    Matcher matcher = Pattern.compile("http:\\/\\/eurovoc\\.europa\\.eu(\\/|#)(\\w*)").matcher(uri);
    String result = null;

    return (matcher.find() && (result = matcher.group(2)) != null) ? result : "";

  }

  /**
   * extractLanguageFromURI.
   *
   * @param uri the uri
   * @return the string
   */
  public String extractLanguageFromUri(String uri) {

    Matcher matcher = Pattern.compile("http:\\/\\/publications\\.europa\\.eu\\"
        + "/(mdr|resource)\\/authority\\/language(\\/|#)(\\w*)").matcher(uri);
    String result = null;

    return (matcher.find() && (result = matcher.group(3)) != null) ? result : "";

  }

  /**
   * getDatasetPattern.
   *
   * @param format the format
   * @return the dataset pattern
   */
  public Pattern getDatasetPattern(DcatApFormat format) {

    switch (format) {

      case TURTLE:
        return turtleDatasetPattern;
      default:
        return rdfDatasetPattern;
    }
  }

}
