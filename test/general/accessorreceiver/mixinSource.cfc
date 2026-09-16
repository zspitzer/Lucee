component accessors="true" {
	property name="name" type="string" default="from-property";

	// ColdBox / Preside hand a component's whole variables scope to a plain template, which then
	// StructAppends it — see RendererEncapsulator.cfm
	function exportVars() {
		return variables;
	}
}
