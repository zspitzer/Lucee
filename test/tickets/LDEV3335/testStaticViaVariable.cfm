<cfscript>
	// sigh, need to call this so static.args is initialized
	cfc = new StaticComponent();
	loop times=10 {
		StaticComponent::toSQL();
	}	
	echo( SerializeJson(StaticComponent::toSQL()) );
</cfscript>
