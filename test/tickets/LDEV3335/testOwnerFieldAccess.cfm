<cfscript>
// Test different ways to access the owner field

obj = new LDEV3335.testOwnerFieldComponent();

systemOutput("=== Testing different ways to access owner field ===", true);
systemOutput("", true);

// Method 1: getMetaData()
try {
	meta = getMetaData(obj.getMessage);
	systemOutput("1. getMetaData(obj.getMessage).owner: " & (structKeyExists(meta, "owner") ? meta.owner : "NOT FOUND"), true);
} catch (any e) {
	systemOutput("1. getMetaData() FAILED: " & e.message, true);
}

// Method 2: Direct UDF reference
try {
	udf = obj.getMessage;
	systemOutput("2. Direct UDF reference type: " & getMetaData(udf).name, true);
} catch (any e) {
	systemOutput("2. Direct UDF reference FAILED: " & e.message, true);
}

// Method 3: Component metadata - does it show property accessor owners?
try {
	compMeta = getMetaData(obj);
	systemOutput("", true);
	systemOutput("3. Component metadata functions: " & compMeta.functions.len(), true);

	loop array=compMeta.functions index="i" item="func" {
		if (func.name == "getMessage") {
			systemOutput("   getMessage in component metadata has owner? " & structKeyExists(func, "owner"), true);
			if (structKeyExists(func, "owner")) {
				systemOutput("   owner: " & func.owner, true);
			}
		}
	}
} catch (any e) {
	systemOutput("3. Component metadata FAILED: " & e.message, true);
}

systemOutput("", true);
systemOutput("=== Conclusion ===", true);
systemOutput("The 'owner' field is part of UDF metadata, accessible via getMetaData(udfReference)", true);
</cfscript>
