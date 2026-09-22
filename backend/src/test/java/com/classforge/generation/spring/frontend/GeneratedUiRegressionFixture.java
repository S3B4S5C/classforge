package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.domain.DomainManifestPlan.*;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.mobile.FlutterMobileRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Emits real applications for the browser and Flutter state regression tests. */
public final class GeneratedUiRegressionFixture {
    private static UUID id(int value) { return new UUID(0, value); }
    private static Attribute attribute(int id, String name, SemanticType type, boolean identifier,
            boolean create, boolean update, boolean requiredCreate, boolean requiredUpdate, boolean secret) {
        return new Attribute(id(id), name, name, name, type, !requiredCreate, identifier, identifier,
                !secret, create, update, !secret, !secret, !secret, secret, secret,
                new Validation(requiredCreate, requiredUpdate, false));
    }
    private static Entity entity(int id, String name, Identifier key, List<Attribute> attributes, List<Relation> relations) {
        return new Entity(id(id), name, name, name.toLowerCase(), "/api/" + name.toLowerCase(), name,
                List.of(), key, new Inheritance("NONE", null, null, List.of()), attributes, relations, List.of(), List.of());
    }
    public static DomainManifestPlan manifest(boolean auth) {
        var teamId = new Identifier("SIMPLE", List.of(new IdentifierField(id(11), "id", SemanticType.LONG)));
        var roleId = new Identifier("COMPOSITE", List.of(new IdentifierField(id(21), "tenant", SemanticType.STRING), new IdentifierField(id(22), "code", SemanticType.LONG)));
        var personId = new Identifier("SIMPLE", List.of(new IdentifierField(id(31), "id", SemanticType.LONG)));
        var team = entity(10, "Team", teamId, List.of(
                attribute(11,"id",SemanticType.LONG,true,false,false,false,false,false),
                attribute(13,"createdAt",SemanticType.DATETIME,false,true,true,false,false,false),
                attribute(12,"name",SemanticType.STRING,false,true,true,true,true,false)), List.of());
        var role = entity(20, "Role", roleId, List.of(
                attribute(21,"tenant",SemanticType.STRING,true,true,false,true,false,false),
                attribute(22,"code",SemanticType.LONG,true,true,false,true,false,false),
                attribute(23,"name",SemanticType.STRING,false,true,true,true,true,false)), List.of());
        var attributes = new ArrayList<>(List.of(
                attribute(31,"id",SemanticType.LONG,true,false,false,false,false,false),
                attribute(32,"name",SemanticType.STRING,false,true,true,true,true,false),
                attribute(33,"amount",SemanticType.DECIMAL,false,true,true,false,false,false),
                attribute(34,"active",SemanticType.BOOLEAN,false,true,true,true,true,false),
                attribute(36,"birthDate",SemanticType.DATE,false,true,true,false,false,false),
                attribute(37,"appointmentAt",SemanticType.DATETIME,false,true,true,false,false,false)));
        if (auth) attributes.add(attribute(35,"password",SemanticType.STRING,false,true,true,true,false,true));
        var person = entity(30, "Person", personId, attributes, List.of(
                new Relation(id(40),"ASSOCIATION","MANY_TO_ONE","team",id(10),"Team",true,"teamId",teamId,false),
                new Relation(id(41),"ASSOCIATION","MANY_TO_MANY","roles",id(20),"Role",true,"roleIds",roleId,false)));
        return new DomainManifestPlan("1.0", auth ? "AUTH" : "SIMPLE", new Api("http://localhost:8080","openapi.yaml","postman.json"),
                new Authentication(auth,"Bearer",auth ? id(30) : null,auth ? id(32) : null,auth ? id(35) : null,"jwt",3600,"bootstrap","login"),
                List.of(team,role,person),List.of());
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        for (boolean auth : List.of(false,true)) {
            var manifest = manifest(auth);
            var files = new ArrayList<GeneratedFile>();
            files.addAll(new AngularFrontendRenderer().render(manifest,"ui-regression","#0F766E"));
            files.addAll(new FlutterMobileRenderer().render(manifest,"ui-regression","com.example.regression","#0F766E"));
            for (var file : files) {
                Path target = output.resolve(auth ? "auth" : "simple").resolve(file.path());
                Files.createDirectories(target.getParent());
                Files.write(target, file.content());
            }
        }
    }
}
