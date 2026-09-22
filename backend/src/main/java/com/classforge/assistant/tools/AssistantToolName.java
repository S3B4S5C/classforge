package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantActionType;

import java.util.Arrays;

public enum AssistantToolName {
    ROUTE_REQUEST("route_uml_request", null),
    CREATE_CLASS("create_class", AssistantActionType.CREATE_CLASS),
    CREATE_ASSOCIATION_CLASS("create_association_class", AssistantActionType.CREATE_ASSOCIATION_CLASS),
    RENAME_CLASS("rename_class", AssistantActionType.RENAME_CLASS),
    DELETE_CLASS("delete_class", AssistantActionType.DELETE_CLASS),
    ADD_ATTRIBUTES("add_attributes", AssistantActionType.ADD_ATTRIBUTES),
    RENAME_ATTRIBUTE("rename_attribute", AssistantActionType.UPDATE_ATTRIBUTE),
    UPDATE_ATTRIBUTE_PROPERTIES("update_attribute_properties", AssistantActionType.UPDATE_ATTRIBUTE),
    DELETE_ATTRIBUTE("delete_attribute", AssistantActionType.DELETE_ATTRIBUTE),
    CREATE_ASSOCIATION("create_association", AssistantActionType.CREATE_RELATIONSHIP),
    CREATE_AGGREGATION("create_aggregation", AssistantActionType.CREATE_RELATIONSHIP),
    CREATE_COMPOSITION("create_composition", AssistantActionType.CREATE_RELATIONSHIP),
    CREATE_GENERALIZATION("create_generalization", AssistantActionType.CREATE_RELATIONSHIP),
    SET_RELATIONSHIP_MULTIPLICITY("set_relationship_multiplicity", AssistantActionType.UPDATE_RELATIONSHIP),
    CHANGE_RELATIONSHIP_TYPE("change_relationship_type", AssistantActionType.UPDATE_RELATIONSHIP),
    DELETE_RELATIONSHIP("delete_relationship", AssistantActionType.DELETE_RELATIONSHIP),
    FINISH_PLAN("finish_plan", null);

    private final String wireName;
    private final AssistantActionType actionType;

    AssistantToolName(String wireName, AssistantActionType actionType) {
        this.wireName = wireName;
        this.actionType = actionType;
    }

    public String wireName() {
        return wireName;
    }

    public AssistantActionType actionType() {
        return actionType;
    }

    public static AssistantToolName fromWireName(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool UML desconocida: " + value));
    }
}
