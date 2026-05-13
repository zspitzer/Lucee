// cfproperty with expression-form default. Expression defaults still classify as declarative —
// per-instance evaluation lives on the scope-seeding side, not in the pseudo-constructor body.
component accessors="true" {

	property name="uid"     type="string"  default="#createUUID()#";
	property name="started" type="date"    default="#now()#";
	property name="ratio"   type="numeric" default="#randRange( 1, 100 )#";
}
