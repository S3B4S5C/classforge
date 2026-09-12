package ${model.basePackage}.entity;

import java.io.Serializable;
import java.util.Objects;
<#list entity.id.fields as field><#if !field.javaType.qualifiedName()?starts_with("java.lang")>import ${field.javaType.qualifiedName()};</#if>
</#list>
public class ${entity.id.idClassName} implements Serializable {
    private static final long serialVersionUID = 1L;
<#list entity.id.fields as field>    private ${field.javaType.simpleName()} ${field.fieldName};
</#list>
    public ${entity.id.idClassName}() { }
    public ${entity.id.idClassName}(<#list entity.id.fields as field>${field.javaType.simpleName()} ${field.fieldName}<#if field_has_next>, </#if></#list>) { <#list entity.id.fields as field>this.${field.fieldName} = ${field.fieldName}; </#list>}
<#list entity.id.fields as field>    public ${field.javaType.simpleName()} get${field.fieldName?cap_first}() { return ${field.fieldName}; }
    public void set${field.fieldName?cap_first}(${field.javaType.simpleName()} ${field.fieldName}) { this.${field.fieldName} = ${field.fieldName}; }
</#list>    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof ${entity.id.idClassName} that)) return false; return <#list entity.id.fields as field>Objects.equals(${field.fieldName}, that.${field.fieldName})<#if field_has_next> && </#if></#list>; }
    @Override public int hashCode() { return Objects.hash(<#list entity.id.fields as field>${field.fieldName}<#if field_has_next>, </#if></#list>); }
}
