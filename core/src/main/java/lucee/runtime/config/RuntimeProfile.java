package lucee.runtime.config;

import lucee.commons.io.SystemUtil;
import lucee.runtime.op.Caster;

/**
 * Runtime profile flags for JIT dead code elimination.
 *
 * These static final boolean flags are loaded once at class initialization time from
 * environment variables or system properties. The JVM HotSpot compiler uses constant
 * folding to completely eliminate dead code paths when flags are false, resulting in
 * zero runtime overhead.
 *
 * Flags are immutable after class loading - changing requires JVM restart.
 */
public final class RuntimeProfile {

	/**
	 * Production mode flag - when true, disables all dev/profiling features.
	 * Environment Variable: LUCEE_PRODUCTION_MODE
	 * System Property: lucee.production.mode
	 * Default: false
	 */
	public static final boolean PROD;

	/**
	 * FusionDebug flag - when true, allows FusionDebug exception signaling.
	 * Environment Variable: LUCEE_ALLOW_FUSIONDEBUG
	 * System Property: lucee.allow.fusiondebug
	 * Default: false
	 */
	public static final boolean FUSION_DEBUG;

	/**
	 * Debugger flag - when true, allows debugger functionality.
	 * Environment Variable: LUCEE_ALLOW_DEBUGGER
	 * System Property: lucee.allow.debugger
	 * Default: !PROD (true in dev mode, false in production mode)
	 */
	public static final boolean DEBUGGER;

	/**
	 * Execution Log flag - when true, allows execution logging.
	 * Environment Variable: LUCEE_ALLOW_EXECUTION_LOG
	 * System Property: lucee.allow.execution.log
	 * Default: !PROD (true in dev mode, false in production mode)
	 */
	public static final boolean EXECUTION_LOG;

	/**
	 * IP Throttle flag - when true, enables IP-based request throttling.
	 * Automatically enabled when concurrent request limit settings are configured.
	 * Default: true if lucee.request.limit.concurrent.* settings exist, false otherwise
	 */
	public static final boolean IP_THROTTLE;

	/**
	 * Monitoring flag - when true, allows request monitoring.
	 * Environment Variable: LUCEE_ALLOW_MONITORING
	 * System Property: lucee.allow.monitoring
	 * Default: true (allowed unless explicitly disallowed)
	 * Note: Unlike debugger features, monitoring is allowed in production mode.
	 */
	public static final boolean MONITORING;

	static {
		PROD = Caster.toBooleanValue(
			SystemUtil.getSystemPropOrEnvVar( "lucee.production.mode", null ),
			false );

		// When PROD=true, features default to disabled but can be explicitly enabled
		// When PROD=false, features default to enabled but can be explicitly disabled
		FUSION_DEBUG = Caster.toBooleanValue(
			SystemUtil.getSystemPropOrEnvVar( "lucee.allow.fusiondebug", null ),
			!PROD );

		DEBUGGER = Caster.toBooleanValue(
			SystemUtil.getSystemPropOrEnvVar( "lucee.allow.debugger", null ),
			!PROD );

		EXECUTION_LOG = Caster.toBooleanValue(
			SystemUtil.getSystemPropOrEnvVar( "lucee.allow.execution.log", null ),
			!PROD );

		// IP_THROTTLE is enabled if any concurrent request limit settings are configured
		String maxNormPrio = SystemUtil.getSystemPropOrEnvVar( "lucee.request.limit.concurrent.maxnormprio", null );
		String maxNoSleep = SystemUtil.getSystemPropOrEnvVar( "lucee.request.limit.concurrent.maxnosleep", null );
		IP_THROTTLE = maxNormPrio != null || maxNoSleep != null;

		// MONITORING defaults based on production mode but can be explicitly overridden
		MONITORING = Caster.toBooleanValue(
			SystemUtil.getSystemPropOrEnvVar( "lucee.allow.monitoring", null ),
			!PROD );
	}

	private RuntimeProfile() {
		// Prevent instantiation
	}
}
