<cfscript>
	// custom tag: hands its attributes scope back via request (shallow copy, values untouched) and prints the simple ones
	if ( thisTag.executionMode == "start" ) {
		request.attrcollEcho = structCopy( attributes );
		keys = structKeyArray( attributes );
		arraySort( keys, "textnocase" );
		parts = [];
		for ( k in keys ) {
			if ( isNull( attributes[ k ] ) ) arrayAppend( parts, k & "=<null>" );
			else if ( isSimpleValue( attributes[ k ] ) ) arrayAppend( parts, k & "=" & attributes[ k ] );
			else arrayAppend( parts, k & "=<complex>" );
		}
		writeOutput( arrayToList( parts, ";" ) );
	}
</cfscript>