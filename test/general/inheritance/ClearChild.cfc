component extends="Parent" accessors="true" {

	property name="cprop" default="c-default";

	function init( string tag = "clear-child" ) {
		this.tag = arguments.tag;
		return this;
	}

	function chain() {
		return super.chain() & ":clear";
	}

	function clearThis() {
		structClear( this );
	}

	function clearVariables() {
		structClear( variables );
	}

	function hasInVariables( required string key ) {
		return structKeyExists( variables, arguments.key );
	}

	function hasInThis( required string key ) {
		return structKeyExists( this, arguments.key );
	}

}
