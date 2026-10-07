component extends="org.lucee.cfml.test.LuceeTestCase" labels="serialization" {

	function beforeAll() {
		// shaped like a real session: nested struct, array of structs, dates, numbers, string, query
		variables.fixture = {
			user: { id: 4711, name: "zac", created: now(), admin: false },
			perms: [ { n: "a", g: true }, { n: "b", g: false } ],
			count: 42,
			ratio: 0.5,
			when: now(),
			str: "hello",
			q: queryNew( "id,name", "integer,varchar", [ [ 1, "one" ], [ 2, "two" ] ] )
		};
	}

	function run( testResults, testBox ) {

		describe( title="Java serialisation: resolving core classes must not throw", body=function() {

			it( title="objectLoad round-trips a session-shaped struct", body=function( currentSpec ) {
				var back = objectLoad( objectSave( variables.fixture ) );
				expect( back.user.id ).toBe( 4711 );
				expect( back.user.name ).toBe( "zac" );
				expect( back.user.admin ).toBeFalse();
				expect( back.perms[ 2 ].g ).toBeFalse();
				expect( back.count ).toBe( 42 );
				expect( back.q.recordcount ).toBe( 2 );
				expect( back.q.name[ 2 ] ).toBe( "two" );
				expect( dateTimeFormat( back.when, "iso" ) ).toBe( dateTimeFormat( variables.fixture.when, "iso" ) );
			});

			it( title="objectLoad resolves core classes without a single ClassNotFoundException", body=function( currentSpec ) {
				var bytes = objectSave( variables.fixture );
				var rec = startRecording();
				for ( var i = 1; i <= 20; i++ ) {
					objectLoad( bytes );
				}
				var counts = stopAndCount( rec );
				expect( counts.fromResolveClass ).toBe( 0,
					"ClassNotFoundException thrown from JavaConverter$ObjectInputStreamImpl.resolveClass while deserialising core classes: " & serializeJson( counts.messages ) );
			});

			it( title="negative control: a classloader that cannot see core makes the detector fire", body=function( currentSpec ) {
				// CFMLEngineFactory.getInstance() returns the loader-jar wrapper; its classloader cannot see core classes
				var wrapperLoader = createObject( "java", "lucee.loader.engine.CFMLEngineFactory" ).getInstance().getClass().getClassLoader();
				var bytes = objectSave( variables.fixture );
				var rec = startRecording();
				var bais = createObject( "java", "java.io.ByteArrayInputStream" ).init( bytes );
				var ois = createObject( "java", "lucee.runtime.converter.JavaConverter$ObjectInputStreamImpl" ).init( wrapperLoader, bais );
				var back = ois.readObject();
				ois.close();
				var counts = stopAndCount( rec );
				expect( back.user.id ).toBe( 4711 );
				expect( counts.fromResolveClass ).toBeGT( 0, "the JFR detector saw no ClassNotFoundException from resolveClass even with a loader that cannot see core" );
			});

		});

		describe( title="datasource session storage with sessionCluster=true", body=function() {

			it( title="loads the session on every request without ClassNotFoundException", skip=skipMysql(), body=function( currentSpec ) {
				var uri = createURI( "JavaSerialization" );
				var userValue = "user-" & createUUID();

				var setResp = _InternalRequest( template: "#uri#/set.cfm", url: { user: userValue } );
				expect( setResp.fileContent ).toBeJson();
				expect( deserializeJson( setResp.fileContent ).sessionUser ).toBe( userValue );
				var cookies = {
					cfid: setResp.session.cfid,
					cftoken: setResp.session.cftoken
				};

				var rec = startRecording();
				var resp = "";
				for ( var i = 1; i <= 5; i++ ) {
					resp = _InternalRequest( template: "#uri#/get.cfm", cookies: cookies );
				}
				var counts = stopAndCount( rec );

				expect( deserializeJson( resp.fileContent ).sessionUser ).toBe( userValue );
				expect( counts.fromResolveClass ).toBe( 0,
					"ClassNotFoundException thrown from JavaConverter$ObjectInputStreamImpl.resolveClass while loading the session from datasource storage: " & serializeJson( counts.messages ) );
			});

		});
	}

	// --- JFR helpers: the only way to observe caught exceptions from CFML -----------------

	private any function startRecording() {
		var rec = createObject( "java", "jdk.jfr.Recording" ).init();
		rec.enable( "jdk.JavaExceptionThrow" ).withStackTrace();
		rec.start();
		return rec;
	}

	/**
	 * stops the recording, dumps it under the temp dir (previous dump removed first, the last one stays for inspection)
	 * and counts ClassNotFoundException events whose stack passes through JavaConverter$ObjectInputStreamImpl.resolveClass
	 */
	private struct function stopAndCount( required any rec ) {
		arguments.rec.stop();
		var file = getTempDirectory() & "JavaSerialization-cnfe.jfr";
		if ( fileExists( file ) ) fileDelete( file );
		var path = createObject( "java", "java.io.File" ).init( file ).toPath();
		arguments.rec.dump( path );
		arguments.rec.close();

		var counts = { cnfe: 0, fromResolveClass: 0, messages: {}, dump: file };
		var events = createObject( "java", "jdk.jfr.consumer.RecordingFile" ).readAllEvents( path );
		for ( var e in events ) {
			if ( e.getEventType().getName() != "jdk.JavaExceptionThrow" ) continue;
			if ( e.getClass( "thrownClass" ).getName() != "java.lang.ClassNotFoundException" ) continue;
			counts.cnfe++;
			var st = e.getStackTrace();
			if ( isNull( st ) ) continue;
			for ( var f in st.getFrames() ) {
				var m = f.getMethod();
				if ( m.getName() == "resolveClass" && findNoCase( "JavaConverter$ObjectInputStreamImpl", m.getType().getName() ) ) {
					var msg = e.getString( "message" );
					counts.messages[ msg ] = ( counts.messages[ msg ] ?: 0 ) + 1;
					counts.fromResolveClass++;
					break;
				}
			}
		}
		return counts;
	}

	private boolean function skipMysql() {
		return isEmpty( server.getDatasource( "mysql" ) );
	}

	private string function createURI( required string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrentTemplatePath() ), "\/" )#/";
		return baseURI & "" & arguments.calledName;
	}

}
