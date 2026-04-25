package bench.opensky.router;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts feature values from Java objects using reflection.
 *
 * <p>The tree features use an {@code attr_} prefix (e.g. {@code attr_velocityMps})
 * which maps to bean getters (e.g. {@code getVelocityMps()}).
 * Method lookups are cached per (class, feature) for performance.</p>
 */
public class FeatureExtractor {

    /**
     * Cache: (className + "#" + featureName) → Method.
     * Using ConcurrentHashMap for thread-safety without synchronization overhead.
     */
    private final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap<>();

    /**
     * Extract a numeric feature value from a fact object.
     *
     * @param fact    the Java object (e.g. OpenSkyStateVector)
     * @param feature the feature name from the tree (e.g. "attr_velocityMps")
     * @return the value as a double, or 0.0 if the field is null or not found
     */
    public double extract(Object fact, String feature) {
        String fieldName = feature.startsWith("attr_") ? feature.substring(5) : feature;

        String cacheKey = fact.getClass().getName() + "#" + fieldName;
        Method method = methodCache.get(cacheKey);

        if (method == null) {
            method = resolveGetter(fact.getClass(), fieldName);
            if (method != null) {
                methodCache.put(cacheKey, method);
            }
        }

        if (method == null) {
            return 0.0;
        }

        try {
            Object val = method.invoke(fact);
            if (val == null) return 0.0;
            if (val instanceof Number)  return ((Number) val).doubleValue();
            if (val instanceof Boolean) return ((Boolean) val) ? 1.0 : 0.0;
            // String fields (like icao24, squawk) — try to parse as number
            try {
                return Double.parseDouble(val.toString());
            } catch (NumberFormatException e) {
                return val.hashCode(); // fallback for categorical
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Method resolveGetter(Class<?> clazz, String fieldName) {
        // Try getFieldName
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            return clazz.getMethod("get" + capitalized);
        } catch (NoSuchMethodException ignored) {}

        // Try isFieldName (for boolean)
        try {
            return clazz.getMethod("is" + capitalized);
        } catch (NoSuchMethodException ignored) {}

        // Try exact field name as method
        try {
            return clazz.getMethod(fieldName);
        } catch (NoSuchMethodException ignored) {}

        return null;
    }
}
