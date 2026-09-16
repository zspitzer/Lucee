component accessors="true" {
	property name="name" type="string";
	property name="age" type="numeric" default="25";
	property name="tags" type="array" singularName="tag" fieldType="one-to-many";
	property name="passport" fieldType="one-to-one";
}
