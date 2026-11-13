component extends="org.lucee.cfml.test.LuceeTestCase" {

	function run( testResults , testBox ) {

		variables.world = {"save":"water","clean":"wastes"};
		variables.human = {"save":"money","clean":"food"};
		variables.legend = {"save":"energy","forget":"sadness"}
		describe( title = "Test suite for structAppend", body = function() {

			it( title = 'Test case for structAppend function',body = function( currentSpec ) {
				structappend(world,human);
				assertEquals("food", world.clean);
				assertEquals("money", world.save);
				structappend(world,legend);
				assertEquals("food", world.clean);
				assertEquals("energy", world.save);
				assertEquals("sadness", world.forget);
				structappend(human,legend);
				assertEquals("food", human.clean);
				assertEquals("energy", human.save);
				assertEquals("sadness", human.forget);
				structappend(legend,human,false);
				assertEquals("food", legend.clean);
				assertEquals("energy", legend.save);
				assertEquals("sadness", legend.forget);
				structappend(legend,{"save":"time"});
				assertEquals("food", legend.clean);
				assertEquals("time", legend.save);
				assertEquals("sadness", legend.forget);
			});

			it( title = 'Test case for structAppend member function',body = function( currentSpec ) {
				world.append(human);
				assertEquals("food", world.clean);
				assertEquals("energy", world.save);
				assertEquals("sadness", world.forget);
				world.append(legend);
				assertEquals("food", world.clean);
				assertEquals("time", world.save);
				assertEquals("sadness", world.forget);
				human.append(legend);
				assertEquals("food", human.clean);
				assertEquals("time", human.save);
				assertEquals("sadness", human.forget);
				legend.append(human,false);
				assertEquals("food", legend.clean);
				assertEquals("time", legend.save);
				assertEquals("sadness", legend.forget);
				legend.append({"save":"time"});
				assertEquals("food", legend.clean);
				assertEquals("time", legend.save);
				assertEquals("sadness", legend.forget);
			});

			it( title = 'Test case for structAppend with ordered structs',body = function( currentSpec ) {
				// Ordered structs using [:] notation should maintain insertion order
				var orderedWorld = ["save":"water","clean":"wastes"];
				var orderedHuman = ["save":"money","clean":"food"];
				var orderedLegend = ["save":"energy","forget":"sadness"];

				structappend(orderedWorld,orderedHuman);
				assertEquals("money", orderedWorld.save);
				assertEquals("food", orderedWorld.clean);

				structappend(orderedWorld,orderedLegend);
				assertEquals("energy", orderedWorld.save);
				assertEquals("food", orderedWorld.clean);
				assertEquals("sadness", orderedWorld.forget);

				structappend(orderedHuman,orderedLegend);
				assertEquals("energy", orderedHuman.save);
				assertEquals("food", orderedHuman.clean);
				assertEquals("sadness", orderedHuman.forget);

				structappend(orderedLegend,orderedHuman,false);
				assertEquals("energy", orderedLegend.save);
				assertEquals("sadness", orderedLegend.forget);
				assertEquals("food", orderedLegend.clean);
			});

			it( title = 'structAppend a large struct into a small one, overwrite=false', body = function( currentSpec ) {
				var dest = { "a": "dest-a", "b": "dest-b", "c": "dest-c", "d": "dest-d" };
				var src = buildStruct( 238 );
				src[ "a" ] = "src-a";
				structAppend( dest, src, false );
				expect( structCount( dest ) ).toBe( 242 );
				expect( dest.a ).toBe( "dest-a" );
				expect( dest.d ).toBe( "dest-d" );
				for ( var i = 1; i <= 238; i++ ) {
					expect( dest[ "key#i#" ] ).toBe( i );
				}
			});

			it( title = 'structAppend a large struct into an empty one', body = function( currentSpec ) {
				var dest = {};
				structAppend( dest, buildStruct( 430 ) );
				expect( structCount( dest ) ).toBe( 430 );
				for ( var i = 1; i <= 430; i++ ) {
					expect( dest[ "key#i#" ] ).toBe( i );
				}
			});

			it( title = 'structAppend overwrite=false replaces a destination key holding null', body = function( currentSpec ) {
				var dest = { "a": "dest-a" };
				dest[ "key1" ] = javacast( "null", "" );
				structAppend( dest, buildStruct( 100 ), false );
				expect( dest.key1 ).toBe( 1 );
				expect( dest.a ).toBe( "dest-a" );
				expect( structCount( dest ) ).toBe( 101 );
			});

			it( title = 'structAppend a large struct into an ordered struct keeps insertion order', body = function( currentSpec ) {
				var dest = [ "x": 1, "y": 2 ];
				var src = [:];
				for ( var i = 1; i <= 100; i++ ) {
					src[ "key#i#" ] = i;
				}
				structAppend( dest, src );
				var keys = structKeyArray( dest );
				expect( arrayLen( keys ) ).toBe( 102 );
				expect( keys[ 1 ] ).toBe( "x" );
				expect( keys[ 2 ] ).toBe( "y" );
				for ( var i = 1; i <= 100; i++ ) {
					expect( keys[ i + 2 ] ).toBe( "key#i#" );
				}
			});

			it( title = 'structAppend an empty struct leaves the destination unchanged', body = function( currentSpec ) {
				var dest = { "a": 1, "b": 2 };
				expect( structAppend( dest, {} ) ).toBeTrue();
				expect( structCount( dest ) ).toBe( 2 );
				expect( dest.a ).toBe( 1 );
			});

			it( title = 'structAppend into variables while a cfthread writes to it keeps every write', body = function( currentSpec ) {
				var result = deserializeJSON( _InternalRequest( template: createURI( "structAppend/variablesWithThread.cfm" ) ).filecontent );
				expect( result.status ).toBe( "COMPLETED" );
				expect( result.src ).toBe( 500 );
				expect( result.thread ).toBe( 20000 );
			});

			it( title = 'structAppend a struct into itself', body = function( currentSpec ) {
				var dest = buildStruct( 100 );
				structAppend( dest, dest );
				expect( structCount( dest ) ).toBe( 100 );
				expect( dest.key100 ).toBe( 100 );
			});
		});

	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrentTemplatePath() ), "\/" )#/";
		return baseURI & "" & calledName;
	}

	private struct function buildStruct( required numeric count ) {
		var sct = {};
		for ( var i = 1; i <= arguments.count; i++ ) {
			sct[ "key#i#" ] = i;
		}
		return sct;
	}
}