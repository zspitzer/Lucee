
<cfset obj = new objects.objectWithFalseGetter() />
<cfset obj.setName("Test Name") />
<cfset obj.setPassword("testPassword1234") />
<cfset sJson = serializeJSON( obj ) >
<cfoutput>
	#sJson#
</cfoutput>