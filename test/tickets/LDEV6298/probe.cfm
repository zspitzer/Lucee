<cfscript>
	// existence probes on a component: every one must answer without changing behaviour
	// whether or not the member is an accessor (LDEV-6298 v2 BoundUDF wraps on get(), not on contains())
	p = new Person();
	p.setName( "alpha" );
	// a raw copy of the variables scope holds bound accessors, probes on it take the plain struct path
	c = p.copyScope();

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
		called: p.getName(),
		scope: p.probeScope(),
		copyBif: structKeyExists( c, "getName" ),
		copyMember: c.keyExists( "getName" ),
		copyCalled: c.getName()
	};
	echo( serializeJSON( r ) );
</cfscript>
