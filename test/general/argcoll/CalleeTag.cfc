<cfcomponent>
	<!--- tag syntax on purpose: the script transformer hardcodes passByRef=true and only stores
		inline arg attributes as metadata, so passby="value" is ONLY honored via cfargument --->
	<cffunction name="mutArrayByValue">
		<cfargument name="arr" type="array" passby="value">
		<cfset arrayAppend( arguments.arr, "MUT" )>
	</cffunction>
</cfcomponent>
