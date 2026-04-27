<cfscript>
events = application.events ?: [];
content type="application/json";
writeOutput( serializeJson( events ) );
</cfscript>
