/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
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
package lucee.runtime.tag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.Cookie;
import javax.servlet.jsp.tagext.Tag;

import lucee.commons.io.DevNullOutputStream;
import lucee.commons.lang.ClassException;
import lucee.commons.lang.ClassUtil;
import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lang.Pair;
import lucee.commons.lang.StringUtil;
import lucee.loader.engine.CFMLEngine;
import lucee.runtime.Component;
import lucee.runtime.ComponentImpl;
import lucee.runtime.ComponentSpecificAccess;
import lucee.runtime.PageContext;
import lucee.runtime.PageContextImpl;
import lucee.runtime.PageSource;
import lucee.runtime.component.ComponentLoader;
import lucee.runtime.config.ConfigWebImpl;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.ApplicationException;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.ext.tag.DynamicAttributes;
import lucee.runtime.functions.BIFProxy;
import lucee.runtime.op.Caster;
import lucee.runtime.reflection.Reflector;
import lucee.runtime.reflection.pairs.MethodInstance;
import lucee.runtime.thread.ThreadUtil;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.util.ArrayUtil;
import lucee.runtime.type.util.KeyConstants;
import lucee.transformer.library.tag.TagLib;
import lucee.transformer.library.tag.TagLibTag;
import lucee.transformer.library.tag.TagLibTagAttr;

import org.osgi.framework.BundleException;

public class TagUtil {
	
	public static final short ORIGINAL_CASE = 0;
	public static final short UPPER_CASE = 1;
	public static final short LOWER_CASE = 2;
	
	//private static final String "invalid call of the function ["+tlt.getName()+", you can not mix named on regular arguments]" = "invalid argument for function, only named arguments are allowed like struct(name:\"value\",name2:\"value2\")";

	public static void setAttributeCollection(PageContext pc,Tag tag, MissingAttribute[] missingAttrs, Struct _attrs, int attrType) throws PageException {
		// check missing tags
		Map<Key, Object> att=new HashMap<Key, Object>();
		{
			Iterator<Entry<Key, Object>> it = _attrs.entryIterator();
			Entry<Key, Object> e;
			while(it.hasNext()){
				e = it.next();
				att.put(e.getKey(), e.getValue());
			}
		}
		
		if(!ArrayUtil.isEmpty(missingAttrs)){
			Key k;
			Object value;
			MissingAttribute miss;
			for(int i=0;i<missingAttrs.length;i++) {
				miss = missingAttrs[i];
				value=att.get(miss.getName());
				// check alias
				if(value==null && !ArrayUtil.isEmpty(miss.getAlias())) {
					String[] alias = miss.getAlias();
					for(int y=0;y<alias.length;y++){
						value=att.get(k=KeyImpl.init(alias[y]));
						if(value!=null) {
							att.remove(k);
							break;
						}
					}
				}
				
				
				if(value==null)
					throw new ApplicationException("attribute "+missingAttrs[i].getName().getString()+" is required but missing");
					//throw new ApplicationException("attribute "+missingAttrs[i].getName().getString()+" is required for tag "+tag.getFullName());
				att.put(
						missingAttrs[i].getName(), 
						Caster.castTo(pc, missingAttrs[i].getType(), value, false));
			}
		}
		setAttributes(pc,tag,att,attrType);
	}
	

	public static void setAttributes(PageContext pc,Tag tag, Map<Key, Object> att, int attrType) throws PageException {
		Iterator<Entry<Key, Object>> it;
		Entry<Key, Object> e;
		//TagLibTag tlt=null;
		if(TagLibTag.ATTRIBUTE_TYPE_DYNAMIC==attrType) {
			DynamicAttributes da=(DynamicAttributes) tag;
			it = att.entrySet().iterator();
			while(it.hasNext()) {
				e = it.next();
				da.setDynamicAttribute(null, e.getKey(),e.getValue());
			}
		}
		else if(TagLibTag.ATTRIBUTE_TYPE_FIXED==attrType) {
			it = att.entrySet().iterator();
			while(it.hasNext()) {
				e = it.next();
				setAttribute(pc,false,true, tag, e.getKey().getLowerString(), e.getValue());
			}	
		}
		else if(TagLibTag.ATTRIBUTE_TYPE_MIXED==attrType) {
			it = att.entrySet().iterator();
			while(it.hasNext()) {
				e = it.next();
				setAttribute(pc, true,true, tag, e.getKey().getLowerString(), e.getValue());
			}
		}
	}
	
	public static void setAttribute(PageContext pc,Tag tag, String name,Object value) throws PageException {
		setAttribute(pc, false, false, tag, name, value);
	}
	
	public static void setAttribute(PageContext pc,boolean doDynamic,boolean silently,Tag tag, String name,Object value) throws PageException {
		MethodInstance setter = Reflector.getSetter(tag, name.toLowerCase(),value,null);
		if(setter!=null) {
			try {
				setter.invoke(tag);
			} 
			catch (Exception _e) {
				if(!(value==null && _e instanceof IllegalArgumentException)) // TODO full null support should allow null, because of that i only suppress in case of an exception id:430 gh:440 ic:gh
				throw Caster.toPageException(_e);
			}
		}
		else if(doDynamic) {
			DynamicAttributes da=(DynamicAttributes) tag;
			da.setDynamicAttribute(null, name,value);
		}
		else if(!silently){
			throw new ApplicationException("failed to call ["+name+"] on tag "+tag);
		}
	}

	/*private static MethodInstance getSetter(PageContext pc,TagLibTag tlt, Tag tag, String name, Object value) throws PageException { 
		MethodInstance setter = Reflector.getSetter(tag, name,value,null);
		if(setter==null && tlt!=null) {
			TagLibTagAttr attr = tlt.getAttribute(name);
			if(attr==null)
				throw new TemplateException(
					"Attribute "+name+" is not allowed for tag "+tlt.getFullName(),
					"valid attribute names are ["+tlt.getAttributeNames()+"]");
			value=Caster.castTo(pc, attr.getType(), value, false);
			setter = Reflector.getSetter(tag, name,value,null);
		}
		return setter;
	}*/

	/*private static TagLibTag getTLT(ConfigWeb config, Tag tag) {
		TagLib[] tlds = ((ConfigWebImpl)config).getTLDs();
		
		TagLibTag tlt;
		for(int i=0;i<tlds.length;i++){
			tlt = tlds[i].getTag(tag.getClass());
			if(tlt!=null) return tlt;
		}
		return null;
	}*/

	public static void setDynamicAttribute(StructImpl attributes,Collection.Key name, Object value, short caseType) {
		if(name.equalsIgnoreCase(KeyConstants._attributecollection)) {
            if(value instanceof lucee.runtime.type.Collection) {
            	lucee.runtime.type.Collection coll=(lucee.runtime.type.Collection)value;
                Iterator<Entry<Key, Object>> it = coll.entryIterator();
            	Entry<Key, Object> e;
                while(it.hasNext()) {
                	e = it.next();
                    if(attributes.get(e.getKey(),null)==null)
                        attributes.setEL(e.getKey(),e.getValue());
                }
                return;
            }
            else if(value instanceof Map) {
            	
            	Map map=(Map) value;
			    Iterator it = map.entrySet().iterator();
			    Map.Entry entry;
			    Key key;
			    while(it.hasNext()) {
			    	entry=(Entry) it.next();
			    	key = Caster.toKey(entry.getKey(),null);
			    	if(!attributes.containsKey(key)){
			    		attributes.setEL(key,entry.getValue());
	            	}
	            }
                return;
            }
        }
		if(LOWER_CASE==caseType)name=KeyImpl.init(name.getLowerString());
		else if(UPPER_CASE==caseType)name=KeyImpl.init(name.getUpperString());
        attributes.setEL(name, value);
	}

	/**
     * load metadata from cfc based custom tags and add the info to the tag
     * @param cs
     * @param config
     */
    public static void addTagMetaData(ConfigWebImpl cw) {
    	if(true) return;
    	
    	PageContextImpl pc=null;
    	try{
    		pc = ThreadUtil.createPageContext(cw, DevNullOutputStream.DEV_NULL_OUTPUT_STREAM, 
				"localhost", "/","", new Cookie[0], new Pair[0], null, new Pair[0], new StructImpl(),false,-1);
    		
    	}
    	catch(Throwable t){
    		ExceptionUtil.rethrowIfNecessary(t);
    		return;
    	}
    	PageContext orgPC = ThreadLocalPageContext.get();
    	try{
    		ThreadLocalPageContext.register(pc);
        
    		
    		// MUST MOST of them are the same, so this is a huge overhead
    		_addTagMetaData(pc,cw,CFMLEngine.DIALECT_CFML);
    		_addTagMetaData(pc,cw,CFMLEngine.DIALECT_LUCEE);
    		
    	}
    	catch(Throwable t){ExceptionUtil.rethrowIfNecessary(t);}
    	finally{
    		pc.getConfig().getFactory().releaseLuceePageContext(pc, true);
    		ThreadLocalPageContext.register(orgPC);
    	}
	}

	private static void _addTagMetaData(PageContext pc,ConfigWebImpl cw, int dialect) {
		TagLibTagAttr attrFileName,attrIsWeb;
    	String filename;
		Boolean isWeb;
		TagLibTag tlt;
		TagLib[] tlds = cw.getTLDs(dialect);
		for(int i=0;i<tlds.length;i++){
			Map<String, TagLibTag> tags = tlds[i].getTags();
			Iterator<TagLibTag> it = tags.values().iterator();
	    	while(it.hasNext()){
	    		tlt = it.next();
	    		if(tlt.getTagClassDefinition().isClassNameEqualTo("lucee.runtime.tag.CFTagCore")) {
	    			attrFileName = tlt.getAttribute("__filename");
	    			attrIsWeb = tlt.getAttribute("__isweb");
	    			if(attrFileName!=null && attrIsWeb!=null) {
	    				filename = Caster.toString(attrFileName.getDefaultValue(),null);
	    				isWeb=Caster.toBoolean(attrIsWeb.getDefaultValue(),null);
	    				if(filename!=null && isWeb!=null) {
	    					addTagMetaData(pc, tlds[i], tlt, filename,isWeb.booleanValue());
	    				}
	    			}
	    		}
	    	}
		}
	}


	private static void addTagMetaData(PageContext pc,TagLib tl, TagLibTag tlt, String filename, boolean isWeb) {
    	if(pc==null) return;
		try{
			ConfigWebImpl config=(ConfigWebImpl) pc.getConfig();
			PageSource ps = isWeb?
					config.getTagMapping().getPageSource(filename):
					config.getServerTagMapping().getPageSource(filename);
			
			//Page p = ps.loadPage(pc);
			ComponentImpl c = ComponentLoader.loadComponent(pc, ps, filename, true,true);
			ComponentSpecificAccess cw = ComponentSpecificAccess.toComponentSpecificAccess(Component.ACCESS_PRIVATE,c);
			Struct meta = Caster.toStruct( cw.get(KeyConstants._metadata,null),null);

			// TODO handle all metadata here and make checking at runtime useless id:331 gh:341 ic:gh
			if(meta!=null) {
				
				// parse body
				boolean rtexprvalue=Caster.toBooleanValue(meta.get(KeyConstants._parsebody,Boolean.FALSE),false);
	    		tlt.setParseBody(rtexprvalue);
	    		
	    		// hint
	    		String hint=Caster.toString(meta.get(KeyConstants._hint,null),null);
	    		if(!StringUtil.isEmpty(hint))tlt.setDescription(hint);
	    		
			}
			
		} 
		catch(Throwable t) {ExceptionUtil.rethrowIfNecessary(t);}
    }

	/**
	 * used by the bytecode builded
	 * @param pc pageContext
	 * @param className
	 * @param bundleName
	 * @param bundleVersion
	 * @return
	 * @throws BundleException 
	 * @throws ClassException 
	 */
	public static Object invokeBIF(PageContext pc, Object[] args, String className, String bundleName, String bundleVersion) throws PageException {
		try {
			Class<?> clazz = ClassUtil.loadClassByBundle(className, bundleName, bundleVersion, pc.getConfig().getIdentification());
			BIF bif;
			if(Reflector.isInstaneOf(clazz, BIF.class)) 
				bif=(BIF)clazz.newInstance();
			else
				bif = new BIFProxy(clazz);
			
			return bif.invoke(pc, args);
			
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}
	

	public static void setAppendix(Tag tag,String appendix) throws PageException { // used by generated bytecode
		// FUTURE if(tag 	instanceof TagPro) ((TagPro)tag).setAppendix(appendix);
		Reflector.callMethod(tag, "setAppendix", new Object[]{appendix});
	}
	
	public static void setMetaData(Tag tag,String name, Object value) throws PageException { // used by generated bytecode
		// FUTURE if(tag 	instanceof TagPro) ((TagPro)tag).setMetaData(name,value);
		Reflector.callMethod(tag, "setMetaData", new Object[]{name,value});
	}
	
	public static void hasBody(Tag tag,boolean hasBody) throws PageException { // used by generated bytecode
		// FUTURE if(tag 	instanceof BodyTagPro) ((BodyTagPro)tag).hasBody(hasBody);
		Reflector.callMethod(tag, "hasBody", new Object[]{hasBody});
	}
	
	
	
}