component accessors="true" {

	property name="name" default="internal-default";
	property name="age" type="numeric" default="1";

	// every accessor reach from inside the component: the shadow hit today, the class-level pool tomorrow

	public any function viaVariables() {
		return variables.getName();
	}
	public any function viaBare() {
		return getName();
	}
	public any function viaThis() {
		return this.getName();
	}
	public any function viaVariablesRef() {
		var f = variables.getName;
		return f();
	}
	public any function viaBracket() {
		return variables[ "getName" ]();
	}

	public any function setViaVariables( required any val ) {
		variables.setName( arguments.val );
		return this;
	}
	public any function setViaBare( required any val ) {
		setName( arguments.val );
		return this;
	}
	public any function setViaThis( required any val ) {
		this.setName( arguments.val );
		return this;
	}
	public any function setAgeViaVariables( required any val ) {
		variables.setAge( arguments.val );
		return this;
	}

	public boolean function existsInVariables( required string key ) {
		return structKeyExists( variables, arguments.key );
	}
	public boolean function existsInThis( required string key ) {
		return structKeyExists( this, arguments.key );
	}
	public boolean function isFunctionInVariables( required string key ) {
		return isCustomFunction( variables[ arguments.key ] );
	}
	public array function variablesKeys() {
		return structKeyArray( variables );
	}
	public any function deleteFromVariables( required string key ) {
		structDelete( variables, arguments.key );
		return this;
	}
	public any function deleteFromThis( required string key ) {
		structDelete( this, arguments.key );
		return this;
	}

}
