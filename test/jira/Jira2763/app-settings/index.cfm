<cfscript>
	settings = getApplicationSettings();
	echo( structKeyExists( settings, "tag" ) ? serializeJSON( settings.tag ) : "no tag key" );
</cfscript>
