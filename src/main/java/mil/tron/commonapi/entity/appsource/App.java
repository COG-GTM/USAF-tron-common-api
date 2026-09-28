package mil.tron.commonapi.entity.appsource;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode
@MappedSuperclass
public abstract class App {
	public static final String NAME_FIELD = "name";
	public static final String ID_FIELD = "id";
	
    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @Column(unique = true)
    private String name;
    	
	@Getter
    @Column(unique = true)
	private String nameAsLower;
    
	@PrePersist 
	@PreUpdate 
	public void sanitize() {
		trimStringFields();
		sanitizeNameForUniqueConstraint();
	}
	
	private void sanitizeNameForUniqueConstraint() {
        nameAsLower = name == null ? null : name.toLowerCase();
    }
	
	private void trimStringFields() {
		name = name == null ? null : name.trim();
	}
}
