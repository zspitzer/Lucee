<cfscript>
// Test to verify property order is preserved
// This is CRITICAL for ORM, serialization, and backward compatibility

cfc = new component accessors="true" {
	property name="zulu" type="string" default="last";
	property name="alpha" type="string" default="first";
	property name="mike" type="string" default="middle";
};

meta = getMetaData( cfc );
props = meta.properties;

systemOutput( "Property count: #arrayLen( props )#", true );
systemOutput( "Property 1: #props[ 1 ].name#", true );
systemOutput( "Property 2: #props[ 2 ].name#", true );
systemOutput( "Property 3: #props[ 3 ].name#", true );

// Properties MUST be in declaration order, not alphabetical!
if ( props[ 1 ].name != "zulu" ) {
	throw( "Property order broken! Expected 'zulu' first, got '#props[ 1 ].name#'" );
}
if ( props[ 2 ].name != "alpha" ) {
	throw( "Property order broken! Expected 'alpha' second, got '#props[ 2 ].name#'" );
}
if ( props[ 3 ].name != "mike" ) {
	throw( "Property order broken! Expected 'mike' third, got '#props[ 3 ].name#'" );
}

systemOutput( "✓ Property order preserved correctly!", true );
</cfscript>
