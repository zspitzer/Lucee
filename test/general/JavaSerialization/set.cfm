<cfscript>
	// seed a session shaped like a real one so the stored stream names several core classes
	session.user = url.user ?: "zac";
	session.profile = { id: 4711, created: now(), admin: false, tags: [ "a", "b" ] };
	session.cart = { items: [ { sku: "X1", qty: 2 }, { sku: "Y2", qty: 1 } ], total: 30.5 };
	session.lastSeen = now();

	result = {
		"action": "set",
		"sessionStorage": getApplicationSettings().sessionStorage,
		"sessionUser": session.user,
		"cfid": cfid
	};
	content type="application/json";
	writeOutput( serializeJson( result ) );
</cfscript>
