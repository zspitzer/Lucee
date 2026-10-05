package lucee.runtime.util;

import java.lang.ref.Reference;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import lucee.commons.collection.RefMap;
import lucee.commons.collection.RefMap.ReferenceType;
import lucee.commons.digest.HashUtil;
import lucee.commons.io.SystemUtil;
import lucee.commons.lang.StringUtil;
import lucee.runtime.PageContext;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.op.Caster;
import lucee.runtime.op.Decision;

public final class CFIDUtil {
	public static Boolean IDENTIFY_CLIENT_DEFAULT = Boolean.FALSE;
	private static Boolean identifyClient;

	private static Map<String, String> clients = new RefMap<>(ReferenceType.SOFT, new ConcurrentHashMap<String, Reference<String>>(100));

	private static final SecureRandom CFID_RANDOM = createDrbg();

	public static boolean isCFID(PageContext pc, Object obj) {
		return Decision.isGUIdSimple(obj);
	}

	public static boolean isCFToken(PageContext pc, Object obj) {
		if (obj == null) return false;
		String str = obj.toString().trim();
		return str.equals("0") || str.length() == 12;
	}

	public static String createCFID(PageContext pc) {
		// same as UUID.randomUUID(), but on our own DRBG, UUID's shared SecureRandom is a JVM wide lock (on Linux, NativePRNG is global)
		byte[] b = new byte[16];
		CFID_RANDOM.nextBytes(b);
		b[6] &= 0x0f;
		b[6] |= 0x40; // version 4
		b[8] &= 0x3f;
		b[8] |= (byte) 0x80; // IETF variant
		long msb = 0, lsb = 0;
		for (int i = 0; i < 8; i++) msb = (msb << 8) | (b[i] & 0xff);
		for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (b[i] & 0xff);
		return new UUID(msb, lsb).toString();
	}

	private static SecureRandom createDrbg() {
		try {
			return SecureRandom.getInstance("DRBG");
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static String createCFToken(PageContext pc) {

		if (identifyClient == null) identifyClient = Caster.toBoolean(SystemUtil.getSystemPropOrEnvVar("lucee.identify.client", null), IDENTIFY_CLIENT_DEFAULT);
		if (identifyClient.equals(Boolean.FALSE)) return "0";

		return createClientBasedCFToken(pc);
	}

	private static String createClientBasedCFToken(PageContext pc) {
		if (pc == null) pc = ThreadLocalPageContext.get();
		String str = null;
		if (pc != null) {
			HttpServletRequest req = pc.getHttpServletRequest();
			if (req != null) {
				str = req.getHeader("User-Agent");
				if (str == null) str = req.getHeader("user-agent");
				if (str == null) str = req.getHeader("accept");
			}
		}

		if (StringUtil.isEmpty(str, true)) return "0";
		String val = clients.get(str);
		if (val != null) return val;

		val = HashUtil.create64BitHashAsString(str, Character.MAX_RADIX);
		if (val.length() > 12) {
			val = val.substring(0, 12);
		}
		while (val.length() < 12) {
			val = val + "0";
		}
		clients.put(str, val);
		return val;
	}

}
