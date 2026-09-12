package ${model.basePackage}.repository;

import ${model.basePackage}.entity.${repository.entityClassName};
<#if repository.idTypeQualifiedName?has_content>import ${repository.idTypeQualifiedName};<#else>import ${model.basePackage}.entity.${repository.idTypeSimpleName};</#if>
import org.springframework.data.jpa.repository.JpaRepository;

public interface ${repository.interfaceName} extends JpaRepository<${repository.entityClassName}, ${repository.idTypeSimpleName}> { }
