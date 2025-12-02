component extends="org.lucee.cfml.test.LuceeTestCase" labels="query" {

	variables.ds = server.getDatasource( service="h2", dbFile=server._getTempDir( "LDEV5026" ) );

	function beforeAll() {
		query datasource=ds {
			echo( "DROP TABLE IF EXISTS LDEV5026" );
		}
		query datasource=ds {
			echo( "CREATE TABLE LDEV5026 ( id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) )" );
		}
	}

	function afterAll() {
		query datasource=ds {
			echo( "DROP TABLE IF EXISTS LDEV5026" );
		}
	}

	function run( testResults, testBox ) {
		describe( title="LDEV-5026 insertResult attribute", body=function() {

			beforeEach( function( currentSpec ) {
				query datasource=ds {
					echo( "DELETE FROM LDEV5026" );
				}
			});

			it( title="insertResult returns generatedKey for single insert", body=function() {
				query datasource=ds insertResult="local.res" {
					echo( "INSERT INTO LDEV5026 ( name ) VALUES ( 'test1' )" );
				}

				expect( res ).toBeStruct();
				expect( res ).toHaveKey( "generatedKey" );
				expect( res.generatedKey ).toBeNumeric();
				expect( res.generatedKey ).toBeGT( 0 );
			});

			it( title="insertResult returns recordCount", body=function() {
				query datasource=ds insertResult="local.res" {
					echo( "INSERT INTO LDEV5026 ( name ) VALUES ( 'test1' )" );
				}

				expect( res ).toHaveKey( "recordCount" );
				expect( res.recordCount ).toBe( 1 );
			});

			it( title="insertResult returns executionTime", body=function() {
				query datasource=ds insertResult="local.res" {
					echo( "INSERT INTO LDEV5026 ( name ) VALUES ( 'test1' )" );
				}

				expect( res ).toHaveKey( "executionTime" );
				expect( res ).toHaveKey( "executionTimeNano" );
				expect( res.executionTime ).toBeGTE( 0 );
				expect( res.executionTimeNano ).toBeGTE( 0 );
			});

			it( title="insertResult does NOT contain SQL string", body=function() {
				query datasource=ds insertResult="local.res" {
					echo( "INSERT INTO LDEV5026 ( name ) VALUES ( 'test1' )" );
				}

				expect( structKeyExists( res, "sql" ) ).toBeFalse();
			});

			it( title="insertResult does NOT contain sqlparameters", body=function() {
				query datasource=ds insertResult="local.res" params={ name: "test1" } {
					echo( "INSERT INTO LDEV5026 ( name ) VALUES ( :name )" );
				}

				expect( structKeyExists( res, "sqlparameters" ) ).toBeFalse();
			});

			it( title="insertResult has no generatedKey when no keys generated", body=function() {
				query datasource=ds insertResult="local.res" {
					echo( "UPDATE LDEV5026 SET name = 'updated' WHERE 1=0" );
				}

				expect( structKeyExists( res, "generatedKey" ) ).toBeFalse();
			});

			it( title="insertResult and result cannot be used together", body=function() {
				expect( function() {
					query datasource=ds result="local.res1" insertResult="local.res2" {
						echo( "INSERT INTO LDEV5026 ( name ) VALUES ( 'test1' )" );
					}
				}).toThrow();
			});

			it( title="insertResult works with queryExecute", body=function() {
				queryExecute(
					"INSERT INTO LDEV5026 ( name ) VALUES ( :name )",
					{ name: "test1" },
					{ datasource: ds, insertResult: "local.res" }
				);

				expect( res ).toBeStruct();
				expect( res ).toHaveKey( "generatedKey" );
				expect( res.generatedKey ).toBeNumeric();
				expect( res.generatedKey ).toBeGT( 0 );
			});

			it( title="regular result still contains SQL and sqlparameters", body=function() {
				query datasource=ds result="local.res" params={ name: "test1" } {
					echo( "INSERT INTO LDEV5026 ( name ) VALUES ( :name )" );
				}

				expect( res ).toHaveKey( "sql" );
				expect( res ).toHaveKey( "sqlparameters" );
				expect( res.sql ).toInclude( "INSERT" );
				expect( res.sqlparameters ).toBeArray();
				expect( arrayLen( res.sqlparameters ) ).toBe( 1 );
			});

		});
	}

}
