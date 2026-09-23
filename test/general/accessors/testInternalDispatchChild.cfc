component extends="testInternalDispatch" accessors="true" {

	property name="childProp" default="child-default";

	// inherited accessor reached from the subclass body
	public any function parentViaVariables() {
		return variables.getName();
	}
	public any function parentViaBare() {
		return getName();
	}
	public any function ownViaVariables() {
		return variables.getChildProp();
	}
	public any function ownViaBare() {
		return getChildProp();
	}
	public any function setParentViaVariables( required any val ) {
		variables.setName( arguments.val );
		return this;
	}

}
