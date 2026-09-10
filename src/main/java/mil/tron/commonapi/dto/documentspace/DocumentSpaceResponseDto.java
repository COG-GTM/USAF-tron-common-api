package mil.tron.commonapi.dto.documentspace;


import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

/**
 * DocumentSpace DTO for read operations
 *
 */
@AllArgsConstructor
@Getter
@Setter
@Builder
public class DocumentSpaceResponseDto {
	@NotNull
	private UUID id;
	
    @NotBlank
    @NotNull
    private String name;
}
