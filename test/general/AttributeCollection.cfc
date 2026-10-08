component extends="org.lucee.cfml.test.LuceeTestCase" {

	// Pins attributeCollection dispatch (TagUtil.setAttributeCollection, Reflector.getSetter, cleanArgs).
	// Every case runs the same call at least twice: the first call resolves the setter, later calls
	// hit the JVM-wide setter cache, so a pinning bug shows up here rather than as an order-dependent
	// failure in some unrelated test. Where behaviour diverges from ACF 2025 it is noted inline.

	function beforeAll() {
		variables.dir = getTempDirectory() & "attributeCollection/";
		if ( directoryExists( variables.dir ) ) directoryDelete( variables.dir, true );
		directoryCreate( variables.dir );
		directoryCreate( variables.dir & "sub" );
		fileWrite( variables.dir & "a.cfm", "a" );
		fileWrite( variables.dir & "b.cfm", "b" );
		fileWrite( variables.dir & "c.txt", "c" );
		fileWrite( variables.dir & "sub/d.txt", "d" );
		request.attrcollDir = variables.dir;
		variables.txtFilter = function( path ) {
			return listLast( arguments.path, "." ) == "txt";
		};
	}

	function run( testResults, testBox ) {

		describe( "attributeCollection: overloaded setters, mixed value types across calls", function() {

			it( "directory filter: closure, string, closure, string", function() {
				var seq = [
					{ filter: variables.txtFilter, expected: "c.txt" },
					{ filter: "*.cfm", expected: "a.cfm,b.cfm" },
					{ filter: variables.txtFilter, expected: "c.txt" },
					{ filter: "*.cfm", expected: "a.cfm,b.cfm" }
				];
				for ( var step in seq ) {
					var s = { action: "list", directory: variables.dir, filter: step.filter };
					expect( dirList( s ) ).toBe( step.expected );
				}
			});

			it( "setting enablecfoutputonly: true, reset, false (twice)", function() {
				var seq = [ true, "reset", false, true, "reset", false ];
				var expected = [ "B", "AB", "AB", "B", "AB", "AB" ];
				for ( var i = 1; i <= arrayLen( seq ); i++ ) {
					request.attrcollSetting = { enablecfoutputonly: seq[ i ] };
					var before = fingerprint( request.attrcollSetting );
					var out = "";
					savecontent variable="out" {
						include "attributeCollection/setting.cfm";
					}
					expect( out ).toBe( expected[ i ] );
					expect( fingerprint( request.attrcollSetting ) ).toBe( before );
				}
			});

			it( "cookie expires: http-date, date object, never, days (twice)", function() {
				var seq = [
					"Mon, 31 Dec 2029 23:59:59 GMT",
					createDateTime( 2029, 12, 31, 23, 59, 59 ),
					"never",
					10,
					"Mon, 31 Dec 2029 23:59:59 GMT",
					createDateTime( 2029, 12, 31, 23, 59, 59 ),
					"never",
					10
				];
				for ( var i = 1; i <= arrayLen( seq ); i++ ) {
					var s = { name: "attrcoll_expires_#i#", value: "v#i#", path: "/", expires: seq[ i ] };
					var before = fingerprint( s );
					cfcookie( attributeCollection=s );
					expect( cookie[ "attrcoll_expires_#i#" ] ).toBe( "v#i#" );
					expect( fingerprint( s ) ).toBe( before );
				}
			});

			it( "throw message: string, number, then complex values fail the cast", function() {
				var seq = [ "plain", 42, "plain", 42 ];
				for ( var v in seq ) {
					var s = { type: "attrcoll.test", message: v };
					try {
						cfthrow( attributeCollection=s );
						fail( "cfthrow did not throw" );
					}
					catch ( "attrcoll.test" e ) {
						expect( e.message ).toBe( v );
					}
				}
				// ACF 2025 also rejects these, with an attribute validation error
				for ( var i = 1; i <= 2; i++ ) {
					expect( function() {
						cfthrow( attributeCollection={ type: "attrcoll.test", message: [ 1, 2 ] } );
					}).toThrow( regex="Complex Object Type \[Array\]" );
					expect( function() {
						cfthrow( attributeCollection={ type: "attrcoll.test", message: { a: 1 } } );
					}).toThrow( regex="Complex Object Type \[Struct\]" );
				}
			});

			it( "lock timeout: Double then TimeSpan, the effective timeout (twice)", function() {
				// must be the first lock attributeCollection call in the JVM: a Double resolves to
				// setTimeout( double ) (a literal number is a Long, which resolves to setTimeout( Object )).
				// Pinned to double, a TimeSpan converts to days, truncates to 0ms and Lock falls back to
				// the request's remaining time.
				// Reads the effective timeout from the page context's ActiveLock (Java internals) rather
				// than timing a contended lock: no threads or sleeps, and exact instead of a time window
				var seq = [ javacast( "double", 2 ), createTimeSpan( 0, 0, 0, 2 ), javacast( "double", 2 ), createTimeSpan( 0, 0, 0, 2 ) ];
				for ( var v in seq ) {
					var timeoutInMillis = 0;
					lock attributeCollection={ name: "attrcoll-timeout", timeout: v, type: "exclusive" } {
						var activeLocks = getPageContext().getActiveLocks();
						timeoutInMillis = activeLocks[ arrayLen( activeLocks ) ].timeoutInMillis;
					}
					expect( timeoutInMillis ).toBe( 2000 );
				}
			});

			it( "lock timeout: number, numeric string, decimal, timespan (twice)", function() {
				var seq = [ 5, "5", 1.5, createTimeSpan( 0, 0, 0, 2 ), 5, "5", 1.5, createTimeSpan( 0, 0, 0, 2 ) ];
				for ( var v in seq ) {
					var ran = false;
					lock attributeCollection={ name: "attrcoll-test", timeout: v, type: "exclusive" } {
						ran = true;
					}
					expect( ran ).toBeTrue();
				}
			});

			it( "directory acl: string then array (S3 only, ignored for local listings)", function() {
				var seq = [ "private", [ { group: "all", permission: "read" } ], "private", [ { group: "all", permission: "read" } ] ];
				for ( var v in seq ) {
					var s = { action: "list", directory: variables.dir, filter: "*.cfm", acl: v };
					expect( dirList( s ) ).toBe( "a.cfm,b.cfm" );
				}
			});
		});

		describe( "attributeCollection: unconvertible value on a FIXED tag", function() {

			it( "directory recurse=notabool is dropped silently via attributeCollection", function() {
				// ACF 2025 throws an attribute validation error here
				for ( var i = 1; i <= 2; i++ ) {
					var s = { action: "list", directory: variables.dir, recurse: "notabool" };
					expect( dirList( s ) ).toBe( "a.cfm,b.cfm,c.txt,sub" );
				}
			});

			it( "lock timeout=notanumber throws via attributeCollection (Object setter casts)", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( function() {
						lock attributeCollection={ name: "attrcoll-test", timeout: "notanumber" } {}
					}).toThrow( regex="notanumber" );
				}
			});
		});

		describe( "attributeCollection: wrapped Java values", function() {

			it( "throw object=java exception (Object-typed setter)", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var caught = false;
					try {
						cfthrow( attributeCollection={ object: createObject( "java", "java.lang.IllegalStateException" ).init( "boom#i#" ) } );
					}
					catch ( any e ) {
						caught = true;
						expect( e.type ).toBe( "java.lang.IllegalStateException" );
						expect( e.message ).toBe( "boom#i#" );
					}
					expect( caught ).toBeTrue();
				}
			});

			it( "dump var=uninitialised JavaObject matches the literal attribute", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var viaColl = "";
					var viaLiteral = "";
					savecontent variable="viaColl" {
						cfdump( attributeCollection={ var: createObject( "java", "java.util.ArrayList" ), format: "text" } );
					}
					savecontent variable="viaLiteral" {
						cfdump( var=createObject( "java", "java.util.ArrayList" ), format="text" );
					}
					expect( normaliseDump( viaColl ) ).toBe( normaliseDump( viaLiteral ) );
				}
			});

			it( "invoke: JavaObject as an undeclared attr, top level and nested in a struct", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var r = "";
					var s = {
						component: new attributeCollection.Target(),
						method: "report",
						returnVariable: "local.r",
						j: createObject( "java", "java.util.ArrayList" ),
						nested: { j: createObject( "java", "java.util.ArrayList" ) }
					};
					cfinvoke( attributeCollection=s );
					expect( isArray( r.j ) ).toBeTrue();
					expect( arrayLen( r.j ) ).toBe( 0 );
					expect( isArray( r.nested.j ) ).toBeTrue();
					expect( arrayLen( r.nested.j ) ).toBe( 0 );
				}
			});

			it( "module: JavaObject as a dynamic attr, top level and nested in a struct", function() {
				for ( var i = 1; i <= 2; i++ ) {
					modOut( { j: createObject( "java", "java.util.ArrayList" ), nested: { j: createObject( "java", "java.util.ArrayList" ) } } );
					var echo = request.attrcollEcho;
					expect( isArray( echo.j ) ).toBeTrue();
					expect( arrayLen( echo.j ) ).toBe( 0 );
					expect( isArray( echo.nested.j ) ).toBeTrue();
					expect( arrayLen( echo.nested.j ) ).toBe( 0 );
				}
			});

			it( "throw detail=struct holding a JavaObject is dropped (String setter, no match)", function() {
				for ( var i = 1; i <= 2; i++ ) {
					try {
						cfthrow( attributeCollection={ type: "attrcoll.test", message: "m", detail: { j: createObject( "java", "java.util.ArrayList" ) } } );
						fail( "cfthrow did not throw" );
					}
					catch ( "attrcoll.test" e ) {
						expect( e.detail ).toBe( "" );
					}
				}
			});
		});

		describe( "attributeCollection: aliases and key case", function() {

			it( "directory name and its alias variable in one struct: name wins, either order", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var qN = nullValue();
					var qV = nullValue();
					directory attributeCollection={ action: "list", directory: variables.dir, name: "local.qN", variable: "local.qV" };
					expect( isNull( qN ) ).toBeFalse();
					expect( isNull( qV ) ).toBeTrue();

					qN = nullValue();
					qV = nullValue();
					var ordered = structNew( "ordered" );
					ordered.action = "list";
					ordered.directory = variables.dir;
					ordered.variable = "local.qV";
					ordered.name = "local.qN";
					directory attributeCollection=ordered;
					expect( isNull( qN ) ).toBeFalse();
					expect( isNull( qV ) ).toBeTrue();
				}
			});

			it( "lock result alias variable", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var lr = nullValue();
					lock attributeCollection={ id: "attrcoll-test-id", timeout: 2, variable: "local.lr" } {}
					expect( lr.succeeded ).toBeTrue();
				}
			});

			var caseShapes = [
				{ label: "VARIABLE", dirKey: "DIRECTORY", s: { "ACTION": "list", "VARIABLE": "local.qq" }, expected: "a.cfm,b.cfm,c.txt,sub" },
				{ label: "Variable", dirKey: "Directory", s: { "Action": "list", "Variable": "local.qq" }, expected: "a.cfm,b.cfm,c.txt,sub" },
				{ label: "vArIaBlE", dirKey: "dIrEcToRy", s: { "aCtIoN": "list", "vArIaBlE": "local.qq" }, expected: "a.cfm,b.cfm,c.txt,sub" },
				{ label: "NAME", dirKey: "DIRECTORY", s: { "ACTION": "list", "NAME": "local.qq" }, expected: "a.cfm,b.cfm,c.txt,sub" },
				{ label: "Name", dirKey: "Directory", s: { "Action": "list", "Name": "local.qq" }, expected: "a.cfm,b.cfm,c.txt,sub" },
				{ label: "FILTER", dirKey: "DIRECTORY", s: { "ACTION": "list", "NAME": "local.qq", "FILTER": "*.txt" }, expected: "c.txt" }
			];
			for ( var shape in caseShapes ) {
				it( title="key case [#shape.label#]", data=shape, body=function( data ) {
					for ( var i = 1; i <= 2; i++ ) {
						var s = duplicate( data.s );
						s[ data.dirKey ] = variables.dir;
						var before = fingerprint( s );
						var qq = nullValue();
						directory attributeCollection=s;
						expect( names( qq ) ).toBe( data.expected );
						expect( fingerprint( s ) ).toBe( before );
					}
				});
			}
		});

		describe( "attributeCollection: compile path and call syntax", function() {

			var syntaxes = [ "tagLower", "tagUpper", "tagMixed", "scriptLower", "scriptUpper", "scriptMixed", "functionLower", "functionMixed" ];
			for ( var syntax in syntaxes ) {
				it( title="directory via [#syntax#]", data={ syntax: syntax }, body=function( data ) {
					request.attrcollArgs = { action: "list", directory: variables.dir, filter: "*.cfm", name: "request.attrcollQ" };
					for ( var i = 1; i <= 2; i++ ) {
						request.attrcollSyntax = {};
						include "attributeCollection/syntax/#data.syntax#.cfm";
						expect( request.attrcollSyntax.literal ).toBe( "a.cfm,b.cfm" );
						expect( request.attrcollSyntax.coll ).toBe( "a.cfm,b.cfm" );
					}
				});
			}
		});

		describe( "attributeCollection: precedence and merge", function() {

			it( "explicit attribute beats the same key in the struct (script)", function() {
				var newDir = variables.dir & "prec-create";
				for ( var i = 1; i <= 2; i++ ) {
					if ( directoryExists( newDir ) ) directoryDelete( newDir, true );
					var pq = nullValue();
					directory action="list" directory=newDir name="local.pq" attributeCollection={ action: "create" };
					expect( directoryExists( newDir ) ).toBeFalse();
					expect( isNull( pq ) ).toBeFalse();
				}
			});

			it( "explicit attribute beats an alias of it in the struct, both directions (script)", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var qa = nullValue();
					var qb = nullValue();
					directory action="list" directory=variables.dir name="local.qa" attributeCollection={ variable: "local.qb" };
					expect( isNull( qa ) ).toBeFalse();
					expect( isNull( qb ) ).toBeTrue();

					var qc = nullValue();
					var qd = nullValue();
					directory action="list" directory=variables.dir variable="local.qc" attributeCollection={ name: "local.qd" };
					expect( isNull( qc ) ).toBeFalse();
					expect( isNull( qd ) ).toBeTrue();
				}
			});

			it( "explicit attribute beats the struct on a custom tag", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var out = "";
					savecontent variable="out" {
						module template="attributeCollection/echoAttrs.cfm" a="explicit" attributeCollection={ a: "coll", b: "coll" };
					}
					expect( out ).toBe( "A=explicit;B=coll" );
				}
			});

			it( "precedence in tag syntax", function() {
				for ( var i = 1; i <= 2; i++ ) {
					request.attrcollPrecedence = {};
					include "attributeCollection/precedence.cfm";
					var res = request.attrcollPrecedence;
					expect( res.action.created ).toBeFalse();
					expect( res.action.listed ).toBeTrue();
					expect( res.nameVsAlias.qa ).toBeTrue();
					expect( res.nameVsAlias.qb ).toBeFalse();
					expect( res.aliasVsName.qc ).toBeTrue();
					expect( res.aliasVsName.qd ).toBeFalse();
					expect( res.module ).toBe( "A=explicit;B=coll" );
				}
			});

			it( "nested attributeCollection key inside the struct: ignored on a built-in tag", function() {
				// ACF 2025 throws an attribute validation error
				for ( var i = 1; i <= 2; i++ ) {
					var qn = nullValue();
					directory attributeCollection={ action: "list", directory: variables.dir, attributeCollection: { name: "local.qn" } };
					expect( isNull( qn ) ).toBeTrue();
				}
			});

			it( "nested attributeCollection key inside the struct: expanded on a custom tag", function() {
				// ACF 2025 keeps it as a literal ATTRIBUTECOLLECTION key instead
				for ( var i = 1; i <= 2; i++ ) {
					expect( modOut( { a: 1, attributeCollection: { b: 2 } } ) ).toBe( "A=1;B=2" );
				}
			});
		});

		describe( "attributeCollection: collection types", function() {

			it( "plain struct, ordered struct and empty struct", function() {
				var ordered = structNew( "ordered" );
				ordered.action = "list";
				ordered.directory = variables.dir;
				ordered.filter = "*.txt";
				for ( var i = 1; i <= 2; i++ ) {
					expect( dirList( { action: "list", directory: variables.dir, filter: "*.txt" } ) ).toBe( "c.txt" );
					expect( dirList( ordered ) ).toBe( "c.txt" );
					var q = "";
					directory action="list" directory=variables.dir name="local.q" attributeCollection={};
					expect( names( q ) ).toBe( "a.cfm,b.cfm,c.txt,sub" );
					expect( modOut( {} ) ).toBe( "" );
				}
			});

			// Lucee fails these with a raw java.lang.IncompatibleClassChangeError (the compiled code assumes a Struct).
			// ACF 2025 accepts a java.util.Map, and rejects the others with
			// "The attributeCollection attribute in the cfdirectory tag can only be a structure."
			// TODO accept java.util.Map, compiled code casts to Struct (IncompatibleClassChangeError)
			it( title="[java.util.LinkedHashMap] works on a built-in and a custom tag", skip=true, body=function() {
				for ( var i = 1; i <= 2; i++ ) {
					var m = badCollection( "map" );
					var q = "";
					directory directory=variables.dir name="local.q" attributeCollection=m;
					expect( names( q ) ).toBe( "c.txt" );
					var out = "";
					savecontent variable="out" {
						module template="attributeCollection/echoAttrs.cfm" attributeCollection=m;
					}
					expect( out ).toBe( "FILTER=*.txt" );
				}
			});

			var badTypes = [
				{ label: "array", type: "array" },
				{ label: "string", type: "string" },
				{ label: "query", type: "query" }
			];
			for ( var badType in badTypes ) {
				// TODO raw IncompatibleClassChangeError, should throw a clean "can only be a struct" error
				it( title="[#badType.label#] throws a clean error on a built-in and a custom tag", skip=true, data=badType, body=function( data ) {
					for ( var i = 1; i <= 2; i++ ) {
						var v = badCollection( data.type );
						expect( function() {
							var q = "";
							directory directory=variables.dir name="local.q" attributeCollection=v;
						}).toThrow( regex="attributeCollection" );
						expect( function() {
							module template="attributeCollection/echoAttrs.cfm" attributeCollection=v;
						}).toThrow( regex="attributeCollection" );
					}
				});
			}
		});

		describe( "attributeCollection: attribute type dispatch", function() {

			it( "unknown key on a FIXED tag is ignored", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( dirList( { action: "list", directory: variables.dir, bogus: 1 } ) ).toBe( "a.cfm,b.cfm,c.txt,sub" );
				}
			});

			it( "unknown literal attribute on a FIXED tag is a compile error", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( function() {
						include "attributeCollection/bogusLiteral.cfm";
					}).toThrow( regex="Attribute \[bogus\] is not allowed for statement \[directory\]" );
				}
			});

			it( "unknown key on a DYNAMIC tag arrives", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( modOut( { bogus: 1 } ) ).toBe( "BOGUS=1" );
				}
			});

			it( "unknown key on a MIXED tag (invoke) becomes an argument", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var r = "";
					cfinvoke( attributeCollection={ component: new attributeCollection.Target(), method: "report", returnVariable: "local.r", bogus: i } );
					expect( r.bogus ).toBe( i );
				}
			});

			it( "required attribute supplied only by the struct", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var rq = "";
					directory attributeCollection={ action: "list", directory: variables.dir, name: "local.rq" };
					expect( names( rq ) ).toBe( "a.cfm,b.cfm,c.txt,sub" );
				}
			});

			it( "required attribute missing from the struct", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( function() {
						var rq = "";
						directory attributeCollection={ action: "list", name: "local.rq" };
					}).toThrow( regex="Attribute \[directory\] is required but missing" );
					expect( function() {
						cfcookie( attributeCollection={ value: "x" } );
					}).toThrow( regex="Attribute \[name\] is required but missing" );
				}
			});

			it( "null value with null support off: custom tag gets the key, built-in tag skips it", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( modOut( { a: javacast( "null", "" ), b: 1 } ) ).toBe( "A=<null>;B=1" );
					expect( dirList( { action: "list", directory: variables.dir, filter: javacast( "null", "" ) } ) ).toBe( "a.cfm,b.cfm,c.txt,sub" );
				}
			});

			it( "null values with null support on", function() {
				var res = nullSupportResults();
				for ( var i = 1; i <= 2; i++ ) {
					expect( res[ "filter#i#" ] ).toBe( "a.cfm,c.txt" );
					expect( res[ "recurse#i#" ] ).toBe( "a.cfm,c.txt" );
					expect( res[ "directory#i#" ] ).toBe( "ERR Attribute [directory] is required but missing" );
					expect( res[ "module#i#" ] ).toBe( "A=<null>;B=1" );
				}
			});

			// TODO null reaching a String setter NPEs inside the tag, should be skipped like other nulls
			it( title="null on a String setter, null support on", skip=true, body=function() {
				var res = nullSupportResults();
				for ( var i = 1; i <= 2; i++ ) {
					expect( res[ "sort#i#" ] ).toBe( "a.cfm,c.txt" );
				}
			});
		});

		describe( "attributeCollection: caller's struct is left alone", function() {

			it( "alias keys in the struct are not rewritten", function() {
				var s = { action: "list", directory: variables.dir, variable: "local.sq", filter: "*.cfm" };
				var before = fingerprint( s );
				for ( var i = 1; i <= 2; i++ ) {
					var sq = "";
					directory attributeCollection=s;
					expect( names( sq ) ).toBe( "a.cfm,b.cfm" );
					expect( fingerprint( s ) ).toBe( before );
					expect( structCount( s ) ).toBe( 4 );
					expect( structKeyExists( s, "name" ) ).toBeFalse();
				}
			});

			it( "custom tag struct is not rewritten", function() {
				var s = { template: "attributeCollection/echoAttrs.cfm", a: 1 };
				var before = fingerprint( s );
				for ( var i = 1; i <= 2; i++ ) {
					var out = "";
					savecontent variable="out" {
						module attributeCollection=s;
					}
					expect( out ).toBe( "A=1" );
					expect( fingerprint( s ) ).toBe( before );
				}
			});
		});

		describe( "attributeCollection: literal coercion", function() {

			it( "directory recurse string yes / no reaches the boolean setter", function() {
				for ( var i = 1; i <= 2; i++ ) {
					expect( dirList( { action: "list", directory: variables.dir, recurse: "yes", filter: "*.txt" } ) ).toBe( "c.txt,d.txt" );
					expect( dirList( { action: "list", directory: variables.dir, recurse: "no", filter: "*.txt" } ) ).toBe( "c.txt" );
				}
			});
		});

		describe( "attributeCollection: CFC based tag (dump), attributes without a Java setter", function() {

			it( "dump via attributeCollection matches the literal attributes", function() {
				for ( var i = 1; i <= 2; i++ ) {
					var viaColl = "";
					var viaLiteral = "";
					savecontent variable="viaColl" {
						cfdump( attributeCollection={ var: { a: 1, b: "two", c: [ 1, 2, 3 ] }, format: "text", label: "L", expand: true, top: 2, keys: 2 } );
					}
					savecontent variable="viaLiteral" {
						cfdump( var={ a: 1, b: "two", c: [ 1, 2, 3 ] }, format="text", label="L", expand=true, top=2, keys=2 );
					}
					expect( normaliseDump( viaColl ) ).toBe( normaliseDump( viaLiteral ) );
					expect( normaliseDump( viaColl ) ).toInclude( "Struct [Entries: 3 (showing top 2)]" );
				}
			});

			// TODO CFTag.validateAttributes misses a single unknown key
			it( title="dump with unknown keys: one or two both throw", skip=true, body=function() {
				// CFTag.validateAttributes only looks for unknown keys when the attributes scope outgrows the
				// declared attribute count, so a single unknown key currently slips through
				for ( var i = 1; i <= 2; i++ ) {
					expect( function() {
						var out = "";
						savecontent variable="out" {
							cfdump( attributeCollection={ var: { a: 1 }, format: "text", foo: "bar" } );
						}
					}).toThrow( regex="attribute \[foo\] is not supported for tag \[Dump\]" );
					expect( function() {
						var out2 = "";
						savecontent variable="out2" {
							cfdump( attributeCollection={ var: { a: 1 }, format: "text", label: "L", bogus: 1, foo: "bar" } );
						}
					}).toThrow( regex="attribute \[(foo|bogus)\] is not supported for tag \[Dump\]" );
				}
			});
		});

		describe( "attributeCollection: include and loop", function() {

			// TODO compiler NPE, TagInclude assumes a literal template attribute
			it( title="include with template only in the struct", skip=true, body=function() {
				// ACF 2025 includes the template; Lucee's TagInclude assumes a literal template attribute (compiler NPE)
				for ( var i = 1; i <= 2; i++ ) {
					var out = "";
					savecontent variable="out" {
						include "attributeCollection/includeScript.cfm";
					}
					expect( trim( out ) ).toBe( "included" );
					savecontent variable="out" {
						include "attributeCollection/includeTag.cfm";
					}
					expect( trim( out ) ).toBe( "included" );
				}
			});

			// the Loop TTE picks the loop type at compile time from literal attributes, so a struct can't drive it.
			// Lucee currently falls through to TYPE_NOTHING and runs the body once; ACF 2025 rejects it at compile time
			var loopTypes = [ "array", "fromTo", "collection", "tag" ];
			for ( var loopType in loopTypes ) {
				// TODO Loop TTE ignores attributeCollection, falls through to TYPE_NOTHING
				it( title="loop [#loopType#] with attributeCollection is a compile error", skip=true, data={ type: loopType }, body=function( data ) {
					for ( var i = 1; i <= 2; i++ ) {
						expect( function() {
							include "attributeCollection/loop/#data.type#.cfm";
						}).toThrow( regex="attributeCollection" );
					}
				});
			}
		});

		describe( "attributeCollection: concurrency", function() {

			it( "directory filter, closure vs string interleaved across threads", function() {
				var threadNames = [];
				var prefix = "attrcoll-" & createUUID() & "-";
				for ( var t = 1; t <= 4; t++ ) {
					arrayAppend( threadNames, prefix & t );
					thread name=prefix & t action="run" phase=t dir=variables.dir {
						var txt = function( path ) {
							return listLast( arguments.path, "." ) == "txt";
						};
						thread.mismatches = [];
						for ( var n = 1; n <= 25; n++ ) {
							var useClosure = ( ( n + attributes.phase ) % 2 ) == 0;
							var q = "";
							directory attributeCollection={ action: "list", directory: attributes.dir, filter: useClosure ? txt : "*.cfm" } name="local.q";
							var got = listSort( valueList( q.name ), "textnocase" );
							var want = useClosure ? "c.txt" : "a.cfm,b.cfm";
							if ( got != want ) arrayAppend( thread.mismatches, "#n#: #got# != #want#" );
						}
					}
				}
				thread action="join" name=arrayToList( threadNames );
				for ( var tn in threadNames ) {
					var th = cfthread[ tn ];
					expect( th.status ).toBe( "COMPLETED", structKeyExists( th, "error" ) ? th.error.message : "" );
					expect( th.mismatches ).toBeEmpty();
				}
			});
		});
	}

	private string function dirList( required struct s ) {
		var before = fingerprint( arguments.s );
		var q = "";
		directory attributeCollection=arguments.s name="local.q";
		expect( fingerprint( arguments.s ) ).toBe( before );
		return names( q );
	}

	private string function modOut( required struct s ) {
		var before = fingerprint( arguments.s );
		var out = "";
		savecontent variable="out" {
			module template="attributeCollection/echoAttrs.cfm" attributeCollection=arguments.s;
		}
		expect( fingerprint( arguments.s ) ).toBe( before );
		return out;
	}

	private string function names( required query q ) {
		return listSort( valueList( arguments.q.name ), "textnocase" );
	}

	// keys, key count and a shape-level view of each value, to check the caller's struct is untouched
	private string function fingerprint( required struct s ) {
		var keys = structKeyArray( arguments.s );
		arraySort( keys, "textnocase" );
		var parts = [];
		for ( var k in keys ) {
			if ( isNull( arguments.s[ k ] ) ) {
				arrayAppend( parts, k & "=<null>" );
				continue;
			}
			var v = arguments.s[ k ];
			if ( isCustomFunction( v ) || isClosure( v ) ) arrayAppend( parts, k & "=<udf>" );
			else if ( isSimpleValue( v ) ) arrayAppend( parts, k & "=" & v );
			else if ( isStruct( v ) ) arrayAppend( parts, k & "={" & fingerprint( v ) & "}" );
			else if ( isArray( v ) ) arrayAppend( parts, k & "=[" & arrayLen( v ) & "]" );
			else arrayAppend( parts, k & "=<object>" );
		}
		return structCount( arguments.s ) & ":" & arrayToList( parts, "|" );
	}

	private struct function nullSupportResults() {
		var nullDir = getTempDirectory() & "attributeCollection-null/";
		if ( directoryExists( nullDir ) ) directoryDelete( nullDir, true );
		directoryCreate( nullDir );
		fileWrite( nullDir & "a.cfm", "a" );
		fileWrite( nullDir & "c.txt", "c" );
		var result = _internalRequest(
			template: createURI( "attributeCollection/nullSupport/index.cfm" ),
			url: { dir: nullDir }
		);
		return deserializeJSON( result.filecontent.trim() );
	}

	private string function normaliseDump( required string out ) {
		var s = reReplace( arguments.out, 'id="[^"]*"', "", "all" );
		s = reReplace( s, "\{ts '[^']*'\}", "", "all" );
		return trim( reReplace( s, "\s+", " ", "all" ) );
	}

	private any function badCollection( required string type ) {
		switch ( arguments.type ) {
			case "map":
				var m = createObject( "java", "java.util.LinkedHashMap" ).init();
				m.put( "filter", "*.txt" );
				return m;
			case "array":
				return [ "list" ];
			case "string":
				return "action=list";
			case "query":
				return queryNew( "action", "varchar", [ [ "list" ] ] );
		}
		throw "unknown type [#arguments.type#]";
	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrentTemplatePath() ), "\/" )#/";
		return baseURI & "" & calledName;
	}
}
