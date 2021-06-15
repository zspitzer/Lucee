component extends="org.lucee.cfml.test.LuceeTestCase"{
	function beforeAll(){
		variables.uri = createURI("LDEV1661");
		setTimezone("UTC");
	}
	// skip closure
	function isNotSupported() {
		var mySql = getCredentials();
		return isNull(mysql) || structCount(mySQL)==0;
	}
	function run( testResults , testBox ) {
		describe( title="Test suite for LDEV-1661", body=function() {
			it(title = "Checking timestamp with createDateTime()",skip=isNotSupported(), body = function( currentSpec ) {
				local.result = _InternalRequest(
					template:"#variables.uri#/test.cfm",
					forms:{Scene:1}
				);
				SystemOutput("", true);
				SystemOutput("JVM timezone", true);
				SystemOutput(GetTimeZoneInfo( "jvm" ), true);
				SystemOutput("CF timezone", true);
				SystemOutput(GetTimeZoneInfo( GetTimeZone() ), true);
				SystemOutput("", true);

				expect(local.result.filecontent.trim()).toBe("{ts '2018-08-01 12:00:00'}");
			});
			it(title = "Checking timestamp with createOdbcDateTime()",skip=isNotSupported(), body = function( currentSpec ) {
				local.result = _InternalRequest(
					template:"#variables.uri#/test.cfm",
					forms:{Scene:2}
				);
				expect(local.result.filecontent.trim()).toBe("{ts '2018-08-01 12:00:00'}");
			});
			it(title = "Checking timestamp column in string format",skip=isNotSupported(), body = function( currentSpec ) {
				local.result = _InternalRequest(
					template:"#variables.uri#/test.cfm",
					forms:{Scene:3}
				);
				expect(local.result.filecontent.trim()).toBe("{ts '2018-08-01 12:00:00'}");
			});
			it(title = "Checking timestamp with cfqueryparam",skip=isNotSupported(), body = function( currentSpec ) {
				local.result = _InternalRequest(
					template:"#variables.uri#/test.cfm",
					forms:{Scene:4}
				);
				expect(local.result.filecontent.trim()).toBe("{ts '2018-08-01 12:00:00'}");
			});
		});
	}

	// private Function//
	private string function createURI(string calledName){
		var baseURI="/test/#listLast(getDirectoryFromPath(getCurrenttemplatepath()),"\/")#/";
		return baseURI&""&calledName;
	}

	private struct function getCredentials() {
		// getting the credentials from the enviroment variables
		return server.getDatasource("mysql");
	}
} 

