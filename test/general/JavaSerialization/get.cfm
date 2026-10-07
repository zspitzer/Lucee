<cfscript>
	// read-only: the session is loaded from storage before this runs, nothing is written back
	result = {
		"action": "get",
		"sessionUser": session.user ?: "(undefined)",
		"cartTotal": session.cart.total ?: 0,
		"cfid": cfid
	};
	content type="application/json";
	writeOutput( serializeJson( result ) );
</cfscript>
