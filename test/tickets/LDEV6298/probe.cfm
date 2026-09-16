<cfscript>
	// existence probes on a component: every one must answer without changing behaviour
	// whether or not the member is an accessor (LDEV-6298 v2 BoundUDF wraps on get(), not on contains())
	p = new Person();
	p.setName( "alpha" );

	r = {
		getter: structKeyExists( p, "getName" ),
		setter: structKeyExists( p, "setName" ),
		udf: structKeyExists( p, "hello" ),
		property: structKeyExists( p, "name" ),
		plain: structKeyExists( p, "plain" ),
		nada: structKeyExists( p, "nada" ),
		missing: structKeyExists( p, "setNope" ),
		isDefinedSetter: isDefined( "p.setName" ),
		isDefinedMissing: isDefined( "p.setNope" ),
		extracted: isCustomFunction( p.getName ),
		called: p.getName()
	};
	echo( serializeJSON( r ) );
</cfscript>
