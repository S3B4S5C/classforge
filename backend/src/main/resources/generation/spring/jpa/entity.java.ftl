package ${model.basePackage}.entity;

<#list imports as import>import ${import};
</#list>
@Entity
@Table(name = "${entity.tableName}"<#if entity.uniqueConstraints?size != 0>, uniqueConstraints = { <#list entity.uniqueConstraints as constraint>@UniqueConstraint(columnNames = {<#list constraint.columnNames as column>"${column}"<#if column_has_next>, </#if></#list>})<#if constraint_has_next>, </#if></#list> }</#if><#if entity.indexes?size != 0>, indexes = { <#list entity.indexes as index>@Index(columnList = "${index.columnNames?join(',')}")<#if index_has_next>, </#if></#list> }</#if>)
<#if entity.inheritance.kind?string == "JOINED_ROOT">@Inheritance(strategy = InheritanceType.JOINED)
</#if><#if entity.id.kind?string == "COMPOSITE" && entity.id.declaredByEntity>@IdClass(${entity.id.idClassName}.class)
</#if><#if entity.inheritance.kind?string == "JOINED_SUBCLASS"><#if entity.inheritance.primaryKeyJoinColumns?size == 1>@PrimaryKeyJoinColumn(name = "${entity.inheritance.primaryKeyJoinColumns[0].localColumnName}", referencedColumnName = "${entity.inheritance.primaryKeyJoinColumns[0].referencedColumnName}")
<#else>@PrimaryKeyJoinColumns({<#list entity.inheritance.primaryKeyJoinColumns as column>
    @PrimaryKeyJoinColumn(name = "${column.localColumnName}", referencedColumnName = "${column.referencedColumnName}")<#if column_has_next>,</#if></#list>
})
</#if></#if>public class ${entity.className}<#if entity.inheritance.kind?string == "JOINED_SUBCLASS"> extends ${entity.inheritance.superClassName}</#if> {
    <#if api.enabled()>public<#else>protected</#if> ${entity.className}() { }

<#list entity.scalarFields as field><#if field.identifier && entity.id.declaredByEntity>    @Id
</#if>    @Column(name = "${field.columnName}", nullable = <#if api.enabled() && (apiEntity.isUsernameAttribute(field.sourceAttributeId) || apiEntity.isPasswordAttribute(field.sourceAttributeId))>false<#else>${field.nullable?c}</#if><#if api.enabled() && apiEntity.isUsernameAttribute(field.sourceAttributeId)>, unique = true</#if>)
    private ${field.javaType.simpleName()} ${field.fieldName};

</#list><#list entity.directRelations as relation><#if relation.onDeleteCascade>    @OnDelete(action = OnDeleteAction.CASCADE)
</#if>    @<#if relation.kind?string == "MANY_TO_ONE">ManyToOne<#else>OneToOne</#if>(fetch = FetchType.LAZY, optional = ${relation.optional?c})
<#if relation.joinColumns?size == 1>    @JoinColumn(name = "${relation.joinColumns[0].localColumnName}", referencedColumnName = "${relation.joinColumns[0].referencedColumnName}", nullable = ${relation.joinColumns[0].nullable?c})
<#else>    @JoinColumns({<#list relation.joinColumns as column>
        @JoinColumn(name = "${column.localColumnName}", referencedColumnName = "${column.referencedColumnName}", nullable = ${column.nullable?c})<#if column_has_next>,</#if></#list>
    })
</#if>    private ${relation.targetEntityClassName} ${relation.fieldName};

</#list><#list entity.manyToManyRelations as relation>    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "${relation.joinTableName}", joinColumns = {<#list relation.joinColumns as column> @JoinColumn(name = "${column.localColumnName}", referencedColumnName = "${column.referencedColumnName}", nullable = ${column.nullable?c})<#if column_has_next>,</#if></#list> }, inverseJoinColumns = {<#list relation.inverseJoinColumns as column> @JoinColumn(name = "${column.localColumnName}", referencedColumnName = "${column.referencedColumnName}", nullable = ${column.nullable?c})<#if column_has_next>,</#if></#list> })
    private Set<${relation.targetEntityClassName}> ${relation.fieldName} = new LinkedHashSet<>();

</#list><#list entity.scalarFields as field>    public ${field.javaType.simpleName()} get${field.fieldName?cap_first}() { return ${field.fieldName}; }
    public void set${field.fieldName?cap_first}(${field.javaType.simpleName()} ${field.fieldName}) { this.${field.fieldName} = ${field.fieldName}; }

</#list><#list entity.directRelations as relation>    public ${relation.targetEntityClassName} get${relation.fieldName?cap_first}() { return ${relation.fieldName}; }
    public void set${relation.fieldName?cap_first}(${relation.targetEntityClassName} ${relation.fieldName}) { this.${relation.fieldName} = ${relation.fieldName}; }

</#list><#list entity.manyToManyRelations as relation>    public Set<${relation.targetEntityClassName}> get${relation.fieldName?cap_first}() { return ${relation.fieldName}; }
    public void set${relation.fieldName?cap_first}(Set<${relation.targetEntityClassName}> ${relation.fieldName}) { this.${relation.fieldName} = ${relation.fieldName}; }

</#list>}
