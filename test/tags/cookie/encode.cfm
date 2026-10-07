<cfscript>
	vals = deserializeJSON( form.cookies );
	// unquoted struct key, the cookie name gets whatever case the key has
	unquoted = { unquotedKey: "lucee" };
</cfscript>

<cfloop array=#vals# item="c">
	<cfcookie name="DEFAULT_#c.name#" value="#c.value#" expires="7"> <!-- i.e. default encodevalue which is true -->
	<cfcookie name="ENCODED_#c.name#" value="#c.value#" expires="7" encodevalue="true">
	<cfcookie name="#c.name#" value="#c.value#" expires="7" encodevalue="false">
	<cfcookie name="preservecase_#c.name#" value="#c.value#" expires="7" encodevalue="false" preservecase="true">
</cfloop>

<cfloop collection=#unquoted# key="k" value="v">
	<cfcookie name="preservecase_#k#" value="#v#" expires="7" preservecase="true">
</cfloop>

<cfcookie name=preservecase_UnquotedAttr value=lucee expires=7 preservecase=true>
