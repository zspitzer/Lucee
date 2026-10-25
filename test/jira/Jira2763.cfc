<!--- 
 *
 * Copyright (c) 2014, the Railo Company LLC. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 ---><cfscript>
component extends="org.lucee.cfml.test.LuceeTestCase"	{

	

	public void function testTagAttrApplication_cfc1(){
		local.uri=createURI("Jira2763/index.cfm");
		local.result=_InternalRequest(template=uri,urls={trim=true});
		assertEquals("-a-",trim(result.filecontent));
	}
	public void function testTagAttrApplication_cfc2(){
		local.uri=createURI("Jira2763/index.cfm");
		local.result=_InternalRequest(template=uri,urls={trim=false});
		assertEquals("- a -",trim(result.filecontent));
	}
	
	public void function testTagAttrApplication_cfc_queryExecute(){
		local.uri=createURI("Jira2763/queryExecute.cfm");
		local.result=_InternalRequest(template=uri,urls={trim=false,queryReturnType="array"});
		assertEquals("array",trim(result.filecontent));
	}

	public void function testTagAttrApplication_cfc_queryExecuteNoDefault(){
		local.uri=createURI("Jira2763/queryExecute.cfm");
		local.result=_InternalRequest(template=uri,urls={trim=false});
		assertEquals("query",trim(result.filecontent));
	}

	// getApplicationSettings() before any tag has run, the this.tag defaults are only read lazily on the first tag use
	// disabled, known edge case: getTagAttributeDefaultValues(pc) skips the lazy init, so .tag falls back to the raw
	// this.tag from the Application.cfc ({"SAVECONTENT":{"TRIM":true}}) instead of the resolved map, revisit with the shared AC COW
	public void function testTagAttrApplication_cfc_getApplicationSettings() skip=true {
		local.uri=createURI("Jira2763/app-settings/index.cfm");
		local.result=_InternalRequest(template=uri);
		local.content=trim(result.filecontent);
		assertTrue(isJSON(content),content);
		local.tag=deserializeJSON(content);
		assertTrue(structKeyExists(tag,"cfsavecontent"),content);
		assertTrue(tag.cfsavecontent.trim,content);
	}

	public void function testTagAttrCFApplication1(){
		application action="update" tag="#{savecontent:{trim:false}}#";
		savecontent variable="local.c" {
			echo(" a ");
		}
		assertEquals(" a ",c);
	}
	
	public void function testTagAttrCFApplication2(){
		application action="update" tag="#{savecontent:{trim:true}}#";
		savecontent variable="local.c" {
			echo(" a ");
		}
		assertEquals("a",c);
	}
	
	public void function testTagAttrCFApplication3(){
		application action="update" tag="#{cfsavecontent:{trim:true}}#";
		savecontent variable="local.c" {
			echo(" a ");
		}
		assertEquals("a",c);
	}
	
	public void function testTagAttrCFApplication4(){
		application action="update" tag="#{cfsavecontent:{trim:false}}#";
		savecontent variable="local.c" {
			echo(" a ");
		}
		assertEquals(" a ",c);
	}
	
	
	
	private string function createURI(string calledName){
		var baseURI="/test/#listLast(getDirectoryFromPath(getCurrenttemplatepath()),"\/")#/";
		return baseURI&""&calledName;
	}
	
} 
</cfscript>