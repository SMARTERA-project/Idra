package it.eng.idra.utils;

import it.eng.idra.beans.dcat.DcatDetails;
import it.eng.idra.beans.dcat.DcatKeyword;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility methods for extracting multilingual dataset title/description values.
 */
public final class DcatDetailsUtil {

  private static final Pattern LANGUAGE_KEY_PATTERN =
      Pattern.compile("^[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})*$");
  private static final Pattern LITERAL_LANGUAGE_SUFFIX_PATTERN =
      Pattern.compile("@([A-Za-z0-9][A-Za-z0-9_-]*)$");

  private static final Set<String> RESERVED_KEYS = new LinkedHashSet<>(
      Arrays.asList("value", "@value", "language", "@language", "lang", "type", "@type", "id",
          "@id"));

  private DcatDetailsUtil() {
  }

  /**
   * Extract dataset details from raw title and description payloads.
   *
   * @param rawTitle raw title value
   * @param rawDescription raw description value
   * @param fallbackTitle fallback plain title
   * @param fallbackDescription fallback plain description
   * @return extracted details
   */
  public static List<DcatDetails> extractDatasetDetails(Object rawTitle, Object rawDescription,
      String fallbackTitle, String fallbackDescription) {

    List<LocalizedValue> titles = parseLocalizedValues(rawTitle);
    List<LocalizedValue> descriptions = parseLocalizedValues(rawDescription);

    if (titles.isEmpty() && StringUtils.isNotBlank(fallbackTitle)) {
      titles.add(new LocalizedValue(null, fallbackTitle));
    }
    if (descriptions.isEmpty() && StringUtils.isNotBlank(fallbackDescription)) {
      descriptions.add(new LocalizedValue(null, fallbackDescription));
    }

    return mergeLocalizedValues(titles, descriptions);
  }

  /**
   * Extract dataset details from raw title and description payloads.
   *
   * @param rawTitle raw title value
   * @param rawDescription raw description value
   * @return extracted details
   */
  public static List<DcatDetails> extractDatasetDetails(Object rawTitle, Object rawDescription) {
    return extractDatasetDetails(rawTitle, rawDescription,
        rawTitle != null ? String.valueOf(rawTitle) : null,
        rawDescription != null ? String.valueOf(rawDescription) : null);
  }

  /**
   * Extract dataset details from RDF literals.
   *
   * @param datasetResource dataset RDF resource
   * @param titleProperty RDF property for title
   * @param descriptionProperty RDF property for description
   * @return extracted details
   */
  public static List<DcatDetails> extractDatasetDetails(Resource datasetResource,
      Property titleProperty, Property descriptionProperty) {

    List<LocalizedValue> titles = new ArrayList<>();
    List<LocalizedValue> descriptions = new ArrayList<>();

    collectRdfLiteralValues(datasetResource, titleProperty, titles);
    collectRdfLiteralValues(datasetResource, descriptionProperty, descriptions);

    return mergeLocalizedValues(titles, descriptions);
  }

  /**
   * Clone details without IDs and FK references not related to datasets.
   *
   * @param source details to clone
   * @return cloned details
   */
  public static List<DcatDetails> cloneDatasetDetails(List<DcatDetails> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }

    List<DcatDetails> cloned = new ArrayList<>();
    for (DcatDetails item : source) {
      if (item == null) {
        continue;
      }
      String title = StringUtils.trimToNull(item.getTitle());
      String description = StringUtils.trimToNull(item.getDescription());
      String language = normalizeLanguage(item.getLanguage());
      if (title == null && description == null) {
        continue;
      }
      cloned.add(new DcatDetails(null, null, null, null, null, description, title, language));
    }
    return cloned;
  }

  /**
   * Extract keyword details from raw keywords payloads.
   *
   * @param rawKeywords raw keywords
   * @return extracted keyword details
   */
  public static List<DcatKeyword> extractKeywordDetails(Object rawKeywords) {
    List<LocalizedValue> values = parseLocalizedValues(rawKeywords);
    return localizedValuesToKeywords(values);
  }

  /**
   * Extract keyword details from RDF literals.
   *
   * @param datasetResource dataset RDF resource
   * @param keywordProperty RDF property for keyword
   * @return extracted keyword details
   */
  public static List<DcatKeyword> extractKeywordDetails(Resource datasetResource, Property keywordProperty) {
    List<LocalizedValue> values = new ArrayList<>();
    collectRdfLiteralValues(datasetResource, keywordProperty, values);
    return localizedValuesToKeywords(values);
  }

  /**
   * Clone keyword details without IDs/FKs.
   *
   * @param source keyword details to clone
   * @return cloned keyword details
   */
  public static List<DcatKeyword> cloneKeywordDetails(List<DcatKeyword> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }

    List<DcatKeyword> cloned = new ArrayList<>();
    for (DcatKeyword item : source) {
      if (item == null) {
        continue;
      }
      String value = StringUtils.trimToNull(item.getValue());
      if (value == null) {
        continue;
      }
      cloned.add(new DcatKeyword(value, normalizeLanguage(item.getLanguage())));
    }
    return cloned;
  }

  private static void collectRdfLiteralValues(Resource resource, Property property,
      List<LocalizedValue> target) {
    if (resource == null || property == null || target == null) {
      return;
    }

    StmtIterator iterator = resource.listProperties(property);
    while (iterator.hasNext()) {
      Statement statement = iterator.next();
      if (statement == null || statement.getObject() == null) {
        continue;
      }

      if (statement.getObject().isLiteral()) {
        Literal literal = statement.getObject().asLiteral();
        String language = StringUtils.trimToNull(literal.getLanguage());
        if (language == null) {
          language = extractLanguageFromLiteralLexicalForm(statement.getObject().toString());
        }
        addLocalizedValue(target, language, literal.getString());
      } else {
        addLocalizedValue(target, null, statement.getObject().toString());
      }
    }
  }

  private static String extractLanguageFromLiteralLexicalForm(String lexicalForm) {
    String lexical = StringUtils.trimToNull(lexicalForm);
    if (lexical == null) {
      return null;
    }
    java.util.regex.Matcher matcher = LITERAL_LANGUAGE_SUFFIX_PATTERN.matcher(lexical);
    if (matcher.find()) {
      return normalizeLanguage(matcher.group(1));
    }
    return null;
  }

  private static List<LocalizedValue> parseLocalizedValues(Object rawValue) {
    List<LocalizedValue> values = new ArrayList<>();
    parseAny(rawValue, null, values);
    return values;
  }

  @SuppressWarnings("unchecked")
  private static void parseAny(Object rawValue, String forcedLanguage, List<LocalizedValue> target) {
    if (rawValue == null) {
      return;
    }

    if (rawValue instanceof JSONObject) {
      parseJsonObject((JSONObject) rawValue, forcedLanguage, target);
      return;
    }

    if (rawValue instanceof JSONArray) {
      JSONArray array = (JSONArray) rawValue;
      for (int i = 0; i < array.length(); i++) {
        parseAny(array.opt(i), forcedLanguage, target);
      }
      return;
    }

    if (rawValue instanceof Map) {
      parseMap((Map<String, Object>) rawValue, forcedLanguage, target);
      return;
    }

    if (rawValue instanceof Iterable) {
      for (Object item : (Iterable<?>) rawValue) {
        parseAny(item, forcedLanguage, target);
      }
      return;
    }

    if (rawValue.getClass().isArray()) {
      int len = Array.getLength(rawValue);
      for (int i = 0; i < len; i++) {
        parseAny(Array.get(rawValue, i), forcedLanguage, target);
      }
      return;
    }

    addLocalizedValue(target, forcedLanguage, String.valueOf(rawValue));
  }

  private static void parseJsonObject(JSONObject jsonObject, String forcedLanguage,
      List<LocalizedValue> target) {
    if (jsonObject == null) {
      return;
    }

    String languageFromObject = firstNonBlank(
        normalizeLanguage(forcedLanguage),
        normalizeLanguage(jsonObject.optString("@language", null)),
        normalizeLanguage(jsonObject.optString("language", null)),
        normalizeLanguage(jsonObject.optString("lang", null)));

    if (jsonObject.has("@value")) {
      parseAny(jsonObject.opt("@value"), languageFromObject, target);
      return;
    }

    if (jsonObject.has("value")) {
      parseAny(jsonObject.opt("value"), languageFromObject, target);
      return;
    }

    if (isLanguageMap(jsonObject)) {
      for (String key : jsonObject.keySet()) {
        parseAny(jsonObject.opt(key), normalizeLanguage(key), target);
      }
      return;
    }

    for (String key : jsonObject.keySet()) {
      parseAny(jsonObject.opt(key), languageFromObject, target);
    }
  }

  private static void parseMap(Map<String, Object> map, String forcedLanguage,
      List<LocalizedValue> target) {
    if (map == null || map.isEmpty()) {
      return;
    }

    if (isLanguageMap(map)) {
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        parseAny(entry.getValue(), normalizeLanguage(entry.getKey()), target);
      }
      return;
    }

    String languageFromMap = normalizeLanguage(forcedLanguage);
    Object preferredValue = map.containsKey("value") ? map.get("value")
        : (map.containsKey("@value") ? map.get("@value") : null);
    if (preferredValue != null) {
      String lang = firstNonBlank(languageFromMap,
          normalizeLanguage(Objects.toString(map.get("@language"), null)),
          normalizeLanguage(Objects.toString(map.get("language"), null)),
          normalizeLanguage(Objects.toString(map.get("lang"), null)));
      parseAny(preferredValue, lang, target);
      return;
    }

    for (Object value : map.values()) {
      parseAny(value, languageFromMap, target);
    }
  }

  private static boolean isLanguageMap(JSONObject jsonObject) {
    if (jsonObject == null || jsonObject.length() == 0) {
      return false;
    }

    for (String key : jsonObject.keySet()) {
      if (RESERVED_KEYS.contains(key)) {
        return false;
      }
      if (!LANGUAGE_KEY_PATTERN.matcher(key).matches()) {
        return false;
      }
    }
    return true;
  }

  private static boolean isLanguageMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return false;
    }

    for (String key : map.keySet()) {
      if (RESERVED_KEYS.contains(key)) {
        return false;
      }
      if (!LANGUAGE_KEY_PATTERN.matcher(key).matches()) {
        return false;
      }
    }
    return true;
  }

  private static void addLocalizedValue(List<LocalizedValue> target, String language, String value) {
    if (target == null) {
      return;
    }
    String normalizedValue = StringUtils.trimToNull(value);
    if (normalizedValue == null) {
      return;
    }
    target.add(new LocalizedValue(normalizeLanguage(language), normalizedValue));
  }

  private static List<DcatDetails> mergeLocalizedValues(List<LocalizedValue> titles,
      List<LocalizedValue> descriptions) {

    Map<String, List<String>> titlesByLanguage = groupByLanguage(titles);
    Map<String, List<String>> descriptionsByLanguage = groupByLanguage(descriptions);

    LinkedHashSet<String> orderedLanguages = new LinkedHashSet<>();
    orderedLanguages.addAll(titlesByLanguage.keySet());
    orderedLanguages.addAll(descriptionsByLanguage.keySet());

    List<DcatDetails> details = new ArrayList<>();

    for (String languageKey : orderedLanguages) {
      List<String> titlesForLang = titlesByLanguage.getOrDefault(languageKey, Collections.emptyList());
      List<String> descriptionsForLang =
          descriptionsByLanguage.getOrDefault(languageKey, Collections.emptyList());

      int max = Math.max(titlesForLang.size(), descriptionsForLang.size());
      if (max == 0) {
        continue;
      }

      for (int i = 0; i < max; i++) {
        String title = valueAtOrNull(titlesForLang, i);
        String description = valueAtOrNull(descriptionsForLang, i);
        if (title == null && description == null) {
          continue;
        }
        details.add(new DcatDetails(null, null, null, null, null, description, title,
            toNullableLanguageKey(languageKey)));
      }
    }

    return details;
  }

  private static Map<String, List<String>> groupByLanguage(List<LocalizedValue> values) {
    Map<String, List<String>> grouped = new LinkedHashMap<>();
    if (values == null) {
      return grouped;
    }

    for (LocalizedValue value : values) {
      if (value == null || StringUtils.isBlank(value.value)) {
        continue;
      }
      String languageKey = toLanguageKey(value.language);
      grouped.computeIfAbsent(languageKey, key -> new ArrayList<>()).add(value.value);
    }
    return grouped;
  }

  private static String valueAtOrNull(List<String> values, int index) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    if (index >= 0 && index < values.size()) {
      return values.get(index);
    }
    return values.get(values.size() - 1);
  }

  private static String toLanguageKey(String language) {
    return language == null ? "__null__" : language;
  }

  private static String toNullableLanguageKey(String languageKey) {
    return "__null__".equals(languageKey) ? null : languageKey;
  }

  private static String normalizeLanguage(String language) {
    String normalized = StringUtils.trimToNull(language);
    if (normalized == null) {
      return null;
    }
    return normalized.replace('_', '-').toLowerCase();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.isNotBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private static List<DcatKeyword> localizedValuesToKeywords(List<LocalizedValue> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }

    Map<String, DcatKeyword> deduplicated = new LinkedHashMap<>();
    for (LocalizedValue value : values) {
      if (value == null || StringUtils.isBlank(value.value)) {
        continue;
      }
      String normalizedValue = StringUtils.trimToNull(value.value);
      String normalizedLanguage = normalizeLanguage(value.language);
      if (normalizedValue == null) {
        continue;
      }
      String key = (normalizedLanguage == null ? "" : normalizedLanguage) + "|" + normalizedValue;
      deduplicated.putIfAbsent(key, new DcatKeyword(normalizedValue, normalizedLanguage));
    }

    return new ArrayList<>(deduplicated.values());
  }

  private static final class LocalizedValue {
    private final String language;
    private final String value;

    private LocalizedValue(String language, String value) {
      this.language = language;
      this.value = value;
    }
  }
}
