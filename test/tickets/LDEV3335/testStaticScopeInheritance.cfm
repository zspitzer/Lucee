<cfscript>
// Test for static scope access on components with inheritance
// This reproduces the bcp null error from benchmark tests

try {
	systemOutput( "Testing static scope access on inherited components...", true );
	systemOutput( "", true );

	// Test 1: Access static method on base component
	systemOutput( "Test 1: Base component static method", true );
	BaseComp = new LDEV3335.BaseComponent();
	result = BaseComp::baseStaticMethod();
	systemOutput( "  Result: #result#", true );
	systemOutput( "  PASS", true );
	systemOutput( "", true );

	// Test 2: Access static method on child component
	systemOutput( "Test 2: Child component static method", true );
	ChildComp = new LDEV3335.ChildComponent();
	result = ChildComp::childStaticMethod();
	systemOutput( "  Result: #result#", true );
	systemOutput( "  PASS", true );
	systemOutput( "", true );

	// Test 3: Access base static method through child (THIS IS WHERE IT FAILS)
	systemOutput( "Test 3: Base static method accessed through child component", true );
	result = ChildComp::baseStaticMethod();
	systemOutput( "  Result: #result#", true );
	systemOutput( "  PASS", true );
	systemOutput( "", true );

	systemOutput( "All tests passed!", true );
}
catch ( any e ) {
	systemOutput( "ERROR: #e.message#", true );
	systemOutput( "Detail: #e.detail#", true );
	systemOutput( "Type: #e.type#", true );
	systemOutput( "", true );
	systemOutput( "Stack trace:", true );
	systemOutput( e.stacktrace, true );
	throw( e );
}
</cfscript>
