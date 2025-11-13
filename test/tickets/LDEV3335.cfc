component extends="org.lucee.cfml.test.LuceeTestCase" {
	function beforeAll() {
		variables.uri = createURI("LDEV3335");
	}
	function run( testResults, testBox ){
		describe( title="Component size tests", body=function(){
			xit( title="Check size of the component with no accessors", body=function( currentSpec ){
				local.result = _InternalRequest(
					template : "#uri#\test.cfm",
					FORM : { scene : 1 }
				);
				expect(trim(result.fileContent)).toBeLT(1000);
			});
			xit( title="Check size of the component with manual setters/getters", body=function( currentSpec ){
				local.result = _InternalRequest(
					template : "#uri#\test.cfm",
					FORM : { scene : 2 }
				);
				expect(trim(result.fileContent)).toBeLT(5000);
			});
			xit( title="Check size of the component with accessors", body=function( currentSpec ){
				local.result = _InternalRequest(
					template : "#uri#\test.cfm",
					FORM : { scene : 3 }
				);
				expect(trim(result.fileContent)).toBeLT(5000);
			});
		});
		describe( title="Static scope inheritance tests", body=function(){
			it( title="Access static method on base component", body=function( currentSpec ){
				var result = LDEV3335.BaseComponent::baseStaticMethod();
				expect(result).toBe("base static method");
			});
			it( title="Access static method on child component", body=function( currentSpec ){
				var result = LDEV3335.ChildComponent::childStaticMethod();
				expect(result).toBe("child static method");
			});
			it( title="Access base static method through child (bcp null issue)", body=function( currentSpec ){
				var result = LDEV3335.ChildComponent::baseStaticMethod();
				expect(result).toBe("base static method");
			});
			it( title="Access static method via variable reference (benchmark pattern)", body=function( currentSpec ){
				local.result = _InternalRequest(
					template : "#uri#\testStaticViaVariable.cfm"
				);
				expect(trim(result.fileContent)).toBe('{"table":true,"name":true}');
			});
		});
	}
	private string function createURI(string calledName){
		var baseURI = "/test/#listLast(getDirectoryFromPath(getCurrenttemplatepath()),"\/")#/";
		return baseURI&""&calledName;
	}
}