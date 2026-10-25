component extends="org.lucee.cfml.test.LuceeTestCase" labels="java" {
	import lucee.runtime.type.KeyImpl;
	import lucee.runtime.type.util.KeyConstants;

	function run( testResults, testBox ) {
		describe( title = "LDEV-6716 KeyImpl h64 across serialization", body = function() {

			it( title = "struct keys resolve case-insensitively after objectLoad", body = function() {
				var sct = {};
				sct[ "fooBar" ] = 1;
				sct[ "stra" & chr( 223 ) & "e" ] = 2;
				var res = objectLoad( objectSave( sct ) );
				expect( res.FOOBAR ).toBe( 1 );
				expect( res[ "foobar" ] ).toBe( 1 );
				expect( res[ "STRASSE" ] ).toBe( 2 );
				expect( listFind( structKeyList( res ), "fooBar" ) ).toBeGT( 0 );
			} );

			it( title = "query columns resolve after objectLoad", body = function() {
				var qry = queryNew( "colA,colB", "varchar,integer", [ [ "a", 1 ] ] );
				expect( qry.colA[ 1 ] ).toBe( "a" );
				var res = objectLoad( objectSave( qry ) );
				expect( res.colA[ 1 ] ).toBe( "a" );
				expect( res.COLB[ 1 ] ).toBe( 1 );
			} );

			// a stream written by another h64 function (older Lucee, or another JVM) carries other hashes
			it( title = "query columns resolve when the stream holds a different h64", body = function() {
				var qry = queryNew( "colA,colB", "varchar,integer", [ [ "a", 1 ] ] );
				// builds the column map, so it's in the stream if it isn't transient
				expect( qry.colA[ 1 ] ).toBe( "a" );
				var hex = binaryEncode( objectSave( qry ), "hex" );
				loop list="colA,colB" item="local.col" {
					var stale = longToHex( KeyImpl::createHash64UC( col & "_stale" ) );
					hex = replaceNoCase( hex, longToHex( KeyImpl::createHash64UC( col ) ), stale, "all" );
				}
				var res = objectLoad( binaryDecode( hex, "hex" ) );
				expect( res.colA[ 1 ] ).toBe( "a" );
				expect( res.colB[ 1 ] ).toBe( 1 );
			} );

			it( title = "createHash64CS is case sensitive", body = function() {
				expect( toString( KeyImpl::createHash64CS( "fooBar" ) ) ).toBe( toString( KeyImpl::createHash64CS( "fooBar" ) ) );
				expect( toString( KeyImpl::createHash64CS( "fooBar" ) ) ).notToBe( toString( KeyImpl::createHash64CS( "FOOBAR" ) ) );
				// never lines up with the case-folded hash, even for an all upper-case name
				expect( toString( KeyImpl::createHash64CS( "FOOBAR" ) ) ).notToBe( toString( KeyImpl::createHash64UC( "FOOBAR" ) ) );
			} );

			it( title = "literal keys intern on the exact spelling", body = function() {
				var name = "ldev6716_" & replace( createUUID(), "-", "", "all" );
				var lc = lCase( name );
				var uc = uCase( name );
				var a = KeyImpl::initKeys( lc, KeyImpl::createHash64UC( lc ), KeyImpl::createHash64CS( lc ), KeyImpl::HASH_VERSION );
				var b = KeyImpl::initKeys( lc );
				var c = KeyImpl::initKeys( uc );
				// same spelling, same instance
				expect( a === b ).toBeTrue();
				// other spelling: own instance that keeps its spelling, still equal
				expect( a === c ).toBeFalse();
				expect( c.getString() ).toBeWithCase( uc );
				expect( a.equals( c ) ).toBeTrue();
			} );

			it( title = "baked hashes from another key hash version are recomputed", body = function() {
				var name = "ldev6716_" & replace( createUUID(), "-", "", "all" );
				// what an older class would bake: hashes from another function
				var k = KeyImpl::initKeys( name, javaCast( "long", 1 ), javaCast( "long", 2 ), KeyImpl::HASH_VERSION - 1 );
				expect( toString( k.hash() ) ).toBe( toString( KeyImpl::createHash64UC( name ) ) );
				expect( k === KeyImpl::initKeys( name ) ).toBeTrue();
				expect( k.equals( KeyImpl::init( uCase( name ) ) ) ).toBeTrue();
			} );

			it( title = "source() returns the compiled instance for compiled names", body = function() {
				// a literal
				var name = "ldev6716_" & replace( createUUID(), "-", "", "all" );
				var lit = KeyImpl::initKeys( name );
				expect( KeyImpl::source( name ) === lit ).toBeTrue( "literal" );
				// a KeyConstants name; getKeys() holds what _controller was assigned, read through a method so it stays a Key
				var constant = KeyConstants::getKeys().get( "controller" );
				expect( KeyImpl::source( "controller" ) === constant ).toBeTrue( "KeyConstants" );
				// a declared function name
				expect( KeyImpl::source( "ldev6716DeclaredFunction" ) === KeyImpl::initKeys( "ldev6716DeclaredFunction" ) ).toBeTrue( "function name" );
				// another spelling isn't the compiled instance, but still equal
				expect( KeyImpl::source( "CONTROLLER" ) === constant ).toBeFalse( "case variant" );
				expect( KeyImpl::source( "CONTROLLER" ).equals( constant ) ).toBeTrue( "case variant equal" );
			} );

			it( title = "compiled literal keys resolve", body = function() {
				var sct = { ldev6716LiteralKey: 1, "Mixed_Case": 2 };
				expect( sct.LDEV6716LITERALKEY ).toBe( 1 );
				expect( sct.mixed_case ).toBe( 2 );
			} );

		} );
	}

	// only declared: its name is registered as a compiled name when the class loads
	private void function ldev6716DeclaredFunction() {
	}

	// any, not numeric: h64 must stay a java.lang.Long, a double drops the low bits
	private string function longToHex( required any h ) {
		var s = createObject( "java", "java.lang.Long" ).toHexString( arguments.h );
		return repeatString( "0", 16 - len( s ) ) & s;
	}

}
