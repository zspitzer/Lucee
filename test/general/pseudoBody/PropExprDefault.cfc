// cfproperty with expression-form default. Each instance needs a fresh evaluation of the
// expression (#createUUID()#, #now()#, #randRange()#) — that work currently lives inline in the
// pseudo-constructor body emission, so the classifier flags it as imperative.
component accessors="true" {

	property name="uid"     type="string"  default="#createUUID()#";
	property name="started" type="date"    default="#now()#";
	property name="ratio"   type="numeric" default="#randRange( 1, 100 )#";
}
