component {

	this.name = hash( getCurrentTemplatePath() );

	// no tags anywhere in this request before getApplicationSettings(), they would trigger the lazy this.tag init
	this.tag.savecontent.trim = true;

}
