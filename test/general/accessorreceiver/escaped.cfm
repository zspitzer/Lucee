<cfscript>
	// LDEV-3335: an accessor copied out of a component's variables scope, then called where there is
	// no component in ambient scope at all. This template is reached by _InternalRequest precisely so
	// that the call site is a plain page — inside a TestBox spec body the closure's defining component
	// would answer as the receiver and the LDEV-1962 tier-1 lookup would mask this.
	a = new mixinSource();
	b = new mixinSource();
	a.setName( "alpha" );
	b.setName( "bravo" );

	fromA = {};
	fromB = {};
	structAppend( fromA, a.exportVars(), false );
	structAppend( fromB, b.exportVars(), false );

	getFromA = fromA.getName;
	getFromB = fromB.getName;

	echo( getFromA() & "|" & getFromB() );
</cfscript>
