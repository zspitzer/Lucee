component name='application' accessors=true output=false persistent=false {

	this.datasources.test = server.getDatasource( "h2", server._getTempDir( "LDEV6203" ) );
	this.name = 'test6203';

	this.applicationTimeout	= createTimeSpan( 2, 0, 0, 0 );
	this.sessionManagement	= true;
	this.sessionTimeout		= createTimeSpan( 0, 4, 0, 0 );
	this.setClientCookies	= true;
	this.setDomainCookies	= false;

	this.ormenabled = true;
	this.ormsettings.autogenmap			= true;
	this.ormsettings.cfclocation		= ['/orm'];
	this.ormsettings.logsql				= false;
	this.ormsettings.useDBForMapping	= false;
	this.ormsettings.eventHandling		= true;
	this.ormsettings.datasource			= 'test';
	this.ormsettings.dbcreate			= 'dropcreate';
	this.ormsettings.flushatrequestend	= false;
	this.ormsettings.autoManageSession	= false;

	public function onRequestStart() {
		setting requesttimeout=10;
	}
}
