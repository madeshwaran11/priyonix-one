package backend;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("workspace_records")
@CompoundIndex(name="workspace_scope",def="{'module':1,'createdBy':1,'updatedAt':-1}")
public class WorkspaceRecord {
    @Id private String id;
    private String module;
    private Map<String,Object> values=new LinkedHashMap<>();
    private String status;
    @Version private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    @Indexed(unique=true) private String businessKey;
    @Indexed(unique=true,sparse=true) private String secondaryKey;
    private long guardRevision;
    public String getId(){return id;} public void setId(String value){id=value;}
    public String getModule(){return module;} public void setModule(String value){module=value;}
    public Map<String,Object> getValues(){return values;} public void setValues(Map<String,Object> value){values=new LinkedHashMap<>(value);}
    public String getStatus(){return status;} public void setStatus(String value){status=value;}
    public Long getVersion(){return version;} public void setVersion(Long value){version=value;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant value){createdAt=value;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant value){updatedAt=value;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String value){createdBy=value;}
    void businessKey(String value){businessKey=value;} void secondaryKey(String value){secondaryKey=value;}
}
