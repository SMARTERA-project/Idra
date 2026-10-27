package it.eng.idra.utils;

import it.eng.idra.beans.dcat.DcatApVersion;
import it.eng.idra.beans.dcat.DcatDataset;
import java.util.List;

/** Vocabulary evidence, not a certification of DCAT-AP conformance. */
public final class DcatVersionDetector {
  private DcatVersionDetector() { }
  public static DcatApVersion detect(List<DcatDataset> datasets, DcatApVersion currentVersion) {
    if (datasets == null || datasets.isEmpty()) return currentVersion;
    boolean declaredV2=false;
    for(DcatDataset dataset:datasets) if(dataset!=null && dataset.getConformsTo()!=null) {
      for(it.eng.idra.beans.dcat.DctStandard profile:dataset.getConformsTo()) {
        if(profile==null)continue;
        String uri=profile.getIdentifier()!=null?profile.getIdentifier().getValue():profile.getUri();
        if(uri==null)continue;
        if(uri.matches("https?://semiceu\\.github\\.io/DCAT-AP/releases/3\\..*"))return DcatApVersion.DCAT_AP_3;
        if(uri.matches("https?://semiceu\\.github\\.io/DCAT-AP/releases/2\\..*"))declaredV2=true;
      }
    }
    if(declaredV2)return DcatApVersion.DCAT_AP_2;
    for (DcatDataset dataset : datasets) {
      if (dataset == null) continue;
      if (dataset.getInSeries() != null && !dataset.getInSeries().isEmpty()
          || nonEmpty(dataset.getHasCurrentVersion()) || nonEmpty(dataset.getPreviousVersion())
          || nonEmpty(dataset.getPrev()) || nonEmpty(dataset.getNext())) return DcatApVersion.DCAT_AP_3;
    }
    // DataService, resolution, qualifiedRelation and HVD cannot discriminate versions.
    // Null is already represented by API/DB and means insufficient evidence.
    return null;
  }
  private static boolean nonEmpty(List<String> values) {
    return values != null && values.stream().anyMatch(v -> v != null && !v.trim().isEmpty());
  }
}
