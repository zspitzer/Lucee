<cfscript>
// Test that accessor methods (getters/setters) are created properly
// Run with script-runner to verify accessors work

component = new testWithAccessors();

// Test setters exist and work
try {
	component.setA( "value a" );
	component.setB( "value b" );
	component.setC( "value c" );
	component.setD( "value d" );
	systemOutput( "SUCCESS: All setters exist and work", true );
} catch ( any e ) {
	systemOutput( "ERROR: Setter failed - #e.message#", true );
	throw e;
}

// Test getters exist and work
try {
	if ( component.getA() != "value a" ) {
		throw( "getA() returned wrong value: #component.getA()#" );
	}
	if ( component.getB() != "value b" ) {
		throw( "getB() returned wrong value: #component.getB()#" );
	}
	if ( component.getC() != "value c" ) {
		throw( "getC() returned wrong value: #component.getC()#" );
	}
	if ( component.getD() != "value d" ) {
		throw( "getD() returned wrong value: #component.getD()#" );
	}
	systemOutput( "SUCCESS: All getters exist and work", true );
} catch ( any e ) {
	systemOutput( "ERROR: Getter failed - #e.message#", true );
	throw e;
}

systemOutput( "", true );
systemOutput( "All accessor tests passed!", true );
</cfscript>
