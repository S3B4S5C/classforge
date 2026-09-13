<#macro relationsAssign><#list entity.directRelations as relation>        if (request.${relation.fieldName}Id() == null) {
<#if relation.optional>            entity.set${relation.fieldName?cap_first}(null);
<#else>            throw new IllegalArgumentException("Relation ${relation.fieldName} is required.");
</#if>        } else {
            entity.set${relation.fieldName?cap_first}(${relation.fieldName}TargetRepository.findById(request.${relation.fieldName}Id())
                    .orElseThrow(() -> new ApiNotFoundException("${relation.targetEntityClassName} relation target not found.")));
        }
</#list><#list entity.manyToManyRelations as relation>        entity.get${relation.fieldName?cap_first}().clear();
        if (request.${relation.fieldName}Ids() != null) {
            for (${relation.targetId.typeSimpleName()} targetId : request.${relation.fieldName}Ids()) {
                entity.get${relation.fieldName?cap_first}().add(${relation.fieldName}TargetRepository.findById(targetId)
                        .orElseThrow(() -> new ApiNotFoundException("${relation.targetEntityClassName} relation target not found.")));
            }
        }
</#list></#macro>
package ${model.basePackage}.service;

import ${model.basePackage}.api.ApiNotFoundException;
import ${model.basePackage}.api.CrudSupport;
import ${model.basePackage}.api.PageResponse;
import ${model.basePackage}.dto.${entity.className}Request;
import ${model.basePackage}.dto.${entity.className}Response;
import ${model.basePackage}.entity.${entity.className};
<#if entity.id.kind?string == "COMPOSITE">import ${model.basePackage}.entity.${entity.id.idClassName};
</#if><#list entity.directRelations as relation>import ${model.basePackage}.entity.${relation.targetEntityClassName};
<#if relation.targetId.kind?string == "COMPOSITE">import ${model.basePackage}.entity.${relation.targetId.idClassName};
</#if></#list><#list entity.manyToManyRelations as relation>import ${model.basePackage}.entity.${relation.targetEntityClassName};
<#if relation.targetId.kind?string == "COMPOSITE">import ${model.basePackage}.entity.${relation.targetId.idClassName};
</#if></#list>import ${model.basePackage}.repository.${entity.repositoryName};
<#list entity.directRelations as relation>import ${model.basePackage}.repository.${relation.targetRepositoryName};
</#list><#list entity.manyToManyRelations as relation>import ${model.basePackage}.repository.${relation.targetRepositoryName};
</#list>import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
<#if entity.authEntity>import org.springframework.security.crypto.password.PasswordEncoder;
</#if>import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ${entity.className}Service {
    private final ${entity.repositoryName} repository;
<#list entity.directRelations as relation>    private final ${relation.targetRepositoryName} ${relation.fieldName}TargetRepository;
</#list><#list entity.manyToManyRelations as relation>    private final ${relation.targetRepositoryName} ${relation.fieldName}TargetRepository;
</#list><#if entity.authEntity>    private final PasswordEncoder passwordEncoder;
</#if>
    public ${entity.className}Service(
            ${entity.repositoryName} repository<#list entity.directRelations as relation>,
            ${relation.targetRepositoryName} ${relation.fieldName}TargetRepository</#list><#list entity.manyToManyRelations as relation>,
            ${relation.targetRepositoryName} ${relation.fieldName}TargetRepository</#list><#if entity.authEntity>,
            PasswordEncoder passwordEncoder</#if>
    ) {
        this.repository = repository;
<#list entity.directRelations as relation>        this.${relation.fieldName}TargetRepository = ${relation.fieldName}TargetRepository;
</#list><#list entity.manyToManyRelations as relation>        this.${relation.fieldName}TargetRepository = ${relation.fieldName}TargetRepository;
</#list><#if entity.authEntity>        this.passwordEncoder = passwordEncoder;
</#if>    }

    @Transactional(readOnly = true)
    public PageResponse<${entity.className}Response> list(
            String q,
            Map<String, String> filters,
            String sort,
            String direction,
            int page,
            int size
    ) {
        List<${entity.className}Response> content = repository.findAll().stream().map(this::toResponse).toList();
        return CrudSupport.query(content, q, filters, sort, direction, page, size);
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public ${entity.className}Response get(${entity.id.typeSimpleName()} id) {
        return toResponse(require(id));
    }

    public ${entity.className}Response create(${entity.className}Request request) {
        Objects.requireNonNull(request, "request is required");
        ${entity.className} entity = new ${entity.className}();
        applyCreate(entity, request);
        return toResponse(repository.save(entity));
    }

    public ${entity.className}Response update(${entity.id.typeSimpleName()} id, ${entity.className}Request request) {
        Objects.requireNonNull(request, "request is required");
        ${entity.className} entity = require(id);
        applyUpdate(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(${entity.id.typeSimpleName()} id) {
        ${entity.className} entity = require(id);
        repository.delete(entity);
    }

<#if entity.authEntity>    @Transactional(readOnly = true)
    public void authenticate(String username, String rawPassword) {
        ${entity.className} entity = repository.findAll().stream()
                .filter(candidate -> candidate.get${entity.usernameField().fieldName?cap_first}() != null)
                .filter(candidate -> candidate.get${entity.usernameField().fieldName?cap_first}().equalsIgnoreCase(username))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));
        if (!passwordEncoder.matches(rawPassword, entity.get${entity.passwordField().fieldName?cap_first}())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
    }

    public ${entity.className}Response bootstrap(${entity.className}Request request) {
        if (repository.count() != 0) {
            throw new IllegalArgumentException("Authentication bootstrap is only available while the authentication table is empty.");
        }
        return create(request);
    }

    private void requireUniqueUsername(String username, ${entity.id.typeSimpleName()} currentId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        boolean duplicate = repository.findAll().stream()
                .filter(candidate -> candidate.get${entity.usernameField().fieldName?cap_first}() != null)
                .filter(candidate -> candidate.get${entity.usernameField().fieldName?cap_first}().equalsIgnoreCase(username))
                .anyMatch(candidate -> currentId == null || !Objects.equals(idOf(candidate), currentId));
        if (duplicate) {
            throw new IllegalArgumentException("Username must be unique.");
        }
    }

</#if>    private ${entity.className} require(${entity.id.typeSimpleName()} id) {
        return repository.findById(id).orElseThrow(() -> new ApiNotFoundException("${entity.className} not found."));
    }

    private void applyCreate(${entity.className} entity, ${entity.className}Request request) {
<#list entity.scalarFields as field><#if entity.isPasswordAttribute(field.sourceAttributeId)>        if (request.${field.fieldName}() == null || request.${field.fieldName}().isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        entity.set${field.fieldName?cap_first}(passwordEncoder.encode(request.${field.fieldName}()));
<#else><#if entity.isUsernameAttribute(field.sourceAttributeId)>        requireUniqueUsername(request.${field.fieldName}(), null);
</#if>        entity.set${field.fieldName?cap_first}(request.${field.fieldName}());
</#if></#list><@relationsAssign/>
    }

    private void applyUpdate(${entity.className} entity, ${entity.className}Request request) {
<#list entity.scalarFields as field><#if !field.identifier><#if entity.isPasswordAttribute(field.sourceAttributeId)>        if (request.${field.fieldName}() != null && !request.${field.fieldName}().isBlank()) {
            entity.set${field.fieldName?cap_first}(passwordEncoder.encode(request.${field.fieldName}()));
        }
<#else><#if entity.isUsernameAttribute(field.sourceAttributeId)>        requireUniqueUsername(request.${field.fieldName}(), idOf(entity));
</#if>        entity.set${field.fieldName?cap_first}(request.${field.fieldName}());
</#if></#if></#list><@relationsAssign/>
    }

    private ${entity.id.typeSimpleName()} idOf(${entity.className} entity) {
<#if entity.id.kind?string == "SIMPLE">        return entity.get${entity.id.fields[0].fieldName?cap_first}();
<#else>        return new ${entity.id.idClassName}(<#list entity.id.fields as field>entity.get${field.fieldName?cap_first}()<#if field_has_next>, </#if></#list>);
</#if>    }

    private ${entity.className}Response toResponse(${entity.className} entity) {
        return new ${entity.className}Response(
<#list entity.responseFields() as field>                entity.get${field.fieldName?cap_first}()<#if field_has_next || entity.directRelations?size != 0 || entity.manyToManyRelations?size != 0>,</#if>
</#list><#list entity.directRelations as relation>                entity.get${relation.fieldName?cap_first}() == null ? null : <#if relation.targetId.kind?string == "SIMPLE">entity.get${relation.fieldName?cap_first}().get${relation.targetId.fields[0].fieldName?cap_first}()<#else>new ${relation.targetId.idClassName}(<#list relation.targetId.fields as idField>entity.get${relation.fieldName?cap_first}().get${idField.fieldName?cap_first}()<#if idField_has_next>, </#if></#list>)</#if><#if relation_has_next || entity.manyToManyRelations?size != 0>,</#if>
</#list><#list entity.manyToManyRelations as relation>                entity.get${relation.fieldName?cap_first}().stream().map(target -> <#if relation.targetId.kind?string == "SIMPLE">target.get${relation.targetId.fields[0].fieldName?cap_first}()<#else>new ${relation.targetId.idClassName}(<#list relation.targetId.fields as idField>target.get${idField.fieldName?cap_first}()<#if idField_has_next>, </#if></#list>)</#if>).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))<#if relation_has_next>,</#if>
</#list>        );
    }

}
