package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CatalogGovernanceView {
    private String scope;

    @JsonProperty("baseline_catalog_summary")
    private CatalogIdentityView baselineCatalogSummary;

    @JsonProperty("current_catalog_summary")
    private CatalogIdentityView currentCatalogSummary;

    private CatalogConsistencyView consistency;

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public CatalogIdentityView getBaselineCatalogSummary() { return baselineCatalogSummary; }
    public void setBaselineCatalogSummary(CatalogIdentityView baselineCatalogSummary) { this.baselineCatalogSummary = baselineCatalogSummary; }
    public CatalogIdentityView getCurrentCatalogSummary() { return currentCatalogSummary; }
    public void setCurrentCatalogSummary(CatalogIdentityView currentCatalogSummary) { this.currentCatalogSummary = currentCatalogSummary; }
    public CatalogConsistencyView getConsistency() { return consistency; }
    public void setConsistency(CatalogConsistencyView consistency) { this.consistency = consistency; }

    public static class CatalogIdentityView {
        @JsonProperty("catalog_source")
        private String catalogSource;
        @JsonProperty("catalog_inventory_version")
        private Integer catalogInventoryVersion;
        @JsonProperty("catalog_revision")
        private Integer catalogRevision;
        @JsonProperty("catalog_fingerprint")
        private String catalogFingerprint;
        @JsonProperty("catalog_asset_count")
        private Integer catalogAssetCount;
        @JsonProperty("catalog_ready_asset_count")
        private Integer catalogReadyAssetCount;
        @JsonProperty("catalog_blacklisted_asset_count")
        private Integer catalogBlacklistedAssetCount;
        @JsonProperty("catalog_role_coverage_count")
        private Integer catalogRoleCoverageCount;
        @JsonProperty("catalog_ready_role_names")
        private List<String> catalogReadyRoleNames;
        @JsonProperty("catalog_present")
        private Boolean catalogPresent;

        public String getCatalogSource() { return catalogSource; }
        public void setCatalogSource(String catalogSource) { this.catalogSource = catalogSource; }
        public Integer getCatalogInventoryVersion() { return catalogInventoryVersion; }
        public void setCatalogInventoryVersion(Integer catalogInventoryVersion) { this.catalogInventoryVersion = catalogInventoryVersion; }
        public Integer getCatalogRevision() { return catalogRevision; }
        public void setCatalogRevision(Integer catalogRevision) { this.catalogRevision = catalogRevision; }
        public String getCatalogFingerprint() { return catalogFingerprint; }
        public void setCatalogFingerprint(String catalogFingerprint) { this.catalogFingerprint = catalogFingerprint; }
        public Integer getCatalogAssetCount() { return catalogAssetCount; }
        public void setCatalogAssetCount(Integer catalogAssetCount) { this.catalogAssetCount = catalogAssetCount; }
        public Integer getCatalogReadyAssetCount() { return catalogReadyAssetCount; }
        public void setCatalogReadyAssetCount(Integer catalogReadyAssetCount) { this.catalogReadyAssetCount = catalogReadyAssetCount; }
        public Integer getCatalogBlacklistedAssetCount() { return catalogBlacklistedAssetCount; }
        public void setCatalogBlacklistedAssetCount(Integer catalogBlacklistedAssetCount) { this.catalogBlacklistedAssetCount = catalogBlacklistedAssetCount; }
        public Integer getCatalogRoleCoverageCount() { return catalogRoleCoverageCount; }
        public void setCatalogRoleCoverageCount(Integer catalogRoleCoverageCount) { this.catalogRoleCoverageCount = catalogRoleCoverageCount; }
        public List<String> getCatalogReadyRoleNames() { return catalogReadyRoleNames; }
        public void setCatalogReadyRoleNames(List<String> catalogReadyRoleNames) { this.catalogReadyRoleNames = catalogReadyRoleNames; }
        public Boolean getCatalogPresent() { return catalogPresent; }
        public void setCatalogPresent(Boolean catalogPresent) { this.catalogPresent = catalogPresent; }
    }

    public static class CatalogConsistencyView {
        private String scope;
        @JsonProperty("baseline_catalog_present")
        private Boolean baselineCatalogPresent;
        @JsonProperty("current_catalog_present")
        private Boolean currentCatalogPresent;
        @JsonProperty("baseline_catalog_source")
        private String baselineCatalogSource;
        @JsonProperty("baseline_catalog_inventory_version")
        private Integer baselineCatalogInventoryVersion;
        @JsonProperty("baseline_catalog_revision")
        private Integer baselineCatalogRevision;
        @JsonProperty("baseline_catalog_fingerprint")
        private String baselineCatalogFingerprint;
        @JsonProperty("current_catalog_source")
        private String currentCatalogSource;
        @JsonProperty("current_catalog_inventory_version")
        private Integer currentCatalogInventoryVersion;
        @JsonProperty("current_catalog_revision")
        private Integer currentCatalogRevision;
        @JsonProperty("current_catalog_fingerprint")
        private String currentCatalogFingerprint;
        @JsonProperty("matches_current_catalog")
        private Boolean matchesCurrentCatalog;
        @JsonProperty("drift_detected")
        private Boolean driftDetected;
        @JsonProperty("mismatch_code")
        private String mismatchCode;
        @JsonProperty("consistency_code")
        private String consistencyCode;
        @JsonProperty("expected_role_names")
        private List<String> expectedRoleNames;
        @JsonProperty("catalog_ready_role_names")
        private List<String> catalogReadyRoleNames;
        @JsonProperty("missing_catalog_roles")
        private List<String> missingCatalogRoles;
        private Boolean covered;
        @JsonProperty("stale_missing_slots")
        private List<String> staleMissingSlots;

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public Boolean getBaselineCatalogPresent() { return baselineCatalogPresent; }
        public void setBaselineCatalogPresent(Boolean baselineCatalogPresent) { this.baselineCatalogPresent = baselineCatalogPresent; }
        public Boolean getCurrentCatalogPresent() { return currentCatalogPresent; }
        public void setCurrentCatalogPresent(Boolean currentCatalogPresent) { this.currentCatalogPresent = currentCatalogPresent; }
        public String getBaselineCatalogSource() { return baselineCatalogSource; }
        public void setBaselineCatalogSource(String baselineCatalogSource) { this.baselineCatalogSource = baselineCatalogSource; }
        public Integer getBaselineCatalogInventoryVersion() { return baselineCatalogInventoryVersion; }
        public void setBaselineCatalogInventoryVersion(Integer baselineCatalogInventoryVersion) { this.baselineCatalogInventoryVersion = baselineCatalogInventoryVersion; }
        public Integer getBaselineCatalogRevision() { return baselineCatalogRevision; }
        public void setBaselineCatalogRevision(Integer baselineCatalogRevision) { this.baselineCatalogRevision = baselineCatalogRevision; }
        public String getBaselineCatalogFingerprint() { return baselineCatalogFingerprint; }
        public void setBaselineCatalogFingerprint(String baselineCatalogFingerprint) { this.baselineCatalogFingerprint = baselineCatalogFingerprint; }
        public String getCurrentCatalogSource() { return currentCatalogSource; }
        public void setCurrentCatalogSource(String currentCatalogSource) { this.currentCatalogSource = currentCatalogSource; }
        public Integer getCurrentCatalogInventoryVersion() { return currentCatalogInventoryVersion; }
        public void setCurrentCatalogInventoryVersion(Integer currentCatalogInventoryVersion) { this.currentCatalogInventoryVersion = currentCatalogInventoryVersion; }
        public Integer getCurrentCatalogRevision() { return currentCatalogRevision; }
        public void setCurrentCatalogRevision(Integer currentCatalogRevision) { this.currentCatalogRevision = currentCatalogRevision; }
        public String getCurrentCatalogFingerprint() { return currentCatalogFingerprint; }
        public void setCurrentCatalogFingerprint(String currentCatalogFingerprint) { this.currentCatalogFingerprint = currentCatalogFingerprint; }
        public Boolean getMatchesCurrentCatalog() { return matchesCurrentCatalog; }
        public void setMatchesCurrentCatalog(Boolean matchesCurrentCatalog) { this.matchesCurrentCatalog = matchesCurrentCatalog; }
        public Boolean getDriftDetected() { return driftDetected; }
        public void setDriftDetected(Boolean driftDetected) { this.driftDetected = driftDetected; }
        public String getMismatchCode() { return mismatchCode; }
        public void setMismatchCode(String mismatchCode) { this.mismatchCode = mismatchCode; }
        public String getConsistencyCode() { return consistencyCode; }
        public void setConsistencyCode(String consistencyCode) { this.consistencyCode = consistencyCode; }
        public List<String> getExpectedRoleNames() { return expectedRoleNames; }
        public void setExpectedRoleNames(List<String> expectedRoleNames) { this.expectedRoleNames = expectedRoleNames; }
        public List<String> getCatalogReadyRoleNames() { return catalogReadyRoleNames; }
        public void setCatalogReadyRoleNames(List<String> catalogReadyRoleNames) { this.catalogReadyRoleNames = catalogReadyRoleNames; }
        public List<String> getMissingCatalogRoles() { return missingCatalogRoles; }
        public void setMissingCatalogRoles(List<String> missingCatalogRoles) { this.missingCatalogRoles = missingCatalogRoles; }
        public Boolean getCovered() { return covered; }
        public void setCovered(Boolean covered) { this.covered = covered; }
        public List<String> getStaleMissingSlots() { return staleMissingSlots; }
        public void setStaleMissingSlots(List<String> staleMissingSlots) { this.staleMissingSlots = staleMissingSlots; }
    }
}
