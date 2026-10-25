<cfscript>
	q = queryNew( "a", "varchar", [ [ "x" ] ] );
	result = queryExecute( "select a from q", {}, { dbtype: "query" } );
	echo( isArray( result ) ? "array" : "query" );
</cfscript>
