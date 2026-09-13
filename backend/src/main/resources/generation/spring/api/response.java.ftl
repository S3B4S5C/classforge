package ${model.basePackage}.dto;

<#list entity.directRelations as relation><#if relation.targetId.kind?string == "COMPOSITE">import ${model.basePackage}.entity.${relation.targetId.idClassName};
</#if></#list><#list entity.manyToManyRelations as relation><#if relation.targetId.kind?string == "COMPOSITE">import ${model.basePackage}.entity.${relation.targetId.idClassName};
</#if></#list>import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record ${entity.className}Response(
<#list entity.responseFields() as field>        ${field.javaType.simpleName()} ${field.fieldName}<#if field_has_next || entity.directRelations?size != 0 || entity.manyToManyRelations?size != 0>,</#if>
</#list><#list entity.directRelations as relation>        ${relation.targetId.typeSimpleName()} ${relation.fieldName}Id<#if relation_has_next || entity.manyToManyRelations?size != 0>,</#if>
</#list><#list entity.manyToManyRelations as relation>        Set<${relation.targetId.typeSimpleName()}> ${relation.fieldName}Ids<#if relation_has_next>,</#if>
</#list>) { }
