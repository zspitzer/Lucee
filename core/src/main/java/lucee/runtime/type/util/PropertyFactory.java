/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Association Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.runtime.type.util;

import java.util.Map;

import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lang.StringUtil;
import lucee.runtime.Component;
import lucee.runtime.ComponentImpl;
import lucee.runtime.component.Member;
import lucee.runtime.component.Property;
import lucee.runtime.component.PropertyImpl;
import lucee.runtime.exp.ApplicationException;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.PageRuntimeException;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.UDF;
import lucee.runtime.type.UDFAddProperty;
import lucee.runtime.type.UDFGetterProperty;
import lucee.runtime.type.UDFHasProperty;
import lucee.runtime.type.UDFRemoveProperty;
import lucee.runtime.type.UDFSetterProperty;

public final class PropertyFactory {

	public static final Collection.Key SINGULAR_NAME = KeyConstants._singularName;
	public static final Key FIELD_TYPE = KeyConstants._fieldtype;

	public static void createPropertyUDFs(ComponentImpl comp, Property property) throws PageException {
		// getter
		if (property.getGetter()) {
			PropertyFactory.addGet(comp, property);
		}
		// setter
		if (property.getSetter()) {
			PropertyFactory.addSet(comp, property);
		}

		createCollectionPropertyUDFs(comp, property);
	}

	// LDEV-3335: Separated collection methods so they can be called independently
	// when getter/setter are already generated as static flyweights in bytecode
	public static void createCollectionPropertyUDFs(ComponentImpl comp, Property property) throws PageException {
		String fieldType = Caster.toString(property.getDynamicAttributes().get(PropertyFactory.FIELD_TYPE, null), null);

		// add
		if (fieldType != null) {
			if ("one-to-many".equalsIgnoreCase(fieldType) || "many-to-many".equalsIgnoreCase(fieldType)) {
				PropertyFactory.addHas(comp, property);
				PropertyFactory.addAdd(comp, property);
				PropertyFactory.addRemove(comp, property);
			}
			else if ("one-to-one".equalsIgnoreCase(fieldType) || "many-to-one".equalsIgnoreCase(fieldType)) {
				PropertyFactory.addHas(comp, property);
			}
		}
	}

	/**
	 * LDEV-3335: builds the class level accessor flyweight pool, called from the generated
	 * &lt;clinit&gt; of every component with properties. The flyweights carry no owner component, the
	 * receiver is passed to _call at dispatch time.
	 *
	 * @param properties the class level property registry
	 * @param pool the class level accessor map to fill, in property declaration order
	 * @param className name of the generated class, for error context only
	 */
	public static void buildAccessorPool(Map<String, PropertyImpl> properties, Map<Key, UDF> pool, String className) {
		if (properties == null || properties.isEmpty()) return;
		for (PropertyImpl prop: properties.values()) {
			try {
				addAccessorUDFs(pool, prop);
			}
			catch (PageException pe) {
				// a class load failure surfaces as ExceptionInInitializerError, so name the property here
				ApplicationException ae = new ApplicationException("cannot create the accessor functions for the property [" + prop.getName() + "] of [" + className + "]",
						pe.getMessage());
				ExceptionUtil.initCauseEL(ae, pe);
				throw new PageRuntimeException(ae);
			}
		}
	}

	/**
	 * LDEV-3335: the pool side counterpart of {@link #createPropertyUDFs(ComponentImpl, Property)},
	 * same set of accessors in the same order, written into a map instead of registered on an
	 * instance.
	 *
	 * @param pool the class level accessor map to fill
	 * @param prop the property to create the accessors for
	 * @throws PageException
	 */
	public static void addAccessorUDFs(Map<Key, UDF> pool, Property prop) throws PageException {
		PropertyImpl propImpl = (PropertyImpl) prop;
		if (prop.getGetter()) pool.put(propImpl.getGetterKey(), new UDFGetterProperty(null, prop));
		if (prop.getSetter()) pool.put(propImpl.getSetterKey(), new UDFSetterProperty(null, prop));

		String fieldType = Caster.toString(prop.getDynamicAttributes().get(PropertyFactory.FIELD_TYPE, null), null);
		if (fieldType == null) return;

		if ("one-to-many".equalsIgnoreCase(fieldType) || "many-to-many".equalsIgnoreCase(fieldType)) {
			put(pool, new UDFHasProperty(null, prop));
			put(pool, new UDFAddProperty(null, prop));
			put(pool, new UDFRemoveProperty(null, prop));
		}
		else if ("one-to-one".equalsIgnoreCase(fieldType) || "many-to-one".equalsIgnoreCase(fieldType)) {
			put(pool, new UDFHasProperty(null, prop));
		}
	}

	private static void put(Map<Key, UDF> pool, UDF udf) {
		pool.put(KeyImpl.init(udf.getFunctionName()), udf);
	}

	public static void addGet(ComponentImpl comp, Property prop) throws ApplicationException {
		PropertyImpl propImpl = (PropertyImpl) prop;
		Collection.Key getterKey = propImpl.getGetterKey();
		Member m = comp.getMember(Component.ACCESS_PRIVATE, getterKey, true, false);
		if (!(m instanceof UDF)) {
			UDF udf = new UDFGetterProperty(comp, prop);
			comp.registerUDF(getterKey, udf);
		}
	}

	public static void addSet(ComponentImpl comp, Property prop) throws PageException {
		PropertyImpl propImpl = (PropertyImpl) prop;
		Collection.Key setterKey = propImpl.getSetterKey();
		Member m = comp.getMember(Component.ACCESS_PRIVATE, setterKey, true, false);
		if (!(m instanceof UDF)) {
			UDF udf = new UDFSetterProperty(comp, prop);
			comp.registerUDF(setterKey, udf);
		}
	}

	public static void addHas(ComponentImpl comp, Property prop) throws ApplicationException {
		Member m = comp.getMember(Component.ACCESS_PRIVATE, KeyImpl.init("has" + getSingularName(prop)), true, false);
		if (!(m instanceof UDF)) {
			UDF udf = new UDFHasProperty(comp, prop);
			comp.registerUDF(KeyImpl.init(udf.getFunctionName()), udf);
		}
	}

	public static void addAdd(ComponentImpl comp, Property prop) throws ApplicationException {
		Member m = comp.getMember(Component.ACCESS_PRIVATE, KeyImpl.init("add" + getSingularName(prop)), true, false);
		if (!(m instanceof UDF)) {
			UDF udf = new UDFAddProperty(comp, prop);
			comp.registerUDF(KeyImpl.init(udf.getFunctionName()), udf);
		}
	}

	public static void addRemove(ComponentImpl comp, Property prop) throws ApplicationException {
		Member m = comp.getMember(Component.ACCESS_PRIVATE, KeyImpl.init("remove" + getSingularName(prop)), true, false);
		if (!(m instanceof UDF)) {
			UDF udf = new UDFRemoveProperty(comp, prop);
			comp.registerUDF(KeyImpl.init(udf.getFunctionName()), udf);
		}
	}

	public static String getSingularName(Property prop) {
		String singularName = Caster.toString(prop.getDynamicAttributes().get(SINGULAR_NAME, null), null);
		if (!StringUtil.isEmpty(singularName)) return singularName;
		return prop.getName();
	}

	public static String getType(Property prop) {
		String type = prop.getType();
		if (StringUtil.isEmpty(type) || "any".equalsIgnoreCase(type) || "object".equalsIgnoreCase(type)) {
			String fieldType = Caster.toString(prop.getDynamicAttributes().get(FIELD_TYPE, null), null);
			if ("one-to-many".equalsIgnoreCase(fieldType) || "many-to-many".equalsIgnoreCase(fieldType)) {
				return "array";
			}
			return "any";
		}
		return type;
	}

}