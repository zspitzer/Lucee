component {
	
	this.name = "LDEV5053-mysql";
	this.datasources["LDEV5053"] = server.getDatasource("mysql");
	// fallback datasource without the LDEV5053 table
	this.datasource = server.getDatasource( "h2", server._getTempDir( "ldev5053" ) );

}
