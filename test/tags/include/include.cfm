<cfscript>
	param name="FORM.scene" default="1";

	if ( form.scene == 1 ) {
		include template="helper.cfm";
	}
	else if ( form.scene == 2 ) {
		local.attrs = { template: "helper.cfm" };
		include attributeCollection=local.attrs;
	}
</cfscript>
