package se.maypril.metrics.util;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.maypril.metrics.entity.CoreEntity;

import java.lang.reflect.Field;
import java.util.*;

public class DBObjectConverter {

    private final static Map<Class<?>, List<Field>> FIELD_CACHE = new HashMap<>();

    private final static Logger LOGGER = LoggerFactory.getLogger(DBObjectConverter.class);

    public DBObjectConverter() {
        // Left empty for lulz.
    }

    public static List<Field> getAllDeclaredFields(final Class<?> type) {
        final List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    /**
     * Converts any object to an DB object.
     * <p>
     * Takes the declared fields of the object and creates keys in the mongodb.
     * <p>
     * Does not support multi level object.
     *
     * @param entity the object to convert.
     * @return a converted db object.
     */
    public static DBObject convert(final Object entity) {
        final DBObject dbObject = new BasicDBObject();
        final Class<?> type = entity.getClass();


        if (!FIELD_CACHE.containsKey(type)) {
            // populate cache.
            final List<Field> declaredFields = getAllDeclaredFields(type);
            for (final Field field : declaredFields) {
                field.setAccessible(true);
            }
            FIELD_CACHE.put(type, declaredFields);
        }


        final List<Field> fields = FIELD_CACHE.get(type);
        for (final Field field : fields) {
            try {
                if (CoreEntity.class.isAssignableFrom(field.getType())) {
                    final CoreEntity centity = (CoreEntity) field.get(entity);
                    dbObject.put(field.getName(), centity.toDBObject());
                } else {
                    dbObject.put(field.getName(), field.get(entity));
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOGGER.error("Failed to get value of {}", field.getName(), ex);
            }
        }

        return dbObject;
    }

}
