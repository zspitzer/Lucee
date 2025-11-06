component {

	public function init( struct lang, struct app ) {
		return this;
	}

	public string function overview( struct lang, struct app, struct req ) {
		return "<h2>LDEV-5895 Test Plugin</h2><p>This is a test plugin for LDEV-5895.</p>";
	}

}
