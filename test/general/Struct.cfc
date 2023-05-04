component extends="org.lucee.cfml.test.LuceeTestCase"{
	function beforeAll(){}

	function afterAll(){}

	function run( testResults , testBox ) {
		describe( "tests for the type sruct", function() {

			it(title="test listener struct return a value", body=function(){
				var sct=structNew(onMissingKey:function(key,data){
    				return "notexistingvalue";
				});
				sct.a=1;
				
				expect(sct.a).toBe(1);
				expect(sct.notexistingkey).toBe("notexistingvalue");
				expect(structKeyList(sct)).toBe("a");
			});

			it(title="test listener struct return a value and store it", body=function(){
				var sct=structNew(onMissingKey:function(key,data){
					return data[key] = "notexistingvalue";
				});
				sct.a=1;
				
				expect(sct.a).toBe(1);
				expect(sct.notexistingkey).toBe("notexistingvalue");
				expect(listSort(structKeyList(sct),"textnocase")).toBe("a,notexistingkey");
			});

			it(title="test listener struct throwing an exception", body=function(){
				var sct=structNew(onMissingKey:function(key,data){
					throw "sorry but we cannot help!";
				});
				
				var msg="";
				try {
					var a=sct.notexistingkey;
				}
				catch(e) {
					msg=e.message;
				}
				expect(msg).toBe("sorry but we cannot help!");
			});

			it(title="test listener tracks accessed values", body=function(){
				var accessed = {};
				var sct=structNew(onMissingKey:function(key,data,isMissing){
					if (!isMissing) accessed[arguments.key]=true;
					else return "notexistingvalue";
				});
				sct.a=1;
				var b = sct.a; // populate accessed, onMissingKey called with isMissing=false
				expect(accessed).toHaveKey("a");
				expect(sct.notexistingkey).toBe("notexistingvalue");
				expect(structKeyList(sct)).toBe("a");
			});

		});
	}
}
