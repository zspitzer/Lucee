package lucee.runtime.functions.system;

import lucee.commons.lang.StringUtil;
import lucee.runtime.PageContext;
import lucee.runtime.config.ConfigWebPro;
import lucee.runtime.exp.FunctionException;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.ext.function.Function;
import lucee.runtime.extension.RHExtension;
import lucee.runtime.op.Caster;
import lucee.runtime.osgi.BundleInfo;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Query;
import lucee.runtime.type.QueryImpl;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.util.KeyConstants;

public class ExtensionInfo extends BIF implements Function {

	private static final long serialVersionUID = 2627423175121799118L;

	private static final Key TLDS = KeyConstants._tlds;
	private static final Key FLDS = KeyConstants._flds;
	private static final Key EVENT_GATEWAYS = KeyConstants._eventGateways;
	private static final Key TAGS = KeyConstants._tags;
	private static final Key FUNCTIONS = KeyConstants._functions;
	private static final Key ARCHIVES = KeyConstants._archives;
	private static final Key CONTEXTS = KeyConstants._contexts;
	private static final Key WEBCONTEXTS = KeyConstants._webcontexts;
	private static final Key CONFIG = KeyConstants._config;
	private static final Key APPLICATIONS = KeyConstants._applications;
	private static final Key CATEGORIES = KeyConstants._categories;
	private static final Key PLUGINS = KeyConstants._plugins;
	private static final Key START_BUNDLES = KeyConstants._startBundles;
	private static final Key TRIAL = KeyConstants._trial;
	private static final Key RELEASE_TYPE = KeyConstants._releaseType;
	private static final Key SYMBOLIC_NAME = KeyConstants._symbolicName;

	public static Struct call(PageContext pc, String id) throws PageException {
		if (StringUtil.isEmpty(id, true)) return new StructImpl();
		Struct info = getInfo(id.trim(), ((ConfigWebPro) pc.getConfig()).getRHExtensions());
		return info.size() > 0 ? info : getInfo(id.trim(), ((ConfigWebPro) pc.getConfig()).getServerRHExtensions());
	}

	private static Struct getInfo(String id, RHExtension[] extensions) throws PageException {
		Struct sct = new StructImpl();
		for (RHExtension ext: extensions) {
			if (ext.getId().equalsIgnoreCase(id) || ext.getSymbolicName().equalsIgnoreCase(id)) {
				String ver = ext.getVersion().toString();
				String sName = ext.getSymbolicName();
				sct.set(KeyConstants._id, ext.getId());
				sct.set(SYMBOLIC_NAME, sName);
				sct.set(KeyConstants._name, ext.getName());
				sct.set(KeyConstants._image, ext.getImage());
				sct.set(KeyConstants._description, ext.getDescription());
				sct.set(KeyConstants._version, ver == null ? null : ver);
				sct.set(TRIAL, ext.isTrial());
				sct.set(RELEASE_TYPE, RHExtension.toReleaseType(ext.getReleaseType(), "all"));
				try {
					sct.set(FLDS, Caster.toArray(ext.getFlds()));
					sct.set(TLDS, Caster.toArray(ext.getTlds()));
					sct.set(FUNCTIONS, Caster.toArray(ext.getFunctions()));
					sct.set(ARCHIVES, Caster.toArray(ext.getArchives()));
					sct.set(TAGS, Caster.toArray(ext.getTags()));
					sct.set(CONTEXTS, Caster.toArray(ext.getContexts()));
					sct.set(WEBCONTEXTS, Caster.toArray(ext.getWebContexts()));
					sct.set(CONFIG, Caster.toArray(ext.getConfigs()));
					sct.set(EVENT_GATEWAYS, Caster.toArray(ext.getEventGateways()));
					sct.set(CATEGORIES, Caster.toArray(ext.getCategories()));
					sct.set(APPLICATIONS, Caster.toArray(ext.getApplications()));
					sct.set(KeyConstants._components, Caster.toArray(ext.getComponents()));
					sct.set(PLUGINS, Caster.toArray(ext.getPlugins()));
					sct.set(START_BUNDLES, Caster.toBoolean(ext.getStartBundles()));

					BundleInfo[] bfs = ext.getBundles();
					Query qryBundles = new QueryImpl(new Key[] { KeyConstants._name, KeyConstants._version }, bfs == null ? 0 : bfs.length, "bundles");
					if (bfs != null) {
						for (int i = 0; i < bfs.length; i++) {
							qryBundles.setAt(KeyConstants._name, i + 1, bfs[i].getSymbolicName());
							if (bfs[i].getVersion() != null) qryBundles.setAt(KeyConstants._version, i + 1, bfs[i].getVersionAsString());
						}
					}
					sct.set("Bundles", qryBundles);
				}
				catch (Exception e) {
					throw Caster.toPageException(e);
				}
			}
		}
		return sct;
	}

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		if (args.length == 1) return call(pc, Caster.toString(args[0]));
		else throw new FunctionException(pc, "ExtensionExists", 1, 1, args.length);
	}
}