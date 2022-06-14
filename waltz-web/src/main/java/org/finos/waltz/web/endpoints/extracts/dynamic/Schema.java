package org.finos.waltz.web.endpoints.extracts.dynamic;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.finos.waltz.model.Nullable;

import java.util.Optional;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReportGridSchema.class, name = ReportGridSchema.TYPE)
})
public interface Schema {

    String type();


}
