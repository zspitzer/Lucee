component {
	// datasource session storage with sessionCluster=true: every request loads the session
	// from cf_session_data through JavaConverter's ObjectInputStreamImpl
	this.name = "javaserialization-storage-" & hash( getCurrentTemplatePath() );
	this.sessionManagement = true;
	this.setClientCookies = true;
	this.sessionType = "application";
	this.sessionCluster = true;
	this.sessionTimeout = createTimespan( 0, 0, 5, 0 );
	this.applicationTimeout = createTimespan( 0, 1, 0, 0 );

	variables.mysql = server.getDatasource( "mysql" );
	variables.mysql.storage = true;
	variables.datasourceName = "javaserialization-ds";
	this.datasources[ datasourceName ] = mysql;
	this.dataSource = datasourceName;
	this.sessionStorage = datasourceName;
}
