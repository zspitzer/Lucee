<cfscript>
	// null values inside attributeCollection with null support enabled, each case run twice
	dir = url.dir;
	function names( q ) {
		return listSort( valueList( q.name ), "textnocase" );
	}
	function attempt( fn ) {
		try {
			return fn();
		}
		catch ( any e ) {
			return "ERR " & e.message;
		}
	}
	res = {};
	for ( i = 1; i <= 2; i++ ) {
		// Object typed setter
		res[ "filter#i#" ] = attempt( function() {
			var q = "";
			directory attributeCollection={ action: "list", directory: dir, name: "local.q", filter: nullValue() };
			return names( q );
		});
		// primitive (boolean) setter
		res[ "recurse#i#" ] = attempt( function() {
			var q = "";
			directory attributeCollection={ action: "list", directory: dir, name: "local.q", recurse: nullValue() };
			return names( q );
		});
		// String setter
		res[ "sort#i#" ] = attempt( function() {
			var q = "";
			directory attributeCollection={ action: "list", directory: dir, name: "local.q", sort: nullValue() };
			return names( q );
		});
		// required attribute
		res[ "directory#i#" ] = attempt( function() {
			var q = "";
			directory attributeCollection={ action: "list", directory: nullValue(), name: "local.q" };
			return names( q );
		});
		// dynamic tag
		res[ "module#i#" ] = attempt( function() {
			var o = "";
			savecontent variable="o" {
				module template="../echoAttrs.cfm" attributeCollection={ a: nullValue(), b: 1 };
			}
			return o;
		});
	}
	echo( serializeJSON( res ) );
</cfscript>