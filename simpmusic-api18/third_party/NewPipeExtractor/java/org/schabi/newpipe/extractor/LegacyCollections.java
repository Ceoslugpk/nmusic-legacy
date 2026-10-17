package org.schabi.newpipe.extractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java 9 collection/string helpers backported for Android API 18. */
public final class LegacyCollections {
    private LegacyCollections() { }

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        ArrayList<T> result = new ArrayList<T>(values.length);
        for (T value : values) {
            if (value == null) throw new NullPointerException("List elements may not be null");
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        LinkedHashSet<T> result = new LinkedHashSet<T>();
        for (T value : values) {
            if (value == null) throw new NullPointerException("Set elements may not be null");
            if (!result.add(value)) throw new IllegalArgumentException("Duplicate set element");
        }
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map mapOf(Object... entries) {
        if ((entries.length & 1) != 0) throw new IllegalArgumentException("Map keys and values must be paired");
        LinkedHashMap result = new LinkedHashMap();
        for (int i = 0; i < entries.length; i += 2) {
            Object key = entries[i];
            Object value = entries[i + 1];
            if (key == null || value == null) throw new NullPointerException("Map keys and values may not be null");
            if (result.containsKey(key)) throw new IllegalArgumentException("Duplicate map key");
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    public static boolean isBlank(CharSequence value) {
        if (value == null || value.length() == 0) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }

    /** java.util.Objects.requireNonNullElse was added after Android API 18. */
    public static <T> T requireNonNullElse(T value, T fallback) {
        if (value != null) return value;
        if (fallback == null) throw new NullPointerException("defaultObj");
        return fallback;
    }
}
