package com.heypickler.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Classifies an admin API call into (module, action, targetType, targetId) from the
 * HTTP method + URL path. Falls back to RAW when the path doesn't match known
 * resources, leaving the full path in the log row for forensics.
 */
public final class OperationLogClassifier {

    private static final Map<String, String> MODULE_MAP = new HashMap<>();
    private static final Map<String, String> SINGULAR_MAP = new HashMap<>();
    static {
        MODULE_MAP.put("users", "USER");
        MODULE_MAP.put("events", "EVENT");
        MODULE_MAP.put("banners", "BANNER");
        MODULE_MAP.put("admins", "ADMIN");
        MODULE_MAP.put("rankings", "RANKING");
        MODULE_MAP.put("ban-records", "BAN_RECORD");
        MODULE_MAP.put("dashboard", "DASHBOARD");
        MODULE_MAP.put("auth", "AUTH");
        MODULE_MAP.put("operation-logs", "OPERATION_LOG");
        MODULE_MAP.put("venues", "VENUE");
        MODULE_MAP.put("courts", "COURT");
        MODULE_MAP.put("bookings", "BOOKING");

        SINGULAR_MAP.put("users", "User");
        SINGULAR_MAP.put("events", "Event");
        SINGULAR_MAP.put("banners", "Banner");
        SINGULAR_MAP.put("admins", "Admin");
        SINGULAR_MAP.put("rankings", "Ranking");
        SINGULAR_MAP.put("ban-records", "BanRecord");
        SINGULAR_MAP.put("operation-logs", "OperationLog");
        SINGULAR_MAP.put("venues", "Venue");
        SINGULAR_MAP.put("courts", "Court");
        SINGULAR_MAP.put("bookings", "Booking");
    }

    private OperationLogClassifier() {}

    public static Classification classify(String method, String fullPath) {
        if (fullPath == null) return new Classification("RAW", "RAW", null, null);
        // strip query string
        int q = fullPath.indexOf('?');
        String path = q >= 0 ? fullPath.substring(0, q) : fullPath;
        // expect /api/admin/{resource}[/{id}][/{sub-action}]
        String[] segs = path.split("/");
        // segs[0]="", segs[1]="api", segs[2]="admin", segs[3]=resource
        if (segs.length < 4) return new Classification("RAW", "RAW", null, null);

        String resource = segs[3];
        String module = MODULE_MAP.getOrDefault(resource, "RAW");
        String targetType = SINGULAR_MAP.get(resource);

        if (module.equals("RAW")) {
            return new Classification("RAW", "RAW", null, null);
        }

        String id = (segs.length >= 5 && isIdLike(segs[4])) ? segs[4] : null;
        String subAction = (segs.length >= 5 && id != null && segs.length >= 6) ? segs[5]
                : (segs.length >= 5 && id == null) ? segs[4] : null;

        String action = classifyAction(method, module, subAction);
        return new Classification(module, action, targetType, id);
    }

    private static String classifyAction(String method, String module, String subAction) {
        if ("AUTH".equals(module) && "login".equals(subAction)) return "LOGIN";
        if ("AUTH".equals(module) && "logout".equals(subAction)) return "LOGOUT";

        String m = method.toUpperCase();
        switch (m) {
            case "POST":
                if ("ban".equals(subAction)) return "BAN";
                if ("unban".equals(subAction)) return "UNBAN";
                if ("points".equals(subAction)) return "ENTER_POINTS";
                if ("refresh".equals(subAction)) return "REFRESH";
                return "CREATE";
            case "PUT":
                return "UPDATE";
            case "DELETE":
                return "DELETE";
            case "PATCH":
                return "UPDATE";
            default:
                return "RAW";
        }
    }

    private static boolean isIdLike(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    public static class Classification {
        public final String module;
        public final String action;
        public final String targetType;
        public final String targetId;
        public Classification(String module, String action, String targetType, String targetId) {
            this.module = module;
            this.action = action;
            this.targetType = targetType;
            this.targetId = targetId;
        }
    }
}
