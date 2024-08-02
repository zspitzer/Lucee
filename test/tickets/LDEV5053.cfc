component extends="org.lucee.cfml.test.LuceeTestCase"  {

	function beforeAll() {
		variables.uri = createURI("LDEV5053");
		if (noMysql()) return;
		variables.ds = server.getDatasource("mysql");
		variables.runs = 2500;
		query datasource=ds {
			echo("DROP TABLE IF EXISTS LDEV5053");
		}
		query datasource=ds{
			echo("CREATE TABLE LDEV5053 ( id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(36))");
		}
		configImport( { "inspectTemplate": "once" }, "server", request.serverAdminPassword );
	}

	function afterAll() {
		if (noMysql()) return;
		query datasource=ds {
			echo("DROP TABLE IF EXISTS LDEV5053");
		}
	}

	function run( testResults , testBox ) {
		describe( "test case for LDEV-5053", function() {

			xit (title = "test simple", skip=noMysql(), body = function( currentSpec ) {
				systemOutput("normal mode: ", true);
				truncateTestTable();
				var arr = [];
				ArraySet( arr, 1, variables.runs, 0 );
				ArrayEach( arr, function( item, idx ){
					systemOutput(idx & ", ");
					var	result = _InternalRequest(
						template : uri & "_mysql/LDEV5053_mysql.cfm",
						url: {
							chaos: false,
							idx: arguments.idx
						}
					);
				});
				expect( getTestRowCount() ).toBe( ArrayLen( arr ) );
			});

			xit (title = "error in arrayEach causes cascading errors (mysql)", skip=noMysql(), body = function( currentSpec ) {
				systemOutput("chaosmode: ", true);
				truncateTestTable();
				var arr = [];
				ArraySet( arr, 1, variables.runs, 0 );
				// every third call with throw an exception
				ArrayEach( arr, function( item, idx ){
					// systemOutput(idx & ", ");
					try {
						var	result = _InternalRequest(
							template : uri & "_mysql/LDEV5053_mysql.cfm",
							url: {
								chaos: true,
								idx: arguments.idx
							}
						);
					} catch ( e ) {
						if ( e.message does not contain "chaos" )
							rethrow;
						// systemOutput(" expected chaos error at [#idx#]", true );
						// ignore
					}
				});

				expect( getTestRowCount() ).toBe( ArrayLen( arr ) );
			});

			it (title = "error in arrayEach causes cascading errors (qoq)", body = function( currentSpec ) {
				systemOutput("chaosmode: ", true);
				var arr = [];
				ArraySet( arr, 1, variables.runs, 0 );
				// every third call with throw an exception
				ArrayEach( arr, function( item, idx ){
					//systemOutput(idx & ", ");
					try {
						var	result = _InternalRequest(
							template : uri & "_qoq/LDEV5053_qoq.cfm",
							url: {
								chaos: true,
								idx: arguments.idx
							}
						);
					} catch ( e ) {
						if ( e.message does not contain "chaos" )
							rethrow;
						//systemOutput(" expected chaos error at [#idx#]", true );
						// ignore
					}
				});
			});
		});
	}

	private string function createURI(string calledName){
		var baseURI = "/test/#listLast(getDirectoryFromPath(getCurrentTemplatepath()),"\/")#/";
		return baseURI & ""&calledName;
	}

	private function truncateTestTable() {
		query datasource=ds {
			echo( "TRUNCATE TABLE ldev5053" );
		}
	}

	private function getTestRowCount() {
		query datasource=ds name="local.q" {
			echo( "SELECT count(*) as r FROM ldev5053" );
		}
		return q.r;
	}

	private function noMysql(){
		return (structCount( server.getDatasource("mysql") ) eq 0);
	}
}