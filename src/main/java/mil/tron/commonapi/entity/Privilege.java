package mil.tron.commonapi.entity;


import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Privilege {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	@Setter
    private Long id;
	
	@Getter
	@Setter
	private String name;
}
