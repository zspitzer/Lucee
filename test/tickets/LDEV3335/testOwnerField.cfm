<cfscript>
// Test if owner field exists on regular accessor UDFs (no mixins)

obj = new LDEV3335.testOwnerFieldComponent();

// Get metadata for the auto-generated getter
meta = getMetaData(obj.getMessage);

systemOutput("=== Testing owner field on regular accessor UDF (no mixins) ===", true);
systemOutput("", true);
systemOutput("Metadata keys: " & structKeyList(meta), true);
systemOutput("", true);

if (structKeyExists(meta, "owner")) {
	systemOutput("✓ owner field EXISTS: " & meta.owner, true);
} else {
	systemOutput("✗ owner field MISSING", true);
}
</cfscript>
