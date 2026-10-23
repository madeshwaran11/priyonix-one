package backend;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService service;
    public WorkspaceController(WorkspaceService service){this.service=service;}
    public record WriteRequest(Map<String,Object> values,Long version){}
    public record StatusRequest(String status,Long version){}
    @GetMapping("/schema") public List<WorkspaceSchema> schemas(){return service.schemas();}
    @GetMapping("/lookups/{module}") public List<Map<String,String>> lookups(@PathVariable String module,Authentication actor){return service.lookups(module,actor);}
    @GetMapping("/{module}") public PagedResult<WorkspaceRecord> list(@PathVariable String module,
            @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,Authentication actor){
        return service.list(module,search,status,page,size,actor);
    }
    @GetMapping("/{module}/{id}") public WorkspaceRecord get(@PathVariable String module,@PathVariable String id,Authentication actor){return service.get(module,id,actor);}
    @PostMapping("/{module}") @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceRecord create(@PathVariable String module,@RequestBody WriteRequest body,Authentication actor){return service.create(module,body.values(),actor);}
    @PutMapping("/{module}/{id}") public WorkspaceRecord update(@PathVariable String module,@PathVariable String id,@RequestBody WriteRequest body,Authentication actor){return service.update(module,id,body.values(),body.version(),actor);}
    @PatchMapping("/{module}/{id}/status") public WorkspaceRecord transition(@PathVariable String module,@PathVariable String id,@RequestBody StatusRequest body,Authentication actor){return service.transition(module,id,body.status(),body.version(),actor);}
    @DeleteMapping("/{module}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String module,@PathVariable String id,@RequestParam Long version,Authentication actor){service.delete(module,id,version,actor);}
}
