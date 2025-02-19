package net.cytonic.cytosis.data.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.NoArgsConstructor;
import net.cytonic.cytosis.data.objects.TypedNamespace;
import net.minestom.server.utils.NamespaceID;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


/**
 * A type adapter for {@link TypedNamespace}, allow Gson to serialize and deserialize it easily.
 */
@NoArgsConstructor
public class TypedNamespaceAdapter extends TypeAdapter<TypedNamespace<?>> implements TypeAdapterFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(JsonWriter out, @NotNull TypedNamespace<?> value) throws IOException {

        out.beginObject();

        // Serialize NamespaceID
        out.name("namespaceID");
        out.value(value.namespaceID().asString());

        // Serialize Class<T>
        out.name("type");
        out.value(value.type().getName());

        out.endObject();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypedNamespace<?> read(JsonReader in) throws IOException {
        in.beginObject();

        NamespaceID namespaceID = null;
        Class<?> type = null;

        while (in.hasNext()) {
            String name = in.nextName();
            if ("namespaceID".equals(name)) {
                // Deserialize NamespaceID
                String namespaceIDString = in.nextString();
                namespaceID = NamespaceID.from(namespaceIDString);
            } else if ("type".equals(name)) {
                // Deserialize Class<T>
                String className = in.nextString();
                try {
                    type = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    return null;
//                    throw new IOException(STR."Class not found: \{className}", e);
                }
            }
        }

        in.endObject();

        return new TypedNamespace<>(namespaceID, type);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (TypedNamespace.class.isAssignableFrom(type.getRawType())) {
            return (TypeAdapter<T>) gson.getDelegateAdapter(this, TypeToken.get(TypedNamespace.class));
        }
        return null;
    }
}
