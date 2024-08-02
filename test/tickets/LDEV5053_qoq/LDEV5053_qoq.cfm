<cfscript>
	param name="url.chaos";
	param name="url.idx";

	ldev5053_qoq = queryNew( "id,name,data", "integer,varchar,varchar" );
	names= [ 'micha', 'zac', 'brad', 'pothys', 'gert' ];
	loop array="#names#" item="n" {
		r = queryAddRow( ldev5053_qoq );
		querySetCell( ldev5053_qoq, "id", r, r );
		querySetCell( ldev5053_qoq, "name", n, r );
	}

	sql = "SELECT id, name FROM ldev5053_qoq ORDER BY name";

	if( url.chaos && ( url.idx mod 3) eq 1 ){
		sql = "BAD " & sql;
	}

	// native engine
	q_native = QueryExecute(
		sql = sql,
		options = { dbtype: 'query' }
	);
	if( url.chaos && ( url.idx mod 3) eq 1 ){
		sleep( 10 );
		throw "chaos mode, throw exception at [#url.idx#]";
	}
</cfscript>