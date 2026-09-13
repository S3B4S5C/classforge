package ${model.basePackage}.controller;

import ${model.basePackage}.api.CrudSupport;
import ${model.basePackage}.api.PageResponse;
import ${model.basePackage}.dto.${entity.className}Request;
import ${model.basePackage}.dto.${entity.className}Response;
<#if entity.id.kind?string == "COMPOSITE">import ${model.basePackage}.entity.${entity.id.idClassName};
</#if>import ${model.basePackage}.service.${entity.className}Service;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/${entity.tableName}")
public class ${entity.className}Controller {
    private final ${entity.className}Service service;

    public ${entity.className}Controller(${entity.className}Service service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<${entity.className}Response> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", defaultValue = "asc") String direction,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam Map<String, String> params
    ) {
        return service.list(q, CrudSupport.extractFilters(params), sort, direction, page, size);
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", service.count());
    }

<#if entity.id.kind?string == "SIMPLE">    @GetMapping("/{id}")
    public ${entity.className}Response get(@PathVariable("id") ${entity.id.typeSimpleName()} id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public ${entity.className}Response update(@PathVariable("id") ${entity.id.typeSimpleName()} id, @Valid @RequestBody ${entity.className}Request request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") ${entity.id.typeSimpleName()} id) {
        service.delete(id);
    }
<#else>    @GetMapping("/by-id")
    public ${entity.className}Response get(<#list entity.id.fields as field>@RequestParam("${field.fieldName}") ${field.javaType.simpleName()} ${field.fieldName}<#if field_has_next>, </#if></#list>) {
        return service.get(new ${entity.id.idClassName}(<#list entity.id.fields as field>${field.fieldName}<#if field_has_next>, </#if></#list>));
    }

    @PutMapping("/by-id")
    public ${entity.className}Response update(<#list entity.id.fields as field>@RequestParam("${field.fieldName}") ${field.javaType.simpleName()} ${field.fieldName}, </#list>@Valid @RequestBody ${entity.className}Request request) {
        return service.update(new ${entity.id.idClassName}(<#list entity.id.fields as field>${field.fieldName}<#if field_has_next>, </#if></#list>), request);
    }

    @DeleteMapping("/by-id")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(<#list entity.id.fields as field>@RequestParam("${field.fieldName}") ${field.javaType.simpleName()} ${field.fieldName}<#if field_has_next>, </#if></#list>) {
        service.delete(new ${entity.id.idClassName}(<#list entity.id.fields as field>${field.fieldName}<#if field_has_next>, </#if></#list>));
    }
</#if>
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ${entity.className}Response create(@Valid @RequestBody ${entity.className}Request request) {
        return service.create(request);
    }
}
