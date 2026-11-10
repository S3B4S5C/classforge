package com.classforge.notification.push;
import com.classforge.auth.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/push")
public class PushController {
    private final CurrentUser user;private final PushProperties properties;private final PushSubscriptions subscriptions;private final PushOutbox outbox;
    public PushController(CurrentUser user,PushProperties properties,PushSubscriptions subscriptions,PushOutbox outbox){this.user=user;this.properties=properties;this.subscriptions=subscriptions;this.outbox=outbox;}
    public record Keys(@NotBlank @Size(max=100)String p256dh,@NotBlank @Size(max=100)String auth){}
    public record Registration(@NotBlank @Size(max=2048)String endpoint,@NotNull @Valid Keys keys){}
    public record Endpoint(@NotBlank @Size(max=2048)String endpoint){}
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("enabled",properties.enabled(),"publicKey",properties.enabled()?properties.publicKey():"");}
    @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody Registration request){subscriptions.register(user.id(),request.endpoint(),request.keys().p256dh(),request.keys().auth());}
    @PostMapping("/subscriptions/status") public Map<String,Boolean> status(@Valid @RequestBody Endpoint request){return Map.of("registered",subscriptions.owns(user.id(),request.endpoint()));}
    @DeleteMapping("/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Valid @RequestBody Endpoint request){subscriptions.remove(user.id(),request.endpoint());}
    @PostMapping("/subscriptions/test") @ResponseStatus(HttpStatus.ACCEPTED)
    public void test(@Valid @RequestBody Endpoint request){outbox.test(user.id(),request.endpoint());}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Suscripción push no válida o proveedor no compatible."));}
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> invalidBody(){return invalid();}
}
