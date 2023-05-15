component extends="org.lucee.cfml.test.LuceeTestCase" {

	variables.rounds = 10000;
	variables.mysql = server.getDatasource("mysql");

	function run( testResults , testBox ) {
		describe( title="Test suite for CreateULID()", body=function() {
			it(title="checking CreateULID() function", body = function( currentSpec ) {
				systemOutput( "", true );
				systemOutput( "sample output", true );
				loop times=10 {
					systemOutput( createULID(), true );
				}
			});

			it(title="checking CreateULID('Monotonic') function", body = function( currentSpec ) {
				systemOutput( "", true );
				systemOutput( "sample Monotonic output", true );
				loop times=10 {
					systemOutput( createULID("Monotonic"), true );
				}
			});

			it(title="checking CreateULID('hash', number, string ) function", body = function( currentSpec ) {
				var once = createULID( "hash", 1, "b" );
				var again = createULID( "hash", 1, "b" );

				expect( once ).toBe( again );
			});

			it(title="checking CreateULID() function perf with #variables.rounds# rows", body = function( currentSpec ) {

				var tbl = createTable( "default" );
				if ( isEmpty( tbl ) ) return;
				timer unit="milli" variable="local.timer" {
					transaction {
						loop times=#variables.rounds# {
							populateTable( tbl, createULID() );
						}
					}
				}
				systemOutput( "" , true );
				systemOutput( "#variables.rounds# rows with CreateULID() took " & timer, true);
			});

			it(title="checking CreateUUID() function perf with #variables.rounds# rows", body = function( currentSpec ) {

				var tbl = createTable( "uuid" );
				if ( isEmpty( tbl ) ) return;

				timer unit="milli" variable="local.timer" {
					transaction {
						loop times=#variables.rounds# {
							populateTable( tbl, createUUID() );
						}
					}
				}

				systemOutput( "" , true );
				systemOutput( "#variables.rounds# rows with createUUID() took " & timer, true);
			});

			it(title="checking CreateULID('Monotonic') function perf with #variables.rounds# rows", body = function( currentSpec ) {

				var tbl = createTable( "Monotonic" );
				if ( isEmpty( tbl ) ) return;

				timer unit="milli" variable="local.timer" {
					transaction {
						loop times=#variables.rounds# {
							populateTable( tbl, CreateULID("Monotonic") );
						}
					}
				}

				systemOutput( "" , true );
				systemOutput( "#variables.rounds# rows with CreateULID('Monotonic') took " & timer, true);
			});

		});
	}

	private function createTable( prefix ){
		if ( isEmpty( variables.mysql ) ) return "";

		var tbl = "test_ulid_" & prefix;

		query datasource=#variables.mysql# {
			echo("DROP TABLE IF EXISTS #tbl#");
		}
		query datasource=#variables.mysql# {
			echo("CREATE TABLE #tbl# ( id varchar(36) NOT NULL PRIMARY KEY ) ");
		}
		return tbl;
	}

	private function populateTable (tbl, id){
		query datasource=#variables.mysql# params={ id: arguments.id, type="varchar" } {
			echo("INSERT into #arguments.tbl# (id) VALUES (:id) "); 
		}
	}
}