<cfscript>
	// a cfthread writes into the page's variables scope while the page appends a large struct into it
	src = {};
	for ( i = 1; i <= 500; i++ ) {
		src[ "src#i#" ] = i;
	}
	thread name="writer" {
		for ( j = 1; j <= 20000; j++ ) {
			variables[ "thread#j#" ] = j;
		}
	}
	// append while the thread is mid-stream, with fewer keys in variables than incoming, so a map
	// rebuild would copy entries while the thread is still writing into them
	started = getTickCount();
	while ( !structKeyExists( variables, "thread300" ) && getTickCount() - started < 5000 ) {
	}
	structAppend( variables, src );
	thread action="join" name="writer";

	threadKeys = 0;
	srcKeys = 0;
	for ( k in structKeyArray( variables ) ) {
		if ( left( k, 6 ) == "thread" && isNumeric( mid( k, 7, 10 ) ) ) threadKeys++;
		else if ( left( k, 3 ) == "src" && isNumeric( mid( k, 4, 10 ) ) ) srcKeys++;
	}
	echo( serializeJSON( { "thread": threadKeys, "src": srcKeys, "status": cfthread.writer.status } ) );
</cfscript>
